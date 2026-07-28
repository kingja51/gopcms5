package com.gonet.config.security;

import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.site.dto.SiteContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 미인증 접근 → 사이트 로그인 폼({@code /login?siteCode=…}) 리다이렉트.
 *
 * <p>회원 로그인은 사이트 스코프라 siteCode 가 없으면 기본 사이트로 인증하게 된다.
 * 판정 시점에 SiteContextHolder 가 살아 있으므로(SiteResolveFilter 가 시큐리티 필터
 * 바깥) 원래 사이트를 그대로 물려준다. 원 요청 복귀는 Spring 의 RequestCache 몫.
 *
 * <p>{@code /api/**} 는 JSON 전용 네임스페이스(conventions §4)라 HTML 폼으로 보내지 않고
 * 401 로 끝낸다 — 클라이언트가 로그인 페이지 HTML 을 응답 본문으로 받는 계약을 막는다.
 */
@Component
public class SiteAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "인증이 필요합니다.");
            return;
        }
        SiteContext site = SiteContextHolder.get();
        String query = site == null ? "" : "?siteCode=" + site.getSiteCode();
        response.sendRedirect(request.getContextPath() + "/login" + query);
    }
}
