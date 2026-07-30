package com.gonet.config.security;

import com.gonet.common.web.LoginPrincipal;
import com.gonet.common.web.UrlNamespaces;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * P6 Security — 다중 FilterChain (로그인 계약 2026-07-28):
 *
 * <pre>
 *  ① adm 체인 (/adm/**)  : 폼 /adm/login — 폼 노출은 IP 게이트(LoginAdmController),
 *                          인증은 (admin_id, ip) 매칭.
 *  ② default 체인        : 사용자 폼 /login?siteCode= — 성공 시 /{siteCode}/index.
 * </pre>
 *
 * <p><b>인가는 두 체인 모두 DB 단일 원천</b>({@link DynamicAuthorizationManager} ←
 * tb_role_url_access, 무매칭 DENY) — 코드에 URL 별 허용을 흩뿌리지 않는다(P6-2).
 * 규칙 등록 절차는 doc/conventions.md §7, 규칙 0건 기동은 RbacSmokeRunner 가 중단시킨다.
 *
 * <p>후속 증분(PLAN P6-3): 2FA(TOTP) · CSP nonce · 세션 정책(GOPCMS_SID·maximumSessions(1)) ·
 * X-Forwarded-For 신뢰 프록시 · 허용 IP CIDR/RANGE.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 세션 쿠키명 — application.yml 의 {@code server.servlet.session.cookie.name} 과 동일해야 한다. */
    public static final String SESSION_COOKIE = "GOPCMS_SID";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * ROLE_ADMIN &gt; ROLE_MANAGER &gt; ROLE_STAFF &gt; ROLE_MEMBER &gt; ROLE_REAL
     * (tb_role 계층과 동기 — 계층 변경 시 이 문자열도 함께 갱신).
     *
     * <p>URL 인가에는 쓰이지 않는다(DB 규칙은 전개된 role_ids 교집합으로 판정) —
     * 뷰의 {@code sec:authorize="hasRole(…)"}·메서드 보안 같은 표현식 평가용이다.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_MANAGER
                ROLE_MANAGER > ROLE_STAFF
                ROLE_STAFF > ROLE_MEMBER
                ROLE_MEMBER > ROLE_REAL
                """);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain admSecurityFilterChain(HttpSecurity http,
            AdminAuthenticationProvider adminProvider,
            DynamicAuthorizationManager authorizationManager) throws Exception {
        http.securityMatcher("/adm/**")
                .authenticationProvider(adminProvider)
                // /adm/login 허용도 DB 규칙(priority 10) — 코드에 예외를 두지 않는다
                .authorizeHttpRequests(auth -> auth.anyRequest().access(authorizationManager))
                .addFilterAfter(new ActorCaptureFilter(), SecurityContextHolderFilter.class)
                // 인가 통과 후에 걸어야 한다 — 미등록 관리자를 등록 화면 밖으로 못 나가게
                .addFilterAfter(new TwoFactorEnrollmentFilter(), AuthorizationFilter.class)
                .sessionManagement(session -> sessionPolicy(session, "/adm/login?expired"))
                .formLogin(form -> form
                        .loginPage("/adm/login")
                        .loginProcessingUrl("/adm/login")
                        .defaultSuccessUrl("/adm/index", true)
                        .failureUrl("/adm/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/adm/logout")
                        .logoutSuccessUrl("/adm/login?logout")
                        .deleteCookies(SESSION_COOKIE));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
            MemberAuthenticationProvider memberProvider,
            DynamicAuthorizationManager authorizationManager,
            SiteAwareAuthenticationEntryPoint entryPoint) throws Exception {
        http.authenticationProvider(memberProvider)
                // front 공개 여부까지 DB 규칙이 결정 — 회원 전용 영역(/{sc}/member/**)은 규칙으로 조임
                .authorizeHttpRequests(auth -> auth.anyRequest().access(authorizationManager))
                // NICE 콜백은 외부 도메인(nice.checkplus.co.kr)이 POST 한다 — 우리 폼이
                // 아니므로 CSRF 토큰이 있을 수 없다. 대신 세션에 둔 요청번호(REQ_SEQ)와
                // 응답의 REQ_SEQ 를 대조해 남이 만든 EncodeData 를 막는다.
                // 예외는 이 두 경로로 끝난다 — 넓히면 인증 흐름 전체가 열린다.
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/member/identity/nice/success", "/member/identity/nice/fail"))
                // 미인증 차단 → 원래 사이트의 로그인 폼(/login?siteCode=)으로 유도
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .addFilterAfter(new ActorCaptureFilter(), SecurityContextHolderFilter.class)
                .sessionManagement(session -> sessionPolicy(session, "/login?expired"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler((request, response, authentication) -> {
                            // 회원 로그인 성공 → 소속 사이트 랜딩 (/{siteCode}/index)
                            String target = "/";
                            if (authentication.getPrincipal() instanceof LoginPrincipal principal
                                    && principal.siteCode() != null) {
                                target = "/" + principal.siteCode() + "/index";
                            }
                            response.sendRedirect(target);
                        })
                        .failureHandler((request, response, exception) -> {
                            // 실패 시 siteCode 유지 — 같은 사이트 로그인 폼으로 복귀.
                            // 값은 요청 파라미터라 그대로 이어 붙이지 않는다: 형식을 통과한
                            // 것만 쓰고(UrlNamespaces = site_code 규칙 단일 원천), 아니면
                            // 통째로 버린다. 검증 없이 붙이면 &·# 로 쿼리를 갈라 뒤에 임의
                            // 파라미터를 얹거나 로그를 오염시킬 수 있다.
                            String siteCode = request.getParameter("siteCode");
                            String suffix = UrlNamespaces.isValidSiteCode(siteCode)
                                    ? "&siteCode=" + siteCode : "";
                            new SimpleUrlAuthenticationFailureHandler("/login?error" + suffix)
                                    .onAuthenticationFailure(request, response, exception);
                        }))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .deleteCookies(SESSION_COOKIE));
        return http.build();
    }

    /**
     * 세션 정책 (두 체인 공통) — 세션 고정 공격 차단 + 계정 공유 억제.
     *
     * <ul>
     *   <li>{@code changeSessionId()}: 로그인 성공 시 세션 ID 재발급(속성은 유지) —
     *       로그인 전 심어 둔 세션 ID 로 로그인 후 세션을 타는 고정 공격을 끊는다</li>
     *   <li>{@code maximumSessions(1)}: 동시 접속 1개. 새 로그인이 이전 세션을 만료시킨다
     *       ({@code maxSessionsPreventsLogin(false)}) — 자리를 뺏긴 쪽은 만료 URL 로 안내</li>
     * </ul>
     */
    private void sessionPolicy(SessionManagementConfigurer<HttpSecurity> session,
            String expiredUrl) {
        session.sessionFixation(fixation -> fixation.changeSessionId())
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
                .expiredUrl(expiredUrl);
    }

    /**
     * 동시 세션 제어에 필요한 세션 레지스트리 — 세션 소멸 이벤트를 받아야 만료 처리가 산다.
     * (서블릿 컨테이너 → Spring 이벤트 브리지)
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
