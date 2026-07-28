package com.gonet.config.security;

import com.gonet.common.web.LoginPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 2FA 등록 강제 — 그룹 정책이 2FA 필수인데 아직 등록하지 않은 관리자를 등록 화면에 묶어 둔다.
 *
 * <p>로그인 자체를 막지 않는 이유: 등록하려면 로그인 상태여야 한다(시크릿을 계정에 붙여야 하므로).
 * 대신 등록 화면·로그아웃 외의 모든 {@code /adm/**} 요청을 등록 화면으로 되돌려,
 * 미등록 상태로 관리 기능에 접근하는 창을 없앤다.
 *
 * <p>{@link ActorCaptureFilter} 와 같은 이유로 <b>빈으로 등록하지 않는다</b>
 * (Boot 가 서블릿 체인 최외곽에 자동 등록하면 인증 전에 돌아 무의미해진다).
 */
public class TwoFactorEnrollmentFilter extends OncePerRequestFilter {

    static final String SETUP_URL = "/adm/2fa/setup";

    /** 미등록 상태에서도 열려야 하는 경로 — 등록 화면과 탈출구(로그아웃). */
    private static final Set<String> ALLOWED = Set.of(SETUP_URL, "/adm/logout");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean pending = authentication != null
                && authentication.getPrincipal() instanceof LoginPrincipal principal
                && principal.twoFactorPending();

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (pending && !ALLOWED.contains(path)) {
            response.sendRedirect(request.getContextPath() + SETUP_URL);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
