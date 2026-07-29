package com.gonet.primary.auth.controller;

import com.gonet.config.security.CaptchaService;
import com.gonet.primary.member.oauth2.service.OAuth2Service;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 사용자 로그인 (/login?siteCode=ai) — 사이트 스코프 로그인 폼.
 * SiteResolveFilter 가 siteCode 파라미터로 SiteContext 를 해석하므로
 * 폼은 해당 사이트의 레이아웃·테마 안에서 렌더된다.
 */
@Controller
@RequiredArgsConstructor
public class LoginUsrController {

    private final CaptchaService captchaService;
    private final OAuth2Service oauth2Service;

    @GetMapping("/login")
    public String loginForm(HttpServletRequest request, Model model) {
        if (captchaService.isRequired(request.getSession(false))) {
            model.addAttribute("captchaQuestion", captchaService.issue(request.getSession(true)));
        }
        // 자격이 들어온 provider 만 버튼을 그린다 — 눌러도 안 되는 버튼을 두지 않는다
        model.addAttribute("socialProviders", oauth2Service.configuredProviders());
        return "front/member/login";
    }
}
