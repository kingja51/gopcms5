package com.gonet.config.web;

import com.gonet.common.web.ClientIpResolver;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.common.web.RequestAttrs;
import com.gonet.common.web.SiteContextHolder;
import com.gonet.logging.access.dto.AccessLog;
import com.gonet.logging.access.service.AccessLogService;
import com.gonet.primary.site.dto.SiteContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 접근 로그 필터 — 모든 HTTP 요청을 log_access 에 적재 (정적 리소스·actuator 제외).
 *
 * <p>설계 원칙:
 * <ul>
 *   <li><b>최외곽</b>(SiteResolveFilter 바깥) — 응답 완료 후 상태코드·소요시간까지 확보.
 *       site 정보는 ThreadLocal 이 아닌 request attribute 로 회수(내부 필터의 finally
 *       clear 이후에도 유효)</li>
 *   <li><b>실패 격리</b> — 적재 예외는 삼키고 WARN 만 남긴다(로그가 요청을 깨지 않음).
 *       트랜잭션도 logging TxManager + REQUIRES_NEW (AccessLogServiceImpl)</li>
 *   <li><b>마스킹</b> — 쿼리스트링의 password/token 류 값은 *** 치환 후 저장</li>
 *   <li><b>actor_*</b> — 체인 안쪽 {@code ActorCaptureFilter} 가 담아 둔 주체를 회수
 *       (여기선 SecurityContext 가 이미 비워진 뒤라 직접 읽을 수 없다)</li>
 *   <li><b>client_ip</b> — {@code ClientIpResolver} 단일 경로(신뢰 프록시 XFF)</li>
 * </ul>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // SiteResolveFilter(+20)보다 바깥
@RequiredArgsConstructor
public class AccessLogFilter extends OncePerRequestFilter {

    /** 적재 제외 — 정적 자원·인프라 엔드포인트 (테이블 주석 계약: "정적 리소스 제외") */
    private static final Set<String> SKIP_PREFIXES = Set.of(
            "css", "js", "fonts", "images", "tmpl", "webjars", "favicon.ico", "actuator");

    private final AccessLogService accessLogService;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int start = 1;
        int end = uri.indexOf('/', start);
        String first = end < 0 ? uri.substring(start) : uri.substring(start, end);
        return SKIP_PREFIXES.contains(first);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                accessLogService.write(build(request, response,
                        (int) (System.currentTimeMillis() - startedAt)));
            } catch (Exception e) {
                log.warn("접근 로그 적재 실패 — 요청은 정상 처리됨: {}", e.getMessage());
            }
        }
    }

    private AccessLog build(HttpServletRequest request, HttpServletResponse response,
            int elapsedMs) {
        // 내부 필터 finally 이후에도 유효한 request attribute 경유 (ThreadLocal 아님)
        SiteContext site = (SiteContext) request.getAttribute(SiteContextHolder.REQUEST_ATTR);
        HttpSession session = request.getSession(false);
        String sessionId = session == null ? null : tail(session.getId(), 8);
        String contentLength = response.getHeader("Content-Length");

        // 인증 주체 — 시큐리티 체인이 이미 반환된 시점이라 SecurityContext 는 비어 있다.
        // 그래서 체인 안쪽에서 담아 둔 request attribute 로 회수한다(ThreadLocal 아님).
        LoginPrincipal actor = (LoginPrincipal) request.getAttribute(RequestAttrs.ACTOR);

        return AccessLog.builder()
                .siteId(site == null ? null : site.getSiteId())
                .siteCode(site == null ? null : site.getSiteCode())
                .menuId((String) request.getAttribute(RequestAttrs.MENU_ID))
                .actorUserId(actor == null ? null : actor.userId())
                .actorUserType(actor == null ? "ANONYMOUS" : actor.userType())
                .actorLoginId(actor == null ? null : cut(actor.loginId(), 50))
                .requestUri(cut(request.getRequestURI(), 500))
                .httpMethod(request.getMethod())
                .queryString(cut(mask(request.getQueryString()), 500))
                .referer(cut(request.getHeader("Referer"), 500))
                .clientIp(clientIpResolver.resolve(request))
                .userAgent(cut(request.getHeader("User-Agent"), 500))
                .statusCode(response.getStatus())
                .responseMs(elapsedMs)
                .bytesSent(contentLength == null ? null : parseLongOrNull(contentLength))
                .sessionId(sessionId)
                .traceId(MDC.get("traceId"))
                .build();
    }

    /** 민감 파라미터 값 마스킹 — password/passwd/pwd/token/secret/otp */
    private String mask(String queryString) {
        if (queryString == null) {
            return null;
        }
        return queryString.replaceAll(
                "(?i)(password|passwd|pwd|token|secret|otp)=[^&]*", "$1=***");
    }

    private String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private String tail(String value, int length) {
        return value == null || value.length() <= length
                ? value : value.substring(value.length() - length);
    }

    private Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
