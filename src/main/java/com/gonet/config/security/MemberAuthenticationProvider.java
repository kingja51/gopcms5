package com.gonet.config.security;

import com.gonet.common.web.ClientIpResolver;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.primary.auth.dto.LoginHistory;
import com.gonet.primary.auth.dto.LoginUser;
import com.gonet.primary.auth.service.AuthService;
import com.gonet.primary.site.dto.SiteContext;
import com.gonet.primary.site.service.SiteService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
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

    /** 사용자에게 돌려주는 문구는 항상 이것 — 실패의 진짜 사유는 이력에만 남긴다. */
    private static final String GENERIC_FAILURE = "아이디 또는 비밀번호가 올바르지 않습니다.";

    private final AuthService authService;
    private final SiteService siteService;
    private final PasswordEncoder passwordEncoder;
    private final ClientIpResolver clientIpResolver;
    private final LoginHistoryRecorder historyRecorder;
    private final CaptchaService captchaService;
    private final LoginTiming loginTiming;

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        String loginId = authentication.getName();
        String rawPassword = String.valueOf(authentication.getCredentials());
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        String clientIp = clientIpResolver.resolve(attributes.getRequest());
        String siteCode = attributes.getRequest().getParameter("siteCode");

        SiteContext site = siteCode == null || siteCode.isBlank()
                ? siteService.getDefaultSiteContext() : siteService.getSiteContext(siteCode);
        if (site == null) {
            loginTiming.burn(rawPassword);
            historyRecorder.failure("MEMBER", null, loginId, null, siteCode,
                    LoginHistory.FAIL_NOT_FOUND, "미해석 siteCode: " + siteCode);
            throw new BadCredentialsException(GENERIC_FAILURE);
        }
        String siteId = site.getSiteId();

        LoginUser user = authService.findLoginUser("MEMBER", siteId, loginId);
        if (user == null) {
            // 있는 계정과 같은 시간을 쓴다 — 응답 속도로 계정 존재가 새어 나가지 않게
            loginTiming.burn(rawPassword);
            historyRecorder.failure("MEMBER", null, loginId, siteId, site.getSiteCode(),
                    LoginHistory.FAIL_NOT_FOUND, "사이트 내 존재하지 않는 회원 ID");
            throw new BadCredentialsException(GENERIC_FAILURE);
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            historyRecorder.failure("MEMBER", user.getUserId(), loginId, siteId,
                    site.getSiteCode(), LoginHistory.FAIL_LOCKED,
                    "잠금 해제 예정: " + user.getLockedUntil());
            throw new LockedException("계정이 잠겨 있습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (!"ACTIVE".equals(user.getStatus()) && !"LOCKED".equals(user.getStatus())) {
            historyRecorder.failure("MEMBER", user.getUserId(), loginId, siteId,
                    site.getSiteCode(), LoginHistory.FAIL_DISABLED, "상태: " + user.getStatus());
            throw new DisabledException("사용할 수 없는 계정입니다.");
        }
        // 잠금 이력이 있는 계정은 비밀번호 검사 전에 CAPTCHA (관리자와 동일 정책)
        if ("Y".equals(user.getCaptchaRequiredYn())) {
            captchaService.require(attributes.getRequest());
            if (!captchaService.verify(attributes.getRequest())) {
                historyRecorder.failure("MEMBER", user.getUserId(), loginId, siteId,
                        site.getSiteCode(), LoginHistory.FAIL_CAPTCHA, "자동입력 방지 문답 불일치");
                throw new BadCredentialsException("자동입력 방지 답이 올바르지 않습니다.");
            }
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            authService.loginFailed("MEMBER", user.getUserId());
            historyRecorder.failure("MEMBER", user.getUserId(), loginId, siteId,
                    site.getSiteCode(), LoginHistory.FAIL_PASSWORD,
                    "실패 누계: " + (user.getLoginFailCount() + 1));
            throw new BadCredentialsException(GENERIC_FAILURE);
        }
        if (user.getPasswordExpireAt() != null
                && user.getPasswordExpireAt().isBefore(LocalDateTime.now())) {
            historyRecorder.failure("MEMBER", user.getUserId(), loginId, siteId,
                    site.getSiteCode(), LoginHistory.FAIL_EXPIRED,
                    "만료일: " + user.getPasswordExpireAt());
            throw new CredentialsExpiredException(
                    "비밀번호 유효기간이 만료되었습니다. 비밀번호를 재설정해 주세요.");
        }

        authService.loginSucceeded("MEMBER", user.getUserId(), clientIp, null);
        captchaService.clear(attributes.getRequest().getSession(false));
        historyRecorder.success("MEMBER", user.getUserId(), loginId, siteId, site.getSiteCode());
        LoginPrincipal principal = new LoginPrincipal("MEMBER", user.getUserId(),
                user.getLoginId(), user.getDisplayName(), site.getSiteId(), site.getSiteCode(),
                user.getRoleIds(), false); // 회원 2FA 는 미도입
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                AdminAuthenticationProvider.toAuthorities(user.getRoleCodes()));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
