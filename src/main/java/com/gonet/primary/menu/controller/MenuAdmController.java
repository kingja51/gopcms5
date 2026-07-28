package com.gonet.primary.menu.controller;

import com.gonet.primary.content.service.ContentService;
import com.gonet.primary.menu.dto.MenuAdmDto;
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
 * 메뉴 관리 (/adm/menu) — 사이트를 먼저 고르고 그 사이트의 트리를 다룬다.
 *
 * <p>목록은 페이징하지 않는다(트리는 잘리면 안 된다). 사이트 미지정이면 기본 사이트를
 * 잡아 준다 — 빈 화면보다 바로 만질 수 있는 트리를 보여주는 편이 낫다.
 */
@Controller
@RequiredArgsConstructor
public class MenuAdmController {

    private final MenuService menuService;
    private final SiteService siteService;
    private final ContentService contentService;

    @GetMapping("/adm/menu")
    public String list(@RequestParam(required = false) String siteId,
            @RequestParam(required = false) String keyword, Model model) {
        List<SiteAdmDto> sites = siteService.getAllForSelect();
        String targetSiteId = resolveSiteId(siteId, sites);

        model.addAttribute("sites", sites);
        model.addAttribute("siteId", targetSiteId);
        model.addAttribute("keyword", keyword);
        model.addAttribute("menus",
                targetSiteId == null ? List.of() : menuService.getAdmList(targetSiteId, keyword));
        return "adm/menu/list";
    }

    @GetMapping("/adm/menu/form")
    public String form(@RequestParam(required = false) String menuId,
            @RequestParam(required = false) String siteId, Model model) {
        MenuAdmDto menu = menuId == null ? newMenu(siteId) : menuService.getAdm(menuId);
        if (menu == null) {
            return "redirect:/adm/menu";
        }
        model.addAttribute("menu", menu);
        addFormOptions(model, menu);
        return "adm/menu/form";
    }

    @PostMapping("/adm/menu/save")
    public String save(@ModelAttribute("menu") MenuAdmDto menu, Model model,
            RedirectAttributes redirect) {
        try {
            menuService.saveAdm(menu);
        } catch (IllegalArgumentException e) {
            // 입력값을 유지한 채 폼으로 — 모델명은 뷰가 참조하는 이름과 일치시킨다
            model.addAttribute("flashError", e.getMessage());
            addFormOptions(model, menu);
            return "adm/menu/form";
        }
        redirect.addFlashAttribute("flashOk", "저장되었습니다. GNB·사이트맵에 즉시 반영됩니다.");
        return "redirect:/adm/menu?siteId=" + menu.getSiteId();
    }

    @PostMapping("/adm/menu/delete")
    public String delete(@RequestParam String menuId, @RequestParam String siteId,
            RedirectAttributes redirect) {
        try {
            menuService.deleteAdm(menuId);
            redirect.addFlashAttribute("flashOk", "삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/adm/menu?siteId=" + siteId;
    }

    /** 요청 사이트 → 없으면 첫 사이트. 목록이 아예 비면 null(사이트부터 만들라는 안내). */
    private String resolveSiteId(String siteId, List<SiteAdmDto> sites) {
        if (siteId != null && !siteId.isBlank()) {
            return siteId;
        }
        return sites.isEmpty() ? null : sites.get(0).getSiteId();
    }

    private MenuAdmDto newMenu(String siteId) {
        MenuAdmDto menu = new MenuAdmDto();
        menu.setSiteId(siteId);
        menu.setMenuType("FOLDER");
        menu.setUseYn("Y");
        menu.setAuthRequiredYn("N");
        return menu;
    }

    /** 상위 메뉴·연결 컨텐츠 후보 — 같은 사이트 안에서만 고를 수 있어야 한다. */
    private void addFormOptions(Model model, MenuAdmDto menu) {
        model.addAttribute("sites", siteService.getAllForSelect());
        if (menu.getSiteId() != null) {
            model.addAttribute("parents", menuService.getAdmList(menu.getSiteId(), null));
            model.addAttribute("contents", contentService.getAllForSelect(menu.getSiteId()));
        } else {
            model.addAttribute("parents", List.of());
            model.addAttribute("contents", List.of());
        }
    }
}
