package com.gonet.config.security;

import com.gonet.common.web.LoginPrincipal;
import com.gonet.common.web.RequestAttrs;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 인증 주체를 request attribute 로 옮겨 담는다 — 접근 로그(actor_*)의 유일한 공급원.
 *
 * <p>{@code AccessLogFilter} 는 상태코드·소요시간을 재려고 시큐리티 체인 <b>바깥</b>에 있고,
 * 그 시점엔 {@code SecurityContextHolderFilter} 가 이미 컨텍스트를 비운 뒤다.
 * 이 필터는 체인 <b>안쪽</b>(SecurityContextHolderFilter 다음)에 놓여 자신의 finally 에서
 * 주체를 읽으므로, 이번 요청에서 막 로그인한 경우까지 포함해 확보된다.
 *
 * <p><b>빈으로 등록하지 않는다</b> — Filter 빈은 Boot 가 서블릿 체인 최외곽에도 자동 등록하는데,
 * {@code OncePerRequestFilter} 는 한 요청에 한 번만 돌므로 바깥 인스턴스가 선점하면
 * 컨텍스트가 비워진 뒤 실행돼 actor 가 항상 null 이 된다. SecurityConfig 가 직접 생성한다.
 */
public class ActorCaptureFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                    && authentication.getPrincipal() instanceof LoginPrincipal principal) {
                request.setAttribute(RequestAttrs.ACTOR, principal);
            }
        }
    }
}
