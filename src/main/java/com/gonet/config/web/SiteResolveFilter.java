package com.gonet.config.web;


import com.gonet.common.web.ClientIpResolver;
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

    /**
     * 사이트 네임스페이스가 아닌 첫 세그먼트 — 해석 자체를 건너뜀.
     *
     * <p>동시에 <b>site_code 로 쓸 수 없는 값</b>이기도 하다(같은 자리를 다투므로) —
     * 사이트 등록 검증(SiteServiceImpl)이 이 목록을 그대로 참조한다.
     */
    public static final Set<String> SKIP_PREFIXES = Set.of(
            "adm", "api", "actuator", "error", "favicon.ico",
            "css", "js", "fonts", "images", "tmpl", "webjars",
            "swagger-ui", "v3");

    private final SiteService siteService;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String first = firstSegment(request.getRequestURI());
        return first != null && SKIP_PREFIXES.contains(first);
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

    /** 해석 순서: ① 경로 첫 세그먼트 → ② siteCode 쿼리 파라미터(/login?siteCode= 등) → ③ 기본 사이트 */
    private SiteContext resolve(HttpServletRequest request) {
        SiteContext context = resolveByCode(firstSegment(request.getRequestURI()));
        if (context == null) {
            context = resolveByCode(request.getParameter("siteCode"));
        }
        return context != null ? context : siteService.getDefaultSiteContext();
    }

    private SiteContext resolveByCode(String siteCode) {
        return siteCode == null || siteCode.isBlank() ? null : siteService.getSiteContext(siteCode);
    }

    private String firstSegment(String uri) {
        if (uri == null || uri.length() < 2) {
            return null;
        }
        int start = 1;
        int end = uri.indexOf('/', start);
        String segment = end < 0 ? uri.substring(start) : uri.substring(start, end);
        return segment.isBlank() ? null : segment;
    }
}
