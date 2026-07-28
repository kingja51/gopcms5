package com.gonet.config.security;

import com.gonet.common.crypto.Aes256Gcm;
import com.gonet.common.web.ClientIpResolver;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.primary.auth.dto.AdminAllowIp;
import com.gonet.primary.auth.dto.LoginHistory;
import com.gonet.primary.auth.dto.LoginUser;
import com.gonet.primary.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
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
 * IP 는 SINGLE·CIDR·RANGE 를 지원하며(P6-3), 판정 대상 주소는 {@link ClientIpResolver}
 * 단일 경로로 해석한다(신뢰 프록시 밖의 X-Forwarded-For 는 무시).
 * 접속 허용 시간대(allowed_time_from/to)는 미구현.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthenticationProvider implements AuthenticationProvider {

    /** 사용자에게 돌려주는 문구는 항상 이것 — 실패의 진짜 사유는 이력에만 남긴다. */
    private static final String GENERIC_FAILURE = "아이디 또는 비밀번호가 올바르지 않습니다.";

    /** 로그인 폼의 OTP 입력 필드명 — 2FA 미사용 계정은 비워 둔다. */
    private static final String TOTP_PARAM = "totpCode";

    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final ClientIpResolver clientIpResolver;
    private final LoginHistoryRecorder historyRecorder;
    private final CaptchaService captchaService;
    private final TotpService totpService;
    private final Aes256Gcm aes256Gcm;

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        String loginId = authentication.getName();
        String rawPassword = String.valueOf(authentication.getCredentials());
        String clientIp = clientIpResolver.resolveCurrent();

        LoginUser user = authService.findLoginUser("ADMIN", null, loginId);
        if (user == null) {
            historyRecorder.failure("ADMIN", null, loginId, null, null,
                    LoginHistory.FAIL_NOT_FOUND, "존재하지 않는 관리자 로그인 ID");
            throw new BadCredentialsException(GENERIC_FAILURE);
        }
        // IP 화이트리스트 — 관리자별 (admin_id, ip) 매칭 필수 (SINGLE·CIDR·RANGE)
        AdminAllowIp allowIp = authService.matchAllowIp(user.getUserId(), clientIp);
        if (allowIp == null) {
            historyRecorder.failure("ADMIN", user.getUserId(), loginId, null, null,
                    LoginHistory.FAIL_IP, "허용 IP 미등록: " + clientIp);
            throw new BadCredentialsException(GENERIC_FAILURE);
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            historyRecorder.failure("ADMIN", user.getUserId(), loginId, null, null,
                    LoginHistory.FAIL_LOCKED, "잠금 해제 예정: " + user.getLockedUntil());
            throw new LockedException("계정이 잠겨 있습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (!"ACTIVE".equals(user.getStatus()) && !"LOCKED".equals(user.getStatus())) {
            historyRecorder.failure("ADMIN", user.getUserId(), loginId, null, null,
                    LoginHistory.FAIL_DISABLED, "상태: " + user.getStatus());
            throw new DisabledException("사용할 수 없는 계정입니다.");
        }
        // 잠금 이력이 있는 계정은 비밀번호 검사 전에 CAPTCHA — 대입 시도의 다음 라운드를 늦춘다
        if ("Y".equals(user.getCaptchaRequiredYn())) {
            HttpServletRequest request = currentRequest();
            captchaService.require(request);
            if (!captchaService.verify(request)) {
                historyRecorder.failure("ADMIN", user.getUserId(), loginId, null, null,
                        LoginHistory.FAIL_CAPTCHA, "자동입력 방지 문답 불일치");
                throw new BadCredentialsException("자동입력 방지 답이 올바르지 않습니다.");
            }
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            authService.loginFailed("ADMIN", user.getUserId());
            historyRecorder.failure("ADMIN", user.getUserId(), loginId, null, null,
                    LoginHistory.FAIL_PASSWORD,
                    "실패 누계: " + (user.getLoginFailCount() + 1));
            throw new BadCredentialsException(GENERIC_FAILURE);
        }
        // 2단계 인증 — 등록된 계정은 OTP 없이는 통과할 수 없다(비밀번호만으로는 부족).
        boolean twoFactorPending = false;
        if ("Y".equals(user.getTwoFactorEnabledYn())) {
            String otp = currentRequest().getParameter(TOTP_PARAM);
            if (!totpService.verify(aes256Gcm.decrypt(user.getTwoFactorSecret()), otp)) {
                historyRecorder.failure("ADMIN", user.getUserId(), loginId, null, null,
                        LoginHistory.FAIL_2FA, "OTP 불일치");
                throw new BadCredentialsException("인증코드가 올바르지 않습니다.");
            }
        } else if (authService.isTwoFactorRequired(user.getGroupId())) {
            // 그룹 정책은 강제인데 미등록 — 로그인은 시키되 등록 화면 밖으로 못 나가게 한다
            twoFactorPending = true;
        }
        // 자격은 맞았지만 유효기간 초과 — 세션을 주지 않는다(잠금 카운트 대상은 아니다).
        // 재설정 경로는 관리자에 의한 초기화 (셀프 재설정은 회원 도메인 페이즈).
        if (user.getPasswordExpireAt() != null
                && user.getPasswordExpireAt().isBefore(LocalDateTime.now())) {
            historyRecorder.failure("ADMIN", user.getUserId(), loginId, null, null,
                    LoginHistory.FAIL_EXPIRED, "만료일: " + user.getPasswordExpireAt());
            throw new CredentialsExpiredException(
                    "비밀번호 유효기간이 만료되었습니다. 관리자에게 재설정을 요청해 주세요.");
        }

        authService.loginSucceeded("ADMIN", user.getUserId(), clientIp, allowIp.getIpId());
        captchaService.clear(currentRequest().getSession(false));
        historyRecorder.success("ADMIN", user.getUserId(), loginId, null, null);
        List<GrantedAuthority> authorities = toAuthorities(user.getRoleCodes());
        LoginPrincipal principal = new LoginPrincipal("ADMIN", user.getUserId(),
                user.getLoginId(), user.getDisplayName(), null, null, user.getRoleIds(),
                twoFactorPending);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
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
}
