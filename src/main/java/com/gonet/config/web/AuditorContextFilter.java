package com.gonet.config.web;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 감사 주체(client_ip)를 요청 스레드에 심는다 — 감사컬럼 6종의 IP 공급원.
 *
 * <p>전에는 {@code SiteResolveFilter} 가 이 일을 겸했는데, 그 필터는 {@code /adm/**} ·
 * {@code /api/**} 를 {@code SKIP_PREFIXES} 로 건너뛴다. 그래서 <b>관리자 화면의 모든 쓰기에서
 * updated_ip 가 NULL 로 남았다</b>(실측 확인). 사이트 해석과 감사 컨텍스트는 적용 범위가
 * 다르므로 필터를 분리한다 — 감사는 예외 없이 전 요청에 필요하다.
 *
 * <p>userId 는 세팅하지 않는다. 이 필터는 시큐리티 체인 <b>바깥</b>이라 아직 인증 전이고,
 * {@link AuditorContext#currentUserId()} 가 조회 시점에 SecurityContext 에서 주체를 읽는다.
 *
 * <p>IP 는 {@link ClientIpResolver} 단일 경로로만 얻는다({@code getRemoteAddr()} 직접 호출 금지 규약).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 6)
@RequiredArgsConstructor
public class AuditorContextFilter extends OncePerRequestFilter {

    private final ClientIpResolver clientIpResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            AuditorContext.set(null, clientIpResolver.resolve(request));
            filterChain.doFilter(request, response);
        } finally {
            AuditorContext.clear();
        }
    }
}
