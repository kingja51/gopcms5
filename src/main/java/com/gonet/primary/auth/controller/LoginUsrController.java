package com.gonet.primary.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 사용자 로그인 (/login?siteCode=ai) — 사이트 스코프 로그인 폼.
 * SiteResolveFilter 가 siteCode 파라미터로 SiteContext 를 해석하므로
 * 폼은 해당 사이트의 레이아웃·테마 안에서 렌더된다.
 */
@Controller
public class LoginUsrController {

    @GetMapping("/login")
    public String loginForm() {
        return "front/member/login";
    }
}
