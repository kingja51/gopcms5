package com.gonet.primary.auth.controller;

import com.gonet.common.web.ClientIpResolver;
import com.gonet.config.security.CaptchaService;
import com.gonet.primary.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 관리자 로그인 (/adm/login) — IP 게이트 (정책 2026-07-28):
 * 요청 IP 가 tb_admin_allow_ip 에 존재하면 로그인 폼, 아니면 "/" 리다이렉트.
 * 계정별 (admin_id, ip) 매칭은 AdminAuthenticationProvider 가 2차 검증.
 */
@Controller
@RequiredArgsConstructor
public class LoginAdmController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;
    private final CaptchaService captchaService;

    @GetMapping("/adm/login")
    public String loginForm(HttpServletRequest request, Model model) {
        if (!authService.isIpAllowedForLoginForm(clientIpResolver.resolve(request))) {
            return "redirect:/"; // 미등록 IP — 폼 자체를 노출하지 않음
        }
        // 잠금을 겪은 계정으로 시도한 세션에만 문답이 붙는다 (CaptchaService 가 표시)
        if (captchaService.isRequired(request.getSession(false))) {
            model.addAttribute("captchaQuestion", captchaService.issue(request.getSession(true)));
        }
        return "adm/login";
    }

}
