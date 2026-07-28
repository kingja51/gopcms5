package com.gonet.primary.auth.controller;

import com.gonet.primary.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
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

    @GetMapping("/adm/login")
    public String loginForm(HttpServletRequest request) {
        if (!authService.isIpAllowedForLoginForm(request.getRemoteAddr())) {
            return "redirect:/"; // 미등록 IP — 폼 자체를 노출하지 않음
        }
        return "adm/login";
    }

    /** 관리자 대시보드 골격 — 관리 모듈(P7)에서 확장 */
    @GetMapping("/adm/index")
    public String index() {
        return "adm/index";
    }
}
