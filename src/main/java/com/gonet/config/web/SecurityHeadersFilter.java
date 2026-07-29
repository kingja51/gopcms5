package com.gonet.config.web;

import com.gonet.common.web.RequestAttrs;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 보안 응답 헤더 + CSP nonce 발급 — 요청당 1회 난수 nonce 를 만들어 헤더와 뷰가 공유한다.
 *
 * <p>CSP 를 Spring Security 의 정적 문자열 설정 대신 필터로 두는 이유:
 * <ul>
 *   <li><b>nonce 는 요청마다 달라야</b> 의미가 있다 — 정적 정책 문자열로는 불가</li>
 *   <li>정적 자원 응답까지 포함해 <b>모든 응답</b>에 헤더를 붙일 수 있다(시큐리티 체인 바깥)</li>
 * </ul>
 *
 * <p>정책은 <b>self + nonce</b> 다 — CDN 금지·self-host 규약(CLAUDE.md)과 1:1 이라
 * 외부 출처를 허용할 자리가 없다. 인라인 {@code <script>} 는 nonce 를 달아야만 실행된다
 * (nonce 없는 인라인이 남아 있으면 콘솔 CSP 위반으로 즉시 드러난다).
 *
 * <p>{@code report-only} 로 먼저 관찰하려면 {@code gopcms.security.csp-report-only=true}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5) // AccessLogFilter(+10)보다 바깥 — 전 응답에 헤더 보장
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /**
     * frame-ancestors 'none' = 클릭재킹 차단(X-Frame-Options 의 현행 표준 대체),
     * object-src 'none' = 레거시 플러그인 차단, base-uri 'self' = base 태그 주입 차단.
     * style-src 에 'unsafe-inline' 을 남긴 이유: Tailwind 유틸이 아닌 서드파티 위젯이
     * style 속성을 쓰는 경우가 있어 P7 관리자 화면까지 관찰 후 조인다.
     */
    private static final String CSP_TEMPLATE = String.join("; ",
            "default-src 'self'",
            // 'wasm-unsafe-eval' — HWP 뷰어(rhwp)가 WebAssembly 를 컴파일하려면 필요하다.
            // eval()·new Function() 을 여는 'unsafe-eval' 과 다르다: WASM 컴파일만 허용하는
            // 좁은 토큰이라, 문서 파싱을 서버가 아니라 브라우저 샌드박스에 맡기는 대가로 감수한다.
            "script-src 'self' 'nonce-%s' 'wasm-unsafe-eval'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "frame-ancestors 'none'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'");

    @Value("${gopcms.security.csp-report-only:false}")
    private boolean reportOnly;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        byte[] seed = new byte[16];
        RANDOM.nextBytes(seed);
        String nonce = ENCODER.encodeToString(seed);
        request.setAttribute(RequestAttrs.CSP_NONCE, nonce);

        response.setHeader(reportOnly
                ? "Content-Security-Policy-Report-Only" : "Content-Security-Policy",
                CSP_TEMPLATE.formatted(nonce));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");

        filterChain.doFilter(request, response);
    }
}
