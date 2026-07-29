package com.gonet.primary.auth.controller;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.auth.dto.UrlAccessRule;
import com.gonet.primary.auth.service.RoleService;
import com.gonet.primary.auth.service.UrlAccessService;
import com.gonet.primary.auth.service.UrlAccessServiceImpl;
import com.gonet.primary.site.service.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * URL 접근 규칙 관리 (/adm/url-access) — 인가의 유일 원천을 화면에서 다룬다.
 *
 * <p>이 화면이 생기기 전까지 규칙은 SQL 로만 고칠 수 있었다(P6-2). 저장하면 서비스가
 * 캐시를 비우므로 <b>다음 요청부터</b> 새 규칙으로 판정한다.
 *
 * <p>목록 순서는 실제 평가 순서와 같다 — 위에 있는 규칙이 먼저 매칭되고 거기서 끝난다.
 */
@Controller
@RequiredArgsConstructor
public class UrlAccessAdmController {

    private static final String LIST = "redirect:/adm/url-access";

    private final UrlAccessService urlAccessService;
    private final RoleService roleService;
    private final SiteService siteService;

    @GetMapping("/adm/url-access")
    public String list(@ModelAttribute("cond") PageRequest cond, Model model) {
        model.addAttribute("page", urlAccessService.getAdmPage(cond));
        return "adm/url-access/list";
    }

    @GetMapping("/adm/url-access/form")
    public String form(@RequestParam(required = false) String urlAccessId, Model model) {
        UrlAccessRule rule = urlAccessId == null ? newRule() : urlAccessService.getAdm(urlAccessId);
        if (rule == null) {
            return LIST;
        }
        model.addAttribute("rule", rule);
        addOptions(model);
        return "adm/url-access/form";
    }

    @PostMapping("/adm/url-access/save")
    public String save(@ModelAttribute("rule") UrlAccessRule rule, Model model,
            RedirectAttributes redirect) {
        try {
            urlAccessService.saveAdm(rule);
        } catch (IllegalArgumentException e) {
            model.addAttribute("flashError", e.getMessage());
            addOptions(model);
            return "adm/url-access/form";
        }
        redirect.addFlashAttribute("flashOk", "저장했습니다. 다음 요청부터 새 규칙으로 판정합니다.");
        return LIST;
    }

    @PostMapping("/adm/url-access/delete")
    public String delete(@RequestParam String urlAccessId, RedirectAttributes redirect) {
        try {
            urlAccessService.deleteAdm(urlAccessId);
            redirect.addFlashAttribute("flashOk", "삭제했습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return LIST;
    }

    /** 캐시만 비우기 — DB 를 직접 고쳤을 때 즉시 반영시키는 탈출구. */
    @PostMapping("/adm/url-access/evict")
    public String evict(RedirectAttributes redirect) {
        urlAccessService.evictCache();
        redirect.addFlashAttribute("flashOk", "규칙 캐시를 비웠습니다. 다음 요청부터 DB 값을 다시 읽습니다.");
        return LIST;
    }

    private UrlAccessRule newRule() {
        UrlAccessRule rule = new UrlAccessRule();
        rule.setHttpMethod("ALL");
        rule.setAccessType("AUTHENTICATED");
        rule.setPriority(100);
        rule.setRequireCsrfYn("Y");
        rule.setRequire2faYn("N");
        rule.setUseYn("Y");
        return rule;
    }

    private void addOptions(Model model) {
        model.addAttribute("accessTypes", UrlAccessServiceImpl.ACCESS_TYPES);
        model.addAttribute("httpMethods", UrlAccessServiceImpl.HTTP_METHODS);
        model.addAttribute("roles", roleService.getAllForSelect());
        model.addAttribute("sites", siteService.getAllForSelect());
    }
}
