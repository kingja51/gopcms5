package com.gonet.primary.template.controller;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.layout.service.LayoutService;
import com.gonet.primary.template.dto.TemplateAdmDto;
import com.gonet.primary.template.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 템플릿 관리 (/adm/template) — 3축 중 시각 언어 축. */
@Controller
@RequiredArgsConstructor
public class TemplateAdmController {

    private static final String LIST = "redirect:/adm/template";

    private final TemplateService templateService;
    private final LayoutService layoutService;

    @GetMapping("/adm/template")
    public String list(@ModelAttribute("cond") PageRequest cond, Model model) {
        model.addAttribute("page", templateService.getAdmPage(cond));
        return "adm/template/list";
    }

    @GetMapping("/adm/template/form")
    public String form(@RequestParam(required = false) String templateId, Model model) {
        TemplateAdmDto template =
                templateId == null ? newTemplate() : templateService.getAdm(templateId);
        if (template == null) {
            return LIST;
        }
        model.addAttribute("template", template);
        model.addAttribute("layouts", layoutService.getAllForSelect());
        return "adm/template/form";
    }

    @PostMapping("/adm/template/save")
    public String save(@ModelAttribute("template") TemplateAdmDto template, Model model,
            RedirectAttributes redirect) {
        try {
            templateService.saveAdm(template);
        } catch (IllegalArgumentException e) {
            model.addAttribute("flashError", e.getMessage());
            model.addAttribute("layouts", layoutService.getAllForSelect());
            return "adm/template/form";
        }
        redirect.addFlashAttribute("flashOk",
                "저장되었습니다. /tmpl/css/" + template.getTemplateCode()
                        + ".css 가 있어야 시각 언어가 적용됩니다.");
        return LIST;
    }

    @PostMapping("/adm/template/delete")
    public String delete(@RequestParam String templateId, RedirectAttributes redirect) {
        try {
            templateService.deleteAdm(templateId);
            redirect.addFlashAttribute("flashOk", "삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return LIST;
    }

    private TemplateAdmDto newTemplate() {
        TemplateAdmDto template = new TemplateAdmDto();
        template.setUseYn("Y");
        return template;
    }
}
