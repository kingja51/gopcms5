package com.gonet.config.security;

import com.gonet.common.util.Csv;
import com.gonet.common.util.IpMatch;
import com.gonet.common.web.ClientIpResolver;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.auth.dto.UrlAccessRule;
import com.gonet.primary.auth.service.UrlAccessService;
import com.gonet.primary.site.dto.SiteContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * DB 기반 인가 — tb_role_url_access 규칙으로 모든 요청을 판정한다 (P6-2).
 *
 * <p><b>평가 계약</b>
 * <ol>
 *   <li>정렬은 SQL 이 확정(priority ASC → 사이트 규칙 우선 → 긴 패턴 우선) —
 *       <b>첫 매칭 규칙이 최종 결정</b>이며 이후 규칙은 보지 않는다.</li>
 *   <li><b>무매칭 = DENY</b>. 새 URL 을 추가하고 규칙을 등록하지 않으면 화면이 열리지 않는다
 *       — 등록 절차는 doc/conventions.md §7. 무매칭은 설정 실수이므로 WARN 으로 남긴다.</li>
 *   <li>사이트 스코프 규칙은 {@code SiteContextHolder} 의 현재 사이트와 일치할 때만 후보다.
 *       SiteResolveFilter 는 시큐리티 필터보다 앞 순서라 판정 시점에 이미 바인딩돼 있다.</li>
 * </ol>
 *
 * <p><b>역할 판정</b>은 규칙의 {@code required_roles}(role_id CSV)와 사용자
 * {@code role_ids}(closure 전개 스냅샷)의 교집합이다 — 계층 상위 역할은 하위 역할 규칙을
 * 자동 통과한다(전개가 이미 반영됨). 전개 동기화는 {@code RoleService} 재계산 배치 몫.
 *
 * <p><b>IP 판정</b>({@code IP_ONLY})은 {@link ClientIpResolver} 단일 경로를 쓴다 —
 * {@code getRemoteAddr()} 직접 호출 금지 규약(CLAUDE.md). 리버스 프록시 뒤에서는 TCP 원격
 * 주소가 프록시 IP 라, 직접 읽으면 <b>프록시를 통과한 모든 요청이 같은 IP 로 보인다</b>.
 * 허용 목록 표기는 SINGLE·CIDR·RANGE 3종({@link IpMatch#matchesToken}).
 *
 * <p>미구현(P6-3): require_2fa_yn 강제 · require_csrf_yn (현재는 Spring Security CSRF 필터가 전담).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final UrlAccessService urlAccessService;
    private final ClientIpResolver clientIpResolver;

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
            RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        String path = pathOf(request);
        String method = request.getMethod();
        SiteContext site = SiteContextHolder.get();
        String siteId = site == null ? null : site.getSiteId();

        for (UrlAccessRule rule : urlAccessService.getActiveRules()) {
            if (!matches(rule, path, method, siteId)) {
                continue;
            }
            boolean granted = evaluate(rule, authentication.get(), request);
            if (!granted && log.isDebugEnabled()) {
                log.debug("접근 거부 — {} {} / 규칙 {}", method, path, rule.label());
            }
            return new AuthorizationDecision(granted);
        }

        log.warn("접근 규칙 없음 → DENY: {} {} — tb_role_url_access 에 규칙을 등록해야 한다"
                + " (doc/conventions.md §7)", method, path);
        return new AuthorizationDecision(false);
    }

    /* ── 매칭 ────────────────────────────────────────────────────────────── */

    private boolean matches(UrlAccessRule rule, String path, String method, String siteId) {
        if (rule.getSiteId() != null && !rule.getSiteId().equals(siteId)) {
            return false; // 다른 사이트의 규칙
        }
        if (!"ALL".equals(rule.getHttpMethod()) && !rule.getHttpMethod().equalsIgnoreCase(method)) {
            return false;
        }
        return pathMatcher.match(rule.getUrlPattern(), path);
    }

    /** 컨텍스트 경로를 제외한 요청 경로 — ERROR 디스패치에서는 포워드 대상(/error)이 된다. */
    private String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri.isEmpty() ? "/" : uri;
    }

    /* ── 판정 ────────────────────────────────────────────────────────────── */

    private boolean evaluate(UrlAccessRule rule, Authentication authentication,
            HttpServletRequest request) {
        boolean authenticated = isAuthenticated(authentication);
        LoginPrincipal principal = authenticated
                && authentication.getPrincipal() instanceof LoginPrincipal p ? p : null;

        return switch (rule.getAccessType()) {
            case "PERMIT_ALL" -> true;
            case "DENY" -> false;
            case "ANONYMOUS" -> !authenticated;
            case "IP_ONLY" -> ipAllowed(rule, request);
            case "AUTHENTICATED" -> authenticated && userTypeAllowed(rule, principal);
            case "ROLE" -> authenticated && userTypeAllowed(rule, principal) && principal != null
                    && intersects(rule.getRequiredRoleSet(),
                            Csv.toSet(principal.roleIds()));
            case "AUTH" -> authenticated && userTypeAllowed(rule, principal) && principal != null
                    && intersects(rule.getRequiredAuthSet(),
                            urlAccessService.getAuthIds(principal.roleIds()));
            default -> {
                log.error("알 수 없는 access_type '{}' — 규칙 {} 는 DENY 로 처리",
                        rule.getAccessType(), rule.getUrlAccessId());
                yield false;
            }
        };
    }

    /**
     * 허용 IP 판정 — 목록이 비어 있으면 통과시키지 않는다.
     *
     * <p>IP_ONLY 인데 allowed_ips 가 비었다면 "아무 데서나 열어라" 가 아니라 <b>등록을
     * 빠뜨린 것</b>이다. 빈 목록을 무조건 허용으로 읽으면 규칙을 잘못 넣은 순간 경로가
     * 통째로 열린다.
     */
    private boolean ipAllowed(UrlAccessRule rule, HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        boolean granted = rule.getAllowedIpSet().stream()
                .anyMatch(token -> IpMatch.matchesToken(token, clientIp));
        if (!granted) {
            log.warn("IP 차단 — {} 는 규칙 {} 의 허용 목록에 없다", clientIp, rule.label());
        }
        return granted;
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /** allowed_user_types 미지정 = 사용자 유형 무관. 지정 시 MEMBER/ADMIN 경계를 강제. */
    private boolean userTypeAllowed(UrlAccessRule rule, LoginPrincipal principal) {
        Set<String> allowed = rule.getAllowedUserTypeSet();
        return allowed.isEmpty() || (principal != null && allowed.contains(principal.userType()));
    }

    private boolean intersects(Set<String> required, Set<String> held) {
        return !required.isEmpty() && !Collections.disjoint(required, held);
    }
}
