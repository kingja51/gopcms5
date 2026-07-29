package com.gonet.primary.member.controller;

import com.gonet.common.web.LoginPrincipal;
import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.auth.service.PasswordService;
import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.dto.MemberProfileForm;
import com.gonet.primary.member.service.MemberLifecycleService;
import com.gonet.primary.member.service.MemberProfileService;
import com.gonet.primary.member.service.StepUpAuth;
import com.gonet.primary.site.dto.SiteContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 마이페이지 — {@code /{siteCode}/member/mypage}.
 *
 * <p>개인정보 화면은 로그인만으로는 열리지 않는다. <b>step-up 재인증</b>(비밀번호 재확인,
 * 5분 유효)을 통과해야 들어간다 — 자리를 비운 사이 남이 브라우저를 만지거나 세션이
 * 탈취된 경우를 한 겹 더 막는 것이 목적이다.
 *
 * <p>회원 영역(사이트별 member 하위 경로)은 이미 인증이 필요하므로 로그인 여부는 다시 묻지
 * 않는다. 다만 <b>주체가 회원인지</b>는 확인한다 — 관리자 세션으로 회원 마이페이지에
 * 들어오면 대상 회원이 없다.
 */
@Controller
@RequestMapping("/{siteCode}/member/mypage")
@RequiredArgsConstructor
public class MemberMyPageUsrController {

    private final MemberProfileService profileService;
    private final PasswordService passwordService;
    private final StepUpAuth stepUpAuth;
    private final PasswordEncoder passwordEncoder;
    private final MemberLifecycleService lifecycleService;

    @GetMapping
    public String mypage(HttpServletRequest request, Model model) {
        LoginPrincipal me = requireMember();
        if (!stepUpAuth.isVerified(request)) {
            return redirectVerify();
        }
        model.addAttribute("member", profileService.get(me.userId()));
        return "front/member/mypage";
    }

    /* ── step-up 재인증 ────────────────────────────────────────────────── */

    @GetMapping("/verify")
    public String verifyForm() {
        requireMember();
        return "front/member/verify";
    }

    @PostMapping("/verify")
    public String verify(@RequestParam String password, HttpServletRequest request,
            Model model, RedirectAttributes redirect) {
        LoginPrincipal me = requireMember();
        MemberDto member = profileService.get(me.userId());

        if (member != null && passwordEncoder.matches(password, member.getPassword())) {
            stepUpAuth.markVerified(request);
            return "redirect:" + base() + "/mypage";
        }
        boolean purged = stepUpAuth.markFailed(request);
        model.addAttribute("flashError", purged
                ? "비밀번호를 여러 번 틀렸습니다. 잠시 후 다시 시도해 주세요."
                : "비밀번호가 올바르지 않습니다.");
        return "front/member/verify";
    }

    /* ── 개인정보 수정 ─────────────────────────────────────────────────── */

    @PostMapping
    public String update(@ModelAttribute MemberProfileForm form, HttpServletRequest request,
            Model model, RedirectAttributes redirect) {
        LoginPrincipal me = requireMember();
        // 폼을 열 때 통과했더라도 저장 시점에 다시 본다 — 그 사이 5분이 지났을 수 있다
        if (!stepUpAuth.isVerified(request)) {
            return redirectVerify();
        }
        try {
            profileService.update(me.userId(), form, request.getHeader("User-Agent"));
        } catch (IllegalArgumentException e) {
            model.addAttribute("flashError", e.getMessage());
            model.addAttribute("member", profileService.get(me.userId()));
            return "front/member/mypage";
        }
        redirect.addFlashAttribute("flashOk", "저장되었습니다.");
        return "redirect:" + base() + "/mypage";
    }

    /* ── 비밀번호 변경 ─────────────────────────────────────────────────── */

    @GetMapping("/password")
    public String passwordForm(HttpServletRequest request) {
        requireMember();
        // 비밀번호 변경은 현재 비밀번호를 다시 묻기 때문에 그 자체가 재인증이다
        return "front/member/password";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam String currentPassword,
            @RequestParam String newPassword, @RequestParam String confirmPassword,
            HttpServletRequest request, Model model, RedirectAttributes redirect) {
        LoginPrincipal me = requireMember();
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("flashError", "새 비밀번호가 일치하지 않습니다.");
            return "front/member/password";
        }
        try {
            passwordService.change("MEMBER", me.userId(), currentPassword, newPassword);
        } catch (IllegalArgumentException | BadCredentialsException e) {
            model.addAttribute("flashError", e.getMessage());
            return "front/member/password";
        }
        // 비밀번호가 바뀌면 세션 성격이 달라진다 — 재인증 흔적을 지우고 다시 로그인시킨다
        stepUpAuth.clear(request);
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        redirect.addFlashAttribute("flashOk", "비밀번호를 변경했습니다. 다시 로그인해 주세요.");
        return "redirect:/login?siteCode=" + siteCode();
    }

    /* ── 셀프 탈퇴 ─────────────────────────────────────────────────────── */

    @GetMapping("/withdraw")
    public String withdrawForm(HttpServletRequest request) {
        requireMember();
        // 되돌릴 수 없는 작업이라 재인증을 반드시 통과해야 한다
        if (!stepUpAuth.isVerified(request)) {
            return redirectVerify();
        }
        return "front/member/withdraw";
    }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam(required = false) String reason,
            @RequestParam String confirm, HttpServletRequest request,
            Model model, RedirectAttributes redirect) {
        LoginPrincipal me = requireMember();
        if (!stepUpAuth.isVerified(request)) {
            return redirectVerify();
        }
        // 오클릭 방지 — 문구를 직접 입력해야 진행된다(되돌릴 수 없다)
        if (!"탈퇴합니다".equals(confirm == null ? "" : confirm.trim())) {
            model.addAttribute("flashError", "확인 문구를 정확히 입력해 주세요.");
            return "front/member/withdraw";
        }
        // 배치와 <b>같은 경로</b>를 탄다 — 경로가 둘이면 정책이 갈린다
        lifecycleService.withdraw(me.userId(),
                reason == null || reason.isBlank() ? "회원 요청(셀프 탈퇴)" : reason.trim(),
                "USER_REQUEST");

        stepUpAuth.clear(request);
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        redirect.addFlashAttribute("flashOk", "탈퇴가 완료되었습니다. 이용해 주셔서 감사합니다.");
        return "redirect:/" + siteCode() + "/index";
    }

    /* ── 부속 ─────────────────────────────────────────────────────────── */

    /** 주체가 회원인지 확인 — 관리자 세션으로 들어오면 대상 회원이 없다. */
    private LoginPrincipal requireMember() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginPrincipal principal)
                || !"MEMBER".equals(principal.userType())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "회원만 이용할 수 있습니다.");
        }
        return principal;
    }

    private String redirectVerify() {
        return "redirect:" + base() + "/mypage/verify";
    }

    private String base() {
        return "/" + siteCode() + "/member";
    }

    private String siteCode() {
        SiteContext site = SiteContextHolder.get();
        return site == null ? "" : site.getSiteCode();
    }
}
