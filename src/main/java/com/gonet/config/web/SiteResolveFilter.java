package com.gonet.config.web;


import com.gonet.common.web.ClientIpResolver;
import com.gonet.common.web.UrlNamespaces;
import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.site.dto.SiteContext;
import com.gonet.primary.site.service.SiteService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 사이트 해석 필터 — 경로 첫 세그먼트({@code /{siteCode}/**}) → SiteContext 바인딩.
 *
 * <p>해석 순서: 첫 세그먼트 site 조회(Caffeine) → 실패 시 기본 사이트(default_yn='Y')
 * 폴백 → 그마저 없으면 컨텍스트 없이 진행(뷰 해석은 layout-001/_default 폴백).
 * 도메인(Host) 기반 판별은 P7 사이트관리에서 보강 — canonical URL 은 항상 경로
 * (conventions.md §5 "siteCode 는 경로 유지").
 *
 * <p>감사 주체(IP)도 여기서 세팅 — userId 는 P6(Security) 에서 인증 주체 연결.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20) // AccessLogFilter(+10) 안쪽
@RequiredArgsConstructor
public class SiteResolveFilter extends OncePerRequestFilter {

    private final SiteService siteService;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 목록은 UrlNamespaces 단일 원천 — 여기에 따로 두면 예약어·slug 목록과 어긋난다
        return UrlNamespaces.isSkip(UrlNamespaces.segment(request.getRequestURI(), 1));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            SiteContext context = resolve(request);
            if (context != null) {
                SiteContextHolder.set(context);
                request.setAttribute(SiteContextHolder.REQUEST_ATTR, context);
            }
            // 감사 컨텍스트(client_ip)는 AuditorContextFilter 담당 — 이 필터는 /adm/** 등을
            // 건너뛰므로 여기서 세우면 관리자 쓰기의 updated_ip 가 비어버린다.
            filterChain.doFilter(request, response);
        } finally {
            SiteContextHolder.clear();
        }
    }

    /**
     * 해석 순서: ① 경로의 사이트코드 자리 → ② siteCode 쿼리 파라미터(/login?siteCode= 등)
     * → ③ 기본 사이트.
     *
     * <p>①의 <b>자리가 경로마다 다르다</b>. 컨텐츠는 첫 세그먼트({@code /{siteCode}/{slug}}),
     * 프로그램은 두 번째({@code /bbs/{siteCode}/{bbsCode}}) — conventions §5.1.
     */
    private SiteContext resolve(HttpServletRequest request) {
        SiteContext context = resolveByCode(
                UrlNamespaces.siteCodeSegment(request.getRequestURI()));
        if (context == null) {
            context = resolveByCode(request.getParameter("siteCode"));
        }
        return context != null ? context : siteService.getDefaultSiteContext();
    }

    private SiteContext resolveByCode(String siteCode) {
        return siteCode == null || siteCode.isBlank() ? null : siteService.getSiteContext(siteCode);
    }
}
