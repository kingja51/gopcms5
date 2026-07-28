package com.gonet.primary.site.controller;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.layout.service.LayoutService;
import com.gonet.primary.site.dto.SiteAdmDto;
import com.gonet.primary.site.service.SiteService;
import com.gonet.primary.template.service.TemplateService;
import com.gonet.primary.theme.service.ThemeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 관리자 대시보드 (/adm/index) — 지금 이 CMS 가 무엇을 담고 있는지 한눈에.
 *
 * <p>집계는 <b>사이트 목록 한 번</b>으로 끝낸다. 목록 쿼리가 사이트별 메뉴·컨텐츠 수를
 * 이미 서브쿼리로 세고 있어서(SiteMapper.findPage), 여기서 합만 내면 된다 —
 * 카운트 전용 쿼리를 새로 만들 이유가 없다.
 */
@Controller
@RequiredArgsConstructor
public class DashboardAdmController {

    /** 대시보드 사이트 표에 보여줄 최대 행 — 그 이상은 사이트 관리로 넘긴다. */
    private static final int SITE_PREVIEW = 8;

    private final SiteService siteService;
    private final TemplateService templateService;
    private final ThemeService themeService;
    private final LayoutService layoutService;

    @GetMapping("/adm/index")
    public String index(Model model) {
        PageRequest cond = new PageRequest();
        cond.setSize(SITE_PREVIEW);
        var sites = siteService.getAdmPage(cond);
        List<SiteAdmDto> rows = sites.content();

        model.addAttribute("siteCount", sites.total());
        model.addAttribute("menuCount", rows.stream().mapToInt(SiteAdmDto::getMenuCount).sum());
        model.addAttribute("contentCount",
                rows.stream().mapToInt(SiteAdmDto::getContentCount).sum());
        model.addAttribute("templateCount", templateService.getAdmPage(new PageRequest()).total());
        model.addAttribute("themeCount", themeService.getAdmPage(new PageRequest()).total());
        model.addAttribute("layoutCount", layoutService.getAdmPage(new PageRequest()).total());
        model.addAttribute("sites", rows);
        return "adm/index";
    }
}
