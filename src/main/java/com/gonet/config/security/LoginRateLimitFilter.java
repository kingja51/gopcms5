package com.gonet.config.security;

import com.gonet.common.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인 시도 레이트리밋 — 인증 체인 <b>앞</b>에서 끊는다.
 *
 * <p>Provider 안에서 세면 이미 DB 조회를 한 뒤다. 무차별 대입은 요청 수 자체가 부하이므로
 * 그 전에 막아야 의미가 있다.
 *
 * <p>POST 로그인 요청만 본다 — 폼을 보는 GET 까지 세면 새로고침만으로 막힌다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 7)   // SecurityHeadersFilter(+5) 다음, AccessLog(+10) 앞
@RequiredArgsConstructor
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    /** 관리자·회원 로그인 진입점. 경로가 늘면 여기에 함께 등록한다. */
    private static final Set<String> LOGIN_PATHS = Set.of("/login", "/adm/login");

    private final LoginRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !LOGIN_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String clientIp = clientIpResolver.resolve(request);
        String loginId = request.getParameter("username");

        if (rateLimiter.tryConsume(clientIp, loginId)) {
            chain.doFilter(request, response);
            return;
        }

        // 로그인 화면으로 되돌리되 사유는 일반 문구로 — 어느 축에 걸렸는지 알려 주면
        // 공격자가 그 축을 피해 조율한다
        log.warn("로그인 시도 제한 초과 ip={} loginId={} uri={}",
                clientIp, loginId, request.getRequestURI());
        response.sendRedirect(request.getRequestURI() + "?error&throttled");
    }
}
