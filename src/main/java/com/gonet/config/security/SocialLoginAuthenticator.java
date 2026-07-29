package com.gonet.config.security;

import com.gonet.common.web.ClientIpResolver;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.primary.auth.dto.LoginUser;
import com.gonet.primary.auth.service.AuthService;
import com.gonet.primary.site.dto.SiteContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 성공 처리 — 비밀번호 검증 없이 세션을 세운다.
 *
 * <p>이 클래스가 security 패키지에 있는 이유: 인증 토큰 조립과 세션 저장은 보안 배관이지
 * 회원 도메인의 일이 아니다. 컨트롤러가 SecurityContext 를 직접 만지면 같은 코드가
 * 여러 곳에 복제되고 세션 고정 방어를 빠뜨리기 쉽다.
 *
 * <p><b>전제</b>: 호출 전에 외부 계정 검증이 끝나 있어야 한다. 여기서는 계정 상태만
 * 다시 본다 — 연결이 살아 있어도 계정이 잠기거나 휴면이면 들여보내지 않는다.
 *
 * <p>폼 로그인과 같은 이력·잠금 정책을 태운다: 성공 이력 적재, 마지막 로그인 갱신,
 * 세션 ID 재발급(고정 공격 차단)까지 {@link MemberAuthenticationProvider} 와 같은 순서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialLoginAuthenticator {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;
    private final LoginHistoryRecorder historyRecorder;

    private final SecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    /**
     * 세션 수립. 로그인 가능한 상태가 아니면 아무것도 하지 않고 {@code false}.
     *
     * @return 로그인 성공 여부
     */
    public boolean login(HttpServletRequest request, HttpServletResponse response,
            SiteContext site, String loginId) {
        LoginUser user = authService.findLoginUser("MEMBER", site.getSiteId(), loginId);
        if (user == null) {
            // vw_user_login 에 없다 = 탈퇴·휴면·삭제. 연결만 살아 있는 상태다.
            log.warn("소셜 로그인 대상 없음 site={} loginId={}", site.getSiteCode(), loginId);
            return false;
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            log.info("소셜 로그인 거부 status={} loginId={}", user.getStatus(), loginId);
            return false;
        }

        String clientIp = clientIpResolver.resolve(request);
        authService.loginSucceeded("MEMBER", user.getUserId(), clientIp, null);
        historyRecorder.success("MEMBER", user.getUserId(), loginId,
                site.getSiteId(), site.getSiteCode());

        LoginPrincipal principal = new LoginPrincipal("MEMBER", user.getUserId(),
                user.getLoginId(), user.getDisplayName(), site.getSiteId(), site.getSiteCode(),
                user.getRoleIds(), false);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, AdminAuthenticationProvider.toAuthorities(user.getRoleCodes())));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        // 로그인 전 세션 ID 로 로그인 후 세션을 타는 고정 공격을 끊는다(폼 로그인과 동일)
        request.changeSessionId();
        return true;
    }
}
