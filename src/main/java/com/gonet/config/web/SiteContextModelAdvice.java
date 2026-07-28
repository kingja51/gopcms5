package com.gonet.config.web;

import com.gonet.common.web.RequestAttrs;
import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.site.dto.SiteContext;
import com.gonet.primary.site.service.SiteServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * front 공통 모델 주입 — 모든 컨트롤러 렌더에서 사이트 3축을 뷰가 쓸 수 있게 한다.
 *
 * <ul>
 *   <li>{@code site}       — SiteContext (미해석 시 null — 뷰는 null-safe 로 작성)</li>
 *   <li>{@code siteLayout} — {@code layouts/{layoutCode}/layout} —
 *       페이지 첫 줄 {@code layout:decorate="~{${siteLayout}}"} 의 대상</li>
 *   <li>{@code themeClass} — {@code <html th:classappend>} 용 ('' = 기본 브랜드)</li>
 *   <li>{@code currentUri} — GNB 활성 표시 prefix 매칭용</li>
 * </ul>
 */
@ControllerAdvice
public class SiteContextModelAdvice {

    @ModelAttribute("site")
    public SiteContext site() {
        return SiteContextHolder.get();
    }

    @ModelAttribute("siteLayout")
    public String siteLayout() {
        SiteContext context = SiteContextHolder.get();
        String layoutCode = context != null
                ? context.getLayoutCode() : SiteServiceImpl.FALLBACK_LAYOUT;
        return "layouts/" + layoutCode + "/layout";
    }

    @ModelAttribute("menuTree")
    public Object menuTree() {
        SiteContext context = SiteContextHolder.get();
        return context != null ? context.getMenuTree() : null;
    }

    @ModelAttribute("themeClass")
    public String themeClass() {
        SiteContext context = SiteContextHolder.get();
        return context != null ? context.getThemeClass() : "";
    }

    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    /** CSP nonce — 인라인 스크립트는 {@code th:attr="nonce=${cspNonce}"} 없이는 실행되지 않는다. */
    @ModelAttribute("cspNonce")
    public String cspNonce(HttpServletRequest request) {
        return (String) request.getAttribute(RequestAttrs.CSP_NONCE);
    }
}
