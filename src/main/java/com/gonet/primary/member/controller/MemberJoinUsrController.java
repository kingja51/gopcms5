package com.gonet.primary.member.controller;

import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.member.dto.MemberJoinForm;
import com.gonet.primary.member.service.MemberJoinService;
import com.gonet.primary.site.dto.SiteContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 회원 가입 — {@code /{siteCode}/member/join}.
 *
 * <p>회원은 사이트에 속한다. 그래서 경로에 siteCode 가 들어가고, 실제 사이트는
 * {@code SiteResolveFilter} 가 세워 둔 컨텍스트에서 가져온다 — 경로 값을 다시 믿으면
 * 다른 사이트에 가입시킬 수 있다.
 *
 * <p>약관 동의는 <b>세션이 아니라 폼</b>으로 받되 서버가 다시 검증한다. 원전의 7단계
 * 플로우(유형선택→약관→본인인증→폼→가입→완료)는 본인인증(NICE) 연동이 붙는 시점에
 * 단계를 나눈다 — 지금은 약관+폼 한 화면이다.
 */
@Controller
@RequestMapping("/{siteCode}/member/join")
@RequiredArgsConstructor
public class MemberJoinUsrController {

    private final MemberJoinService memberJoinService;

    @GetMapping
    public String form(@ModelAttribute("joinForm") MemberJoinForm form, Model model) {
        SiteContext site = SiteContextHolder.get();
        if (site != null) {
            form.setSiteCode(site.getSiteCode());
        }
        return "front/member/join";
    }

    @PostMapping
    public String join(@ModelAttribute("joinForm") MemberJoinForm form,
            HttpServletRequest request, Model model, RedirectAttributes redirect) {
        SiteContext site = SiteContextHolder.get();
        if (site == null) {
            throw new IllegalArgumentException("사이트를 확인할 수 없습니다.");
        }
        form.setSiteCode(site.getSiteCode());
        try {
            memberJoinService.join(form, site.getSiteId(), request.getHeader("User-Agent"));
        } catch (IllegalArgumentException e) {
            // 비밀번호는 폼에 되돌리지 않는다 — 화면·로그에 남을 이유가 없다
            form.setPassword(null);
            form.setPasswordConfirm(null);
            model.addAttribute("flashError", e.getMessage());
            return "front/member/join";
        }
        redirect.addFlashAttribute("flashOk", "가입이 완료되었습니다. 로그인해 주세요.");
        return "redirect:/login?siteCode=" + site.getSiteCode();
    }
}
