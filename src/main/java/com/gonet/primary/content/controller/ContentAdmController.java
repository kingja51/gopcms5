package com.gonet.primary.content.controller;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.content.dto.ContentAdmDto;
import com.gonet.primary.content.service.ContentService;
import com.gonet.primary.menu.service.MenuService;
import com.gonet.primary.site.dto.SiteAdmDto;
import com.gonet.primary.site.service.SiteService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 컨텐츠 관리 (/adm/content) — 사이트 단위로 목록·편집.
 *
 * <p>메뉴 관리와 마찬가지로 사이트 선택이 앞선다. slug 규칙 위반은 서비스가 막고,
 * 저장 후에는 사용자 화면 링크를 바로 눌러 확인할 수 있게 목록에 "보기"를 둔다.
 */
@Controller
@RequiredArgsConstructor
public class ContentAdmController {

    private final ContentService contentService;
    private final SiteService siteService;
    private final MenuService menuService;

    @GetMapping("/adm/content")
    public String list(@RequestParam(required = false) String siteId,
            @ModelAttribute("cond") PageRequest cond, Model model) {
        List<SiteAdmDto> sites = siteService.getAllForSelect();
        String targetSiteId = resolveSiteId(siteId, sites);

        model.addAttribute("sites", sites);
        model.addAttribute("siteId", targetSiteId);
        model.addAttribute("siteCode", siteCodeOf(sites, targetSiteId));
        model.addAttribute("page", targetSiteId == null
                ? new com.gonet.common.web.PageResult<ContentAdmDto>(List.of(), 0, 1, cond.getSize())
                : contentService.getAdmPage(targetSiteId, cond));
        return "adm/content/list";
    }

    @GetMapping("/adm/content/form")
    public String form(@RequestParam(required = false) String contentId,
            @RequestParam(required = false) String siteId, Model model) {
        ContentAdmDto content = contentId == null ? newContent(siteId)
                : contentService.getAdm(contentId);
        if (content == null) {
            return "redirect:/adm/content";
        }
        model.addAttribute("content", content);
        addFormOptions(model, content);
        return "adm/content/form";
    }

    @PostMapping("/adm/content/save")
    public String save(@ModelAttribute("content") ContentAdmDto content, Model model,
            RedirectAttributes redirect) {
        try {
            contentService.saveAdm(content);
        } catch (IllegalArgumentException e) {
            model.addAttribute("flashError", e.getMessage());
            addFormOptions(model, content);
            return "adm/content/form";
        }
        redirect.addFlashAttribute("flashOk", "저장되었습니다.");
        return "redirect:/adm/content?siteId=" + content.getSiteId();
    }

    @PostMapping("/adm/content/delete")
    public String delete(@RequestParam String contentId, @RequestParam String siteId,
            RedirectAttributes redirect) {
        contentService.deleteAdm(contentId);
        redirect.addFlashAttribute("flashOk", "삭제되었습니다.");
        return "redirect:/adm/content?siteId=" + siteId;
    }

    private String resolveSiteId(String siteId, List<SiteAdmDto> sites) {
        if (siteId != null && !siteId.isBlank()) {
            return siteId;
        }
        return sites.isEmpty() ? null : sites.get(0).getSiteId();
    }

    /** 목록의 "보기" 링크(/{siteCode}/{slug})를 만들려면 코드가 필요하다. */
    private String siteCodeOf(List<SiteAdmDto> sites, String siteId) {
        return sites.stream().filter(s -> s.getSiteId().equals(siteId))
                .map(SiteAdmDto::getSiteCode).findFirst().orElse(null);
    }

    private ContentAdmDto newContent(String siteId) {
        ContentAdmDto content = new ContentAdmDto();
        content.setSiteId(siteId);
        content.setStatus("DRAFT");
        return content;
    }

    /** 연결 메뉴 후보 — 같은 사이트의 메뉴만. */
    private void addFormOptions(Model model, ContentAdmDto content) {
        model.addAttribute("sites", siteService.getAllForSelect());
        model.addAttribute("menus", content.getSiteId() == null
                ? List.of() : menuService.getAdmList(content.getSiteId(), null));
    }
}
