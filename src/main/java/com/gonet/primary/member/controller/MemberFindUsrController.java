package com.gonet.primary.member.controller;

import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.member.service.MemberFindService;
import com.gonet.primary.site.dto.SiteContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 아이디 찾기 — {@code /{siteCode}/member/find-id}.
 *
 * <p>결과가 있든 없든 <b>같은 화면·같은 형태</b>로 돌려준다. "일치하는 계정이 없습니다" 를
 * 따로 보여주면 이메일이 가입돼 있는지 확인하는 도구가 된다.
 */
@Controller
@RequestMapping("/{siteCode}/member/find-id")
@RequiredArgsConstructor
public class MemberFindUsrController {

    private final MemberFindService memberFindService;

    @GetMapping
    public String form() {
        return "front/member/find-id";
    }

    @PostMapping
    public String find(@RequestParam String memberName, @RequestParam String email, Model model) {
        SiteContext site = SiteContextHolder.get();
        String masked = site == null
                ? null : memberFindService.findLoginIdMasked(site.getSiteId(), memberName, email);

        model.addAttribute("searched", true);
        model.addAttribute("maskedLoginId", masked);
        return "front/member/find-id";
    }
}
