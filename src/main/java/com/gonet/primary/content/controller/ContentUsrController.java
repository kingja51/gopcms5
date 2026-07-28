package com.gonet.primary.content.controller;

import com.gonet.common.web.RequestAttrs;
import com.gonet.common.web.SiteContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import com.gonet.primary.content.dto.ContentDto;
import com.gonet.primary.content.service.ContentService;
import com.gonet.primary.menu.service.MenuService;
import com.gonet.primary.site.dto.SiteContext;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * 컨텐츠 URL 계약 (conventions.md §5): /{siteCode}/index · /sitemap · /{slug}.
 *
 * <p>고정 라우트(index·sitemap)는 리터럴 패턴이라 {slug} 캐치올보다 우선 매칭된다
 * (Spring MVC 패턴 특이성 — 선언 순서는 가독용). 컨트롤러는 논리 뷰명(front/**)만
 * 반환 — 템플릿·레이아웃은 SiteTemplateViewResolver 몫.
 */
@Controller
@RequiredArgsConstructor
public class ContentUsrController {

    /** 예약 slug — 고정 라우트·상위 네임스페이스 충돌 차단 (conventions §5, 등록측 Validator 와 동일 목록) */
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "index", "sitemap", "search", "member", "bbs", "prg", "adm", "api");

    private final ContentService contentService;
    private final MenuService menuService;

    /** 루트 → 기본 사이트 랜딩 (SiteResolveFilter 가 default_yn 폴백을 이미 적용) */
    @GetMapping("/")
    public String root() {
        SiteContext site = SiteContextHolder.get();
        if (site == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "기본 사이트 미설정");
        }
        return "redirect:/" + site.getSiteCode() + "/index";
    }

    /** 랜딩 페이지 — 경로 변수는 conventions §5 패턴으로 제약(점(.) 포함 정적 자원 경로가
     *  캐치올에 삼켜지는 것 차단: /css/output.css 가 {siteCode}/{slug} 에 매칭되던 실측 이슈) */
    @GetMapping("/{siteCode:[a-z0-9-]{1,30}}/index")
    public String index(@PathVariable String siteCode, Model model) {
        SiteContext site = requireSite(siteCode);
        model.addAttribute("recentContents", contentService.getRecent(site.getSiteId(), 5));
        return "front/index";
    }

    /** 사이트맵 — 동일 menuTree 를 4뎁스 전개 (모델은 Advice 의 menuTree) */
    @GetMapping("/{siteCode:[a-z0-9-]{1,30}}/sitemap")
    public String sitemap(@PathVariable String siteCode) {
        requireSite(siteCode);
        return "front/sitemap";
    }

    /** 컨텐츠 페이지 캐치올 — 마지막 선언 유지. slug 패턴은 매핑 정규식이 강제 */
    @GetMapping("/{siteCode:[a-z0-9-]{1,30}}/{slug:[a-z0-9-]{1,200}}")
    public String content(@PathVariable String siteCode, @PathVariable String slug,
            Model model, HttpServletRequest request) {
        SiteContext site = requireSite(siteCode);
        if (RESERVED_SLUGS.contains(slug)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ContentDto content = contentService.viewBySlug(site.getSiteId(), slug);
        if (content == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        request.setAttribute(RequestAttrs.MENU_ID, content.getMenuId()); // 접근 로그 menu_id
        model.addAttribute("content", content);
        model.addAttribute("breadcrumb",
                menuService.findPathByMenuId(site.getMenuTree(), content.getMenuId()));
        return "front/content";
    }

    /**
     * 경로 siteCode 와 해석된 컨텍스트 일치 검증 — 미등록 siteCode 는 필터가 기본
     * 사이트로 폴백하므로, 경로 불일치는 404 로 확정한다(잘못된 URL 을 기본 사이트
     * 콘텐츠로 위장 응답하지 않음 — canonical 원칙).
     */
    private SiteContext requireSite(String siteCode) {
        SiteContext site = SiteContextHolder.get();
        if (site == null || !site.getSiteCode().equals(siteCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "미등록 사이트: " + siteCode);
        }
        return site;
    }
}
