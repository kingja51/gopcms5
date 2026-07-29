package com.gonet.primary.member.controller;

import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.identity.config.NiceCheckProperties;
import com.gonet.primary.identity.controller.NiceCheckUsrController;
import com.gonet.primary.identity.dto.NiceCheckResult;
import com.gonet.primary.member.dto.JoinSession;
import com.gonet.primary.member.dto.MemberJoinForm;
import com.gonet.primary.member.service.MemberJoinService;
import com.gonet.primary.site.dto.SiteContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 회원 가입 마법사 — {@code /{siteCode}/member/join}.
 *
 * <pre>
 *   STEP 1  /join            유형 선택 (일반 / 소아·청소년)
 *   STEP 2  /join/terms      약관 동의
 *   STEP 3  /join/verify     본인인증 (NICE) — 끄면 건너뛴다
 *   STEP 4  /join/form       정보 입력
 *   STEP 5  /join/complete   가입 완료
 * </pre>
 *
 * <p><b>단계 상태는 전부 세션</b>({@link JoinSession})이다. 동의·인증 결과를 hidden 으로
 * 넘기면 사용자가 값을 바꿔 인증을 건너뛸 수 있다. 각 단계는 진입 시 앞 단계가 끝났는지
 * 확인하고, 아니면 되돌린다 — URL 을 직접 쳐서 중간에 들어오는 경로를 막는다.
 *
 * <p>회원은 사이트에 속한다. 실제 사이트는 경로 값이 아니라 {@code SiteResolveFilter} 가
 * 세워 둔 컨텍스트에서 가져오고, 마법사 도중 사이트가 바뀌면 진행 상태를 버린다.
 */
@Slf4j
@Controller
@RequestMapping("/{siteCode}/member/join")
@RequiredArgsConstructor
public class MemberJoinUsrController {

    private final MemberJoinService memberJoinService;
    private final NiceCheckProperties niceProps;

    /* ── STEP 1 — 유형 선택 ──────────────────────────────────────────────── */

    @GetMapping
    public String stepType(HttpServletRequest request, Model model) {
        SiteContext site = requireSite();
        JoinSession join = new JoinSession();
        join.setSiteId(site.getSiteId());
        join.setSiteCode(site.getSiteCode());
        // 마법사를 처음부터 다시 시작한다 — 이전 진행이 남아 있으면 섞인다
        JoinSession previous = current(request);
        if (previous != null && previous.hasOauth()) {
            // 소셜 콜백이 심어 둔 외부 프로필은 유지한다(그 흐름의 시작점이 여기다)
            join.setOauthProvider(previous.getOauthProvider());
            join.setOauthUserId(previous.getOauthUserId());
            join.setOauthEmail(previous.getOauthEmail());
            join.setOauthName(previous.getOauthName());
        }
        request.getSession(true).setAttribute(JoinSession.SESSION_KEY, join);

        model.addAttribute("join", join);
        addSteps(model, 1);
        return "front/member/join/type";
    }

    @PostMapping("/type")
    public String submitType(@RequestParam String userType, HttpServletRequest request) {
        JoinSession join = current(request);
        if (join == null) {
            return redirect("");
        }
        join.setUserType(JoinSession.TYPE_CHILD.equals(userType)
                ? JoinSession.TYPE_CHILD : JoinSession.TYPE_ADULT);
        return redirect("/terms");
    }

    /* ── STEP 2 — 약관 동의 ──────────────────────────────────────────────── */

    @GetMapping("/terms")
    public String stepTerms(HttpServletRequest request, Model model) {
        JoinSession join = current(request);
        if (join == null || join.getUserType() == null) {
            return redirect("");
        }
        model.addAttribute("join", join);
        addSteps(model, 2);
        return "front/member/join/terms";
    }

    @PostMapping("/terms")
    public String submitTerms(@RequestParam(required = false) String termsAgreeYn,
            @RequestParam(required = false) String privacyAgreeYn,
            @RequestParam(required = false) String marketingAgreeYn,
            @RequestParam(required = false) String smsAgreeYn,
            @RequestParam(required = false) String emailAgreeYn,
            HttpServletRequest request, Model model) {
        JoinSession join = current(request);
        if (join == null || join.getUserType() == null) {
            return redirect("");
        }
        // 필수 2종은 서버가 다시 본다 — 화면에서 required 를 지워도 통과하지 않는다
        if (!"Y".equals(termsAgreeYn) || !"Y".equals(privacyAgreeYn)) {
            model.addAttribute("join", join);
            model.addAttribute("flashError",
                    "이용약관과 개인정보 수집·이용에 동의해야 가입할 수 있습니다.");
            addSteps(model, 2);
            return "front/member/join/terms";
        }
        join.setTermsAgreeYn("Y");
        join.setPrivacyAgreeYn("Y");
        join.setMarketingAgreeYn(yn(marketingAgreeYn));
        join.setSmsAgreeYn(yn(smsAgreeYn));
        join.setEmailAgreeYn(yn(emailAgreeYn));
        join.setAgreed(true);
        join.setAgreedAt(LocalDateTime.now());

        return identityRequired() ? redirect("/verify") : redirect("/form");
    }

    /* ── STEP 3 — 본인인증 ───────────────────────────────────────────────── */

    /**
     * 본인인증 단계. 팝업이 인증을 마치면 부모창(이 페이지)이 다시 열리고, 그때
     * 세션에 놓인 NICE 결과를 마법사 상태로 옮긴다.
     */
    @GetMapping("/verify")
    public String stepVerify(HttpServletRequest request, Model model) {
        JoinSession join = current(request);
        if (join == null || !join.isAgreed()) {
            return redirect("");
        }
        if (!identityRequired()) {
            return redirect("/form");
        }
        consumeNiceResult(request, join);
        if (join.isVerified()) {
            return redirect("/form");
        }
        model.addAttribute("join", join);
        model.addAttribute("niceReady", niceReady());
        // 팝업이 끝나면 부모창이 이 주소로 돌아온다 — 그때 결과를 흡수한다
        model.addAttribute("niceUrl", "/member/identity/nice"
                + "?purpose=" + (join.isChild()
                        ? NiceCheckUsrController.PURPOSE_PARENT
                        : NiceCheckUsrController.PURPOSE_SELF)
                + "&siteCode=" + join.getSiteCode()
                + "&next=" + base() + "/verify");
        addSteps(model, 3);
        return "front/member/join/verify";
    }

    /* ── STEP 4 — 정보 입력 ──────────────────────────────────────────────── */

    @GetMapping("/form")
    public String stepForm(@ModelAttribute("joinForm") MemberJoinForm form,
            HttpServletRequest request, Model model) {
        JoinSession join = current(request);
        if (join == null || !join.isAgreed()) {
            return redirect("");
        }
        consumeNiceResult(request, join);
        if (!join.isIdentityStepDone(identityRequired())) {
            return redirect("/verify");
        }
        prefill(form, join);
        model.addAttribute("join", join);
        addSteps(model, 4);
        return "front/member/join/form";
    }

    @PostMapping("/form")
    public String submitForm(@ModelAttribute("joinForm") MemberJoinForm form,
            HttpServletRequest request, Model model, RedirectAttributes redirect) {
        JoinSession join = current(request);
        if (join == null || !join.isAgreed()) {
            return redirect("");
        }
        if (!join.isIdentityStepDone(identityRequired())) {
            return redirect("/verify");
        }
        form.setSiteCode(join.getSiteCode());
        String memberId;
        try {
            memberId = memberJoinService.join(form, join, join.getSiteId(),
                    request.getHeader("User-Agent"));
        } catch (IllegalArgumentException e) {
            // 비밀번호는 폼에 되돌리지 않는다 — 화면·로그에 남을 이유가 없다
            form.setPassword(null);
            form.setPasswordConfirm(null);
            model.addAttribute("flashError", e.getMessage());
            model.addAttribute("join", join);
            addSteps(model, 4);
            return "front/member/join/form";
        }

        // 신원 정보가 담긴 세션은 즉시 버린다
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(JoinSession.SESSION_KEY);
            session.removeAttribute(NiceCheckUsrController.SESSION_RESULT);
        }
        log.info("가입 마법사 완료 member={} site={}", memberId, join.getSiteCode());
        redirect.addFlashAttribute("joinedLoginId", form.getLoginId());
        return redirect("/complete");
    }

    /* ── STEP 5 — 완료 ──────────────────────────────────────────────────── */

    @GetMapping("/complete")
    public String stepComplete(Model model) {
        addSteps(model, 5);
        return "front/member/join/complete";
    }

    /* ── 내부 ───────────────────────────────────────────────────────────── */

    /**
     * 팝업이 남긴 NICE 결과를 마법사 상태로 옮긴다 — <b>한 번만</b> 쓰고 지운다.
     *
     * <p>CHILD 는 인증 주체가 법정대리인이라 결과를 parent_* 칸에 넣는다. 아이 본인의
     * 이름·생년월일은 다음 단계에서 사용자가 입력한다.
     */
    private void consumeNiceResult(HttpServletRequest request, JoinSession join) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        Object raw = session.getAttribute(NiceCheckUsrController.SESSION_RESULT);
        if (!(raw instanceof NiceCheckResult result) || !result.isSuccess()) {
            return;
        }
        session.removeAttribute(NiceCheckUsrController.SESSION_RESULT);

        if (join.isChild()) {
            join.setParentName(result.getName());
            join.setParentDi(result.getDi());
        } else {
            join.setVerifiedName(result.getName());
            join.setVerifiedBirthDate(result.getBirthDate());
            join.setVerifiedGender(toGender(result.getGender()));
            join.setVerifiedMobile(result.getMobileNo());
            join.setDi(result.getDi());
        }
        join.setVerified(true);
        log.info("가입 본인인증 반영 site={} type={}", join.getSiteCode(), join.getUserType());
    }

    /** NICE 성별(1=남, 0=여)을 tb_member.gender(M/F/N)로 옮긴다. */
    private String toGender(String niceGender) {
        if ("1".equals(niceGender)) {
            return "M";
        }
        return "0".equals(niceGender) ? "F" : "N";
    }

    /** 확정된 값으로 폼을 채운다 — 화면에서는 읽기 전용으로 보여 준다. */
    private void prefill(MemberJoinForm form, JoinSession join) {
        if (join.isVerified() && !join.isChild()) {
            form.setMemberName(join.getVerifiedName());
            form.setBirthDate(join.getVerifiedBirthDate());
            form.setGender(join.getVerifiedGender());
            if (isBlank(form.getPhone())) {
                form.setPhone(join.getVerifiedMobile());
            }
        }
        if (join.hasOauth() && isBlank(form.getEmail())) {
            form.setEmail(join.getOauthEmail());
        }
        if (join.hasOauth() && isBlank(form.getNickname())) {
            form.setNickname(join.getOauthName());
        }
        form.setSiteCode(join.getSiteCode());
    }

    /**
     * 진행 중인 마법사 상태 — 사이트가 어긋나면 없는 것으로 본다.
     *
     * <p>다른 사이트로 이동한 채 이어 가면 동의·인증이 엉뚱한 사이트에 적용된다.
     */
    private JoinSession current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object raw = session.getAttribute(JoinSession.SESSION_KEY);
        if (!(raw instanceof JoinSession join)) {
            return null;
        }
        SiteContext site = SiteContextHolder.get();
        if (site == null || !site.getSiteId().equals(join.getSiteId())) {
            session.removeAttribute(JoinSession.SESSION_KEY);
            return null;
        }
        return join;
    }

    /** 본인인증을 요구하는가 — 끄면 STEP 3 이 통째로 빠진다(계약 전 개발용). */
    private boolean identityRequired() {
        return niceProps.isEnabled();
    }

    /** 계약 자격이 들어와 실제 인증 창을 띄울 수 있는가. */
    private boolean niceReady() {
        return !isBlank(niceProps.getSiteCode()) && !isBlank(niceProps.getSitePassword());
    }

    private void addSteps(Model model, int step) {
        model.addAttribute("step", step);
        model.addAttribute("identityRequired", identityRequired());
    }

    private SiteContext requireSite() {
        SiteContext site = SiteContextHolder.get();
        if (site == null) {
            throw new IllegalArgumentException("사이트를 확인할 수 없습니다.");
        }
        return site;
    }

    private String redirect(String suffix) {
        return "redirect:" + base() + suffix;
    }

    private String base() {
        SiteContext site = SiteContextHolder.get();
        return "/" + (site == null ? "" : site.getSiteCode()) + "/member/join";
    }

    private String yn(String value) {
        return "Y".equals(value) ? "Y" : "N";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
