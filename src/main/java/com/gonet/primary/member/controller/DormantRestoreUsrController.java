package com.gonet.primary.member.controller;

import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.identity.config.NiceCheckProperties;
import com.gonet.primary.identity.controller.NiceCheckUsrController;
import com.gonet.primary.identity.dto.NiceCheckResult;
import com.gonet.primary.member.service.DormantRestoreService;
import com.gonet.primary.site.dto.SiteContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 휴면 복원 — {@code /{siteCode}/member/dormant}.
 *
 * <p>휴면 계정은 {@code vw_user_login} 에 없어 평범한 로그인은 그냥 실패한다.
 * <b>아이디와 비밀번호가 모두 맞을 때만</b> 이 화면으로 온다 — 아이디만으로
 * "휴면입니다" 를 알려 주면 계정 존재 확인 도구가 된다.
 *
 * <p>대상 회원은 세션에 담는다. 화면 폼에 member_id 를 실으면 남의 계정으로 코드를
 * 발송시킬 수 있다.
 */
@Controller
@RequestMapping("/{siteCode}/member/dormant")
@RequiredArgsConstructor
public class DormantRestoreUsrController {

    /** 비밀번호까지 확인된 휴면 대상 — 폼이 아니라 세션이 들고 있는다. */
    public static final String SESSION_TARGET = "GOPCMS_DORMANT_TARGET";

    private final DormantRestoreService restoreService;
    private final NiceCheckProperties niceProps;

    /**
     * 1단계 — 아이디·비밀번호 확인 후 휴면이면 코드 입력 화면으로.
     *
     * <p>실명인증 팝업이 끝나면 부모창이 이 주소로 돌아온다. 그때 세션에 놓인 NICE
     * 결과를 소비해 곧바로 복원한다 — 인증이 끝난 시점에 본인 확인도 끝났으므로
     * 아이디·비밀번호를 다시 물을 이유가 없다.
     */
    @GetMapping
    public String form(HttpServletRequest request, Model model,
            RedirectAttributes redirect) {
        String restoredLoginId = consumeNiceResult(request);
        if (restoredLoginId != null) {
            redirect.addFlashAttribute("flashOk",
                    "계정이 복원되었습니다. 아이디 %s 로 로그인해 주세요.".formatted(restoredLoginId));
            return "redirect:/login?siteCode=" + siteCode();
        }
        model.addAttribute("hasTarget", target(request) != null);
        addNiceModel(model);
        return "front/member/dormant";
    }

    /**
     * 실명인증 결과를 <b>한 번만</b> 소비해 복원을 시도한다.
     *
     * <p>결과를 지우지 않으면 이 화면을 새로고침할 때마다 복원이 다시 시도된다.
     *
     * @return 복원된 로그인 ID, 복원하지 못했으면 null
     */
    private String consumeNiceResult(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object raw = session.getAttribute(NiceCheckUsrController.SESSION_RESULT);
        if (!(raw instanceof NiceCheckResult result) || !result.isSuccess()) {
            return null;
        }
        session.removeAttribute(NiceCheckUsrController.SESSION_RESULT);

        SiteContext site = SiteContextHolder.get();
        return site == null ? null
                : restoreService.restoreByIdentity(site.getSiteId(), result.getDi());
    }

    /** 실명인증 버튼 노출 여부와 팝업 주소 — 계약 자격이 없으면 버튼을 내린다. */
    private void addNiceModel(Model model) {
        boolean ready = niceProps.isEnabled()
                && notBlank(niceProps.getSiteCode())
                && notBlank(niceProps.getSitePassword());
        model.addAttribute("niceReady", ready);
        if (ready) {
            model.addAttribute("niceUrl", "/member/identity/nice"
                    + "?purpose=" + NiceCheckUsrController.PURPOSE_SELF
                    + "&siteCode=" + siteCode()
                    + "&next=" + base() + "/dormant");
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    @PostMapping("/check")
    public String check(@RequestParam String loginId, @RequestParam String password,
            HttpServletRequest request, Model model) {
        SiteContext site = SiteContextHolder.get();
        String memberId = site == null
                ? null : restoreService.findDormantMemberId(site.getSiteId(), loginId, password);

        if (memberId == null) {
            // 휴면이 아닌지, 비밀번호가 틀렸는지 구분하지 않는다
            model.addAttribute("flashError",
                    "휴면 계정을 확인할 수 없습니다. 아이디와 비밀번호를 다시 확인해 주세요.");
            addNiceModel(model);
            return "front/member/dormant";
        }
        request.getSession(true).setAttribute(SESSION_TARGET, memberId);
        try {
            restoreService.issueCode(memberId);
            model.addAttribute("flashOk", "등록된 이메일로 인증번호를 보냈습니다.");
        } catch (IllegalStateException e) {
            model.addAttribute("flashError", e.getMessage());
        }
        model.addAttribute("hasTarget", true);
        addNiceModel(model);
        return "front/member/dormant";
    }

    /** 재발송 — 쿨다운은 서비스가 판단한다. */
    @PostMapping("/resend")
    public String resend(HttpServletRequest request, Model model) {
        String memberId = target(request);
        if (memberId == null) {
            return "redirect:" + base() + "/dormant";
        }
        try {
            restoreService.issueCode(memberId);
            model.addAttribute("flashOk", "인증번호를 다시 보냈습니다.");
        } catch (IllegalStateException e) {
            model.addAttribute("flashError", e.getMessage());
        }
        model.addAttribute("hasTarget", true);
        addNiceModel(model);
        return "front/member/dormant";
    }

    /** 2단계 — 코드 검증 + 복원. */
    @PostMapping("/verify")
    public String verify(@RequestParam String code, HttpServletRequest request,
            Model model, RedirectAttributes redirect) {
        String memberId = target(request);
        if (memberId == null) {
            return "redirect:" + base() + "/dormant";
        }
        if (!restoreService.verifyAndRestore(memberId, code)) {
            // 만료인지 오답인지 알려 주지 않는다 — 대입에 도움이 된다
            model.addAttribute("flashError", "인증번호가 올바르지 않거나 유효시간이 지났습니다.");
            model.addAttribute("hasTarget", true);
            addNiceModel(model);
            return "front/member/dormant";
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_TARGET);
        }
        redirect.addFlashAttribute("flashOk", "계정이 복원되었습니다. 다시 로그인해 주세요.");
        return "redirect:/login?siteCode=" + siteCode();
    }

    private String target(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(SESSION_TARGET);
    }

    private String base() {
        return "/" + siteCode() + "/member";
    }

    private String siteCode() {
        SiteContext site = SiteContextHolder.get();
        return site == null ? "" : site.getSiteCode();
    }
}
