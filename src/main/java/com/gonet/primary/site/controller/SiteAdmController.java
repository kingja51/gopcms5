package com.gonet.primary.site.controller;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.layout.service.LayoutService;
import com.gonet.primary.site.dto.SiteAdmDto;
import com.gonet.primary.site.service.SiteService;
import com.gonet.primary.template.service.TemplateService;
import com.gonet.primary.theme.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 사이트 관리 (/adm/site) — 3축(템플릿·테마·레이아웃) 선택과 기본 사이트 지정.
 *
 * <p>접근 규칙은 별도 등록이 필요 없다 — {@code /adm/**} ROLE_ADMIN 규칙(priority 20)이
 * 이미 덮는다(conventions §7).
 *
 * <p>저장은 {@code SiteService} 가 siteContext 캐시를 비우므로 <b>재기동 없이</b> 반영된다
 * (P5 에서 "반영은 재기동 경유"로 남겨 둔 숙제를 여기서 회수).
 *
 * <p>폼 모델명이 {@code site} 가 아니라 <b>{@code siteForm}</b> 인 이유: 전역
 * {@code SiteContextModelAdvice} 가 이미 {@code site}(렌더용 SiteContext)를 넣는데,
 * {@code /adm/**} 에서는 그 값이 null 이라 이름이 겹치면 폼 재렌더가 NPE 로 죽는다(P7 실측).
 */
@Controller
@RequiredArgsConstructor
public class SiteAdmController {

    private static final String LIST = "redirect:/adm/site";

    private final SiteService siteService;
    private final TemplateService templateService;
    private final ThemeService themeService;
    private final LayoutService layoutService;

    @GetMapping("/adm/site")
    public String list(@ModelAttribute("cond") PageRequest cond, Model model) {
        model.addAttribute("page", siteService.getAdmPage(cond));
        return "adm/site/list";
    }

    /** 등록·수정 공용 폼 — siteId 가 없으면 신규. */
    @GetMapping("/adm/site/form")
    public String form(@RequestParam(required = false) String siteId, Model model) {
        SiteAdmDto site = siteId == null ? newSite() : siteService.getAdm(siteId);
        if (site == null) {
            return LIST;
        }
        model.addAttribute("siteForm", site);
        addSelectOptions(model);
        return "adm/site/form";
    }

    @PostMapping("/adm/site/save")
    public String save(@ModelAttribute("siteForm") SiteAdmDto site, Model model,
            RedirectAttributes redirect) {
        try {
            siteService.saveAdm(site);
        } catch (IllegalArgumentException e) {
            // 입력값을 유지한 채 폼으로 — 리다이렉트하면 사용자가 다시 타이핑해야 한다
            model.addAttribute("flashError", e.getMessage());
            addSelectOptions(model);
            return "adm/site/form";
        }
        redirect.addFlashAttribute("flashOk", "저장되었습니다. 사이트 화면에 즉시 반영됩니다.");
        return LIST;
    }

    @PostMapping("/adm/site/delete")
    public String delete(@RequestParam String siteId, RedirectAttributes redirect) {
        try {
            siteService.deleteAdm(siteId);
            redirect.addFlashAttribute("flashOk", "삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return LIST;
    }

    private SiteAdmDto newSite() {
        SiteAdmDto site = new SiteAdmDto();
        site.setDefaultLang("ko");
        site.setDefaultYn("N");
        site.setUseYn("Y");
        return site;
    }

    /** 3축 선택 상자 + 상위 사이트 선택 — 폼을 다시 그리는 모든 경로에서 필요하다. */
    private void addSelectOptions(Model model) {
        model.addAttribute("templates", templateService.getAllForSelect());
        model.addAttribute("themes", themeService.getAllForSelect());
        model.addAttribute("layouts", layoutService.getAllForSelect());
        model.addAttribute("sites", siteService.getAllForSelect());
    }
}
