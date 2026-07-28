package com.gonet.config.security;

import com.gonet.common.web.LoginPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

/**
 * P6 Security — 다중 FilterChain (로그인 계약 2026-07-28):
 *
 * <pre>
 *  ① adm 체인 (/adm/**)  : 폼 /adm/login — ROLE_ADMIN(계층 반영) 필요.
 *                          폼 노출은 IP 게이트(LoginAdmController), 인증은 (admin_id, ip) 매칭.
 *  ② default 체인        : 사용자 폼 /login?siteCode= — 성공 시 /{siteCode}/index.
 *                          front 는 permitAll (회원 전용 영역·RBAC 는 P6 후속 증분).
 * </pre>
 *
 * 후속 증분(PLAN P6): DB RBAC(tb_role_url_access + DynamicAuthorizationManager, 무매칭 DENY) ·
 * 2FA(TOTP) · CSP nonce · 세션 정책(GOPCMS_SID·maximumSessions(1)) · X-Forwarded-For.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** ROLE_ADMIN > ROLE_MANAGER > ROLE_STAFF > ROLE_MEMBER > ROLE_REAL (tb_role 계층과 동기) */
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
            AdminAuthenticationProvider adminProvider, RoleHierarchy roleHierarchy)
            throws Exception {
        AuthorityAuthorizationManager<RequestAuthorizationContext> hasAdmin =
                AuthorityAuthorizationManager.hasRole("ADMIN");
        hasAdmin.setRoleHierarchy(roleHierarchy);

        http.securityMatcher("/adm/**")
                .authenticationProvider(adminProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/adm/login").permitAll()
                        .anyRequest().access(hasAdmin))
                .formLogin(form -> form
                        .loginPage("/adm/login")
                        .loginProcessingUrl("/adm/login")
                        .defaultSuccessUrl("/adm/index", true)
                        .failureUrl("/adm/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/adm/logout")
                        .logoutSuccessUrl("/adm/login?logout"));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
            MemberAuthenticationProvider memberProvider) throws Exception {
        http.authenticationProvider(memberProvider)
                // front 전체 permitAll — 회원 전용 영역은 RBAC 증분(tb_role_url_access)에서 조임
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
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
                            // 실패 시 siteCode 유지 — 같은 사이트 로그인 폼으로 복귀
                            String siteCode = request.getParameter("siteCode");
                            String suffix = siteCode == null || siteCode.isBlank()
                                    ? "" : "&siteCode=" + siteCode;
                            new SimpleUrlAuthenticationFailureHandler("/login?error" + suffix)
                                    .onAuthenticationFailure(request, response, exception);
                        }))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/"));
        return http.build();
    }
}
