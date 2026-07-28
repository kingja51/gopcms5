package com.gonet.primary.auth.controller;

import com.gonet.common.web.LoginPrincipal;
import com.gonet.primary.auth.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 관리자 본인 비밀번호 변경 (/adm/password).
 *
 * <p>접근 규칙은 별도 등록이 필요 없다 — {@code /adm/**} ROLE_ADMIN 규칙(priority 20)이
 * 이미 덮는다(conventions §7: 상위 네임스페이스 규칙이 있으면 하위 URL 은 자동 적용).
 *
 * <p>만료된 비밀번호로는 로그인 자체가 막히므로(FAIL_EXPIRED) 이 화면은 <b>만료 전 자율 변경</b>
 * 경로다. 만료된 계정 복구는 다른 관리자의 재설정 — 셀프 재설정(본인확인 경유)은 후속.
 */
@Controller
@RequiredArgsConstructor
public class PasswordAdmController {

    private final PasswordService passwordService;

    @GetMapping("/adm/password")
    public String form() {
        return "adm/password";
    }

    @PostMapping("/adm/password")
    public String change(@AuthenticationPrincipal LoginPrincipal principal,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpServletRequest request, Model model) {
        try {
            if (!newPassword.equals(confirmPassword)) {
                throw new IllegalArgumentException("새 비밀번호가 확인 값과 일치하지 않습니다.");
            }
            passwordService.change("ADMIN", principal.userId(), currentPassword, newPassword);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "adm/password";
        }
        // 변경 후 재로그인 강제 — 세션을 직접 무효화한다(/adm/logout 은 POST 전용이라
        // 리다이렉트로는 탈 수 없다). 예전 자격으로 살아 있는 세션을 남기지 않는 것이 목적.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return "redirect:/adm/login?pwchanged";
    }
}
