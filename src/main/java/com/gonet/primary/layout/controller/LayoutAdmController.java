package com.gonet.primary.layout.controller;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.layout.dto.LayoutAdmDto;
import com.gonet.primary.layout.service.LayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 레이아웃 관리 (/adm/layout) — 3축 중 구조 축. */
@Controller
@RequiredArgsConstructor
public class LayoutAdmController {

    private static final String LIST = "redirect:/adm/layout";

    private final LayoutService layoutService;

    @GetMapping("/adm/layout")
    public String list(@ModelAttribute("cond") PageRequest cond, Model model) {
        model.addAttribute("page", layoutService.getAdmPage(cond));
        return "adm/layout/list";
    }

    @GetMapping("/adm/layout/form")
    public String form(@RequestParam(required = false) String layoutId, Model model) {
        LayoutAdmDto layout = layoutId == null ? newLayout() : layoutService.getAdm(layoutId);
        if (layout == null) {
            return LIST;
        }
        model.addAttribute("layout", layout);
        return "adm/layout/form";
    }

    @PostMapping("/adm/layout/save")
    public String save(@ModelAttribute("layout") LayoutAdmDto layout, Model model,
            RedirectAttributes redirect) {
        try {
            layoutService.saveAdm(layout);
        } catch (IllegalArgumentException e) {
            model.addAttribute("flashError", e.getMessage());
            return "adm/layout/form";
        }
        redirect.addFlashAttribute("flashOk",
                "저장되었습니다. 뷰 폴더 templates/layouts/" + layout.getLayoutCode()
                        + "/layout.html 이 있어야 사이트가 렌더됩니다.");
        return LIST;
    }

    @PostMapping("/adm/layout/delete")
    public String delete(@RequestParam String layoutId, RedirectAttributes redirect) {
        try {
            layoutService.deleteAdm(layoutId);
            redirect.addFlashAttribute("flashOk", "삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return LIST;
    }

    private LayoutAdmDto newLayout() {
        LayoutAdmDto layout = new LayoutAdmDto();
        layout.setUseYn("Y");
        return layout;
    }
}
