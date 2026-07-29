package com.gonet.primary.member.controller;

import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.member.service.MemberPasswordResetService;
import com.gonet.primary.site.dto.SiteContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 비밀번호 찾기 — {@code /{siteCode}/member/find-password}.
 *
 * <p>계정이 있든 없든 <b>같은 안내</b>를 보여준다. "등록되지 않은 계정입니다" 를 따로
 * 알려 주면 아이디·이메일 조합의 가입 여부를 확인하는 도구가 된다.
 */
@Controller
@RequestMapping("/{siteCode}/member/find-password")
@RequiredArgsConstructor
public class MemberPasswordResetUsrController {

    private final MemberPasswordResetService passwordResetService;

    @GetMapping
    public String form() {
        return "front/member/find-password";
    }

    @PostMapping
    public String reset(@RequestParam String loginId, @RequestParam String email, Model model) {
        SiteContext site = SiteContextHolder.get();
        if (site != null) {
            passwordResetService.issueTemporaryPassword(site.getSiteId(), loginId, email);
        }
        // 결과와 무관하게 같은 화면 — 서비스도 성공/실패를 돌려주지 않는다
        model.addAttribute("submitted", true);
        return "front/member/find-password";
    }
}
