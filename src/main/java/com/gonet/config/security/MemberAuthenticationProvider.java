package com.gonet.config.security;

import com.gonet.common.web.LoginPrincipal;
import com.gonet.primary.auth.dto.LoginUser;
import com.gonet.primary.auth.service.AuthService;
import com.gonet.primary.site.dto.SiteContext;
import com.gonet.primary.site.service.SiteService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 회원 인증 (/login?siteCode=) — vw_user_login(user_type=MEMBER) + 사이트 스코프.
 *
 * <p>siteCode 요청 파라미터(폼 hidden)로 사이트를 해석해 (site_id, login_id) 로 조회 —
 * uk_member_login 과 1:1. siteCode 미지정 시 기본 사이트로 폴백.
 * EMAIL_PENDING 등 비활성 상태는 로그인 거부. 실패 5회 = 30분 잠금.
 */
@Component
@RequiredArgsConstructor
public class MemberAuthenticationProvider implements AuthenticationProvider {

    private final AuthService authService;
    private final SiteService siteService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        String loginId = authentication.getName();
        String rawPassword = String.valueOf(authentication.getCredentials());
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        String clientIp = attributes.getRequest().getRemoteAddr();
        String siteCode = attributes.getRequest().getParameter("siteCode");

        SiteContext site = siteCode == null || siteCode.isBlank()
                ? siteService.getDefaultSiteContext() : siteService.getSiteContext(siteCode);
        if (site == null) {
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        LoginUser user = authService.findLoginUser("MEMBER", site.getSiteId(), loginId);
        if (user == null) {
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new LockedException("계정이 잠겨 있습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (!"ACTIVE".equals(user.getStatus()) && !"LOCKED".equals(user.getStatus())) {
            throw new DisabledException("사용할 수 없는 계정입니다.");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            authService.loginFailed("MEMBER", user.getUserId());
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        authService.loginSucceeded("MEMBER", user.getUserId(), clientIp);
        LoginPrincipal principal = new LoginPrincipal("MEMBER", user.getUserId(),
                user.getLoginId(), user.getDisplayName(), site.getSiteId(), site.getSiteCode());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                AdminAuthenticationProvider.toAuthorities(user.getRoleCodes()));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
