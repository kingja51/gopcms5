package com.gonet.config.security;

import com.gonet.common.web.LoginPrincipal;
import com.gonet.primary.auth.dto.LoginUser;
import com.gonet.primary.auth.service.AuthService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 관리자 인증 (/adm/login) — vw_user_login(user_type=ADMIN) 기반.
 *
 * <p>정책(2026-07-28): <b>(admin_id, ip_address) 쌍이 tb_admin_allow_ip 에 존재해야
 * 로그인 가능</b> — 등록된 IP 가 아니면 자격이 맞아도 거부(메시지는 일반화 — 정보 노출 방지).
 * 폼 노출 게이트는 LoginAdmController 가 담당(미등록 IP → "/" 리다이렉트).
 * 실패 5회 = 30분 잠금, 성공 시 카운트 리셋 (AuthMapper).
 * 2FA(two_factor_enabled_yn)·시간대·CIDR/RANGE IP 는 P6 후속 증분.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthenticationProvider implements AuthenticationProvider {

    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        String loginId = authentication.getName();
        String rawPassword = String.valueOf(authentication.getCredentials());
        String clientIp = currentClientIp();

        LoginUser user = authService.findLoginUser("ADMIN", null, loginId);
        if (user == null) {
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        // IP 화이트리스트 — 관리자별 (admin_id, ip) 매칭 필수
        if (!authService.isIpAllowedForAdmin(user.getUserId(), clientIp)) {
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new LockedException("계정이 잠겨 있습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (!"ACTIVE".equals(user.getStatus()) && !"LOCKED".equals(user.getStatus())) {
            throw new DisabledException("사용할 수 없는 계정입니다.");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            authService.loginFailed("ADMIN", user.getUserId());
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        authService.loginSucceeded("ADMIN", user.getUserId(), clientIp);
        List<GrantedAuthority> authorities = toAuthorities(user.getRoleCodes());
        LoginPrincipal principal = new LoginPrincipal("ADMIN", user.getUserId(),
                user.getLoginId(), user.getDisplayName(), null, null);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    static List<GrantedAuthority> toAuthorities(String roleCodesCsv) {
        if (roleCodesCsv == null || roleCodesCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roleCodesCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }

    static String currentClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest().getRemoteAddr(); // X-Forwarded-For 신뢰 프록시 해석은 P6 후속
    }
}
