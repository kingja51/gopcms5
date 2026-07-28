package com.gonet.primary.theme.controller;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.template.service.TemplateService;
import com.gonet.primary.theme.dto.ThemeAdmDto;
import com.gonet.primary.theme.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 테마 관리 (/adm/theme) — 3축 중 색 축(템플릿 종속). */
@Controller
@RequiredArgsConstructor
public class ThemeAdmController {

    private static final String LIST = "redirect:/adm/theme";

    private final ThemeService themeService;
    private final TemplateService templateService;

    @GetMapping("/adm/theme")
    public String list(@ModelAttribute("cond") PageRequest cond, Model model) {
        model.addAttribute("page", themeService.getAdmPage(cond));
        return "adm/theme/list";
    }

    @GetMapping("/adm/theme/form")
    public String form(@RequestParam(required = false) String themeId, Model model) {
        ThemeAdmDto theme = themeId == null ? newTheme() : themeService.getAdm(themeId);
        if (theme == null) {
            return LIST;
        }
        model.addAttribute("theme", theme);
        model.addAttribute("templates", templateService.getAllForSelect());
        return "adm/theme/form";
    }

    @PostMapping("/adm/theme/save")
    public String save(@ModelAttribute("theme") ThemeAdmDto theme, Model model,
            RedirectAttributes redirect) {
        try {
            themeService.saveAdm(theme);
        } catch (IllegalArgumentException e) {
            model.addAttribute("flashError", e.getMessage());
            model.addAttribute("templates", templateService.getAllForSelect());
            return "adm/theme/form";
        }
        redirect.addFlashAttribute("flashOk", "저장되었습니다.");
        return LIST;
    }

    @PostMapping("/adm/theme/delete")
    public String delete(@RequestParam String themeId, RedirectAttributes redirect) {
        try {
            themeService.deleteAdm(themeId);
            redirect.addFlashAttribute("flashOk", "삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return LIST;
    }

    private ThemeAdmDto newTheme() {
        ThemeAdmDto theme = new ThemeAdmDto();
        theme.setUseYn("Y");
        theme.setCssClass("");
        return theme;
    }
}
