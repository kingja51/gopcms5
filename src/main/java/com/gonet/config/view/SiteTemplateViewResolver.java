package com.gonet.config.view;

import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.site.dto.SiteContext;
import com.gonet.primary.site.service.SiteServiceImpl;
import java.util.Locale;
import org.springframework.web.servlet.View;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

/**
 * ★ 템플릿 해석 지점 (template-resolver-design.md §2) ★
 *
 * <p>{@code front/**} 논리 뷰명을 3단 폴백으로 물리 경로 재작성한다:
 * <pre>
 *   front/index → sites/{siteCode}/index          (① 사이트 전용 페이지 — 랜딩 등 커스텀 HTML)
 *               → layouts/{layoutCode}/index      (② 레이아웃 오버라이드)
 *               → layouts/_default/index          (③ 공용 1벌)
 * </pre>
 * {@code adm/**}·redirect:·forward: 는 재작성 없이 통과. 컨트롤러는 템플릿을 모른다.
 * 사이트 전용 층(①)은 "각 사이트 랜딩은 다르게" 요구(2026-07-28)의 구현 지점 —
 * 파일은 통상 {@code layout:decorate="~{${siteLayout}}"} 로 사이트 레이아웃 안에서 렌더.
 *
 * <p>주의: 뷰 캐시는 반드시 꺼야 한다(ThymeleafViewConfig 에서 setCache(false)) —
 * AbstractCachingViewResolver 가 "front/board/list" 원본 이름으로 View 를 캐시하면
 * 첫 사이트의 레이아웃이 전 사이트에 고정된다. Thymeleaf 템플릿 캐시(물리명 키)가
 * 성능을 담당하므로 뷰 캐시 off 비용은 미미하다.
 */
public class SiteTemplateViewResolver extends ThymeleafViewResolver {

    private static final String FRONT_PREFIX = "front/";
    private static final String LAYOUTS = "layouts/";
    private static final String DEFAULT_DIR = "layouts/_default/";

    private final ViewTemplateLookup lookup;

    public SiteTemplateViewResolver(ViewTemplateLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    protected View createView(String viewName, Locale locale) throws Exception {
        if (!viewName.startsWith(FRONT_PREFIX)) {
            return super.createView(viewName, locale);
        }
        SiteContext context = SiteContextHolder.get();
        String layoutCode = context != null
                ? context.getLayoutCode() : SiteServiceImpl.FALLBACK_LAYOUT;
        String rest = viewName.substring(FRONT_PREFIX.length());

        // ① 사이트 전용
        if (context != null) {
            String siteView = "sites/" + context.getSiteCode() + "/" + rest;
            if (lookup.exists(siteView)) {
                return super.createView(siteView, locale);
            }
        }
        // ② 레이아웃 오버라이드 → ③ 공용
        String candidate = LAYOUTS + layoutCode + "/" + rest;
        String physical = lookup.exists(candidate) ? candidate : DEFAULT_DIR + rest;
        return super.createView(physical, locale);
    }
}
