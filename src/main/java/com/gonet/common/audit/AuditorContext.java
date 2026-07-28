package com.gonet.common.audit;

import com.gonet.common.web.LoginPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 현재 요청의 감사 주체(사용자 ID·IP) ThreadLocal 홀더.
 *
 * <p><b>IP</b> 는 필터({@code SiteResolveFilter})가 요청 진입 시 채운다
 * ({@code ClientIpResolver} 단일 경로 — 신뢰 프록시 밖 X-Forwarded-For 는 무시).
 *
 * <p><b>userId</b> 는 홀더에 값이 없으면 조회 시점의 SecurityContext 에서 읽는다.
 * 필터가 시큐리티 체인 <b>바깥</b>이라 진입 시점엔 아직 인증 전이기 때문이다(P6-3 실측).
 * 배치·스케줄러처럼 요청 밖 흐름은 {@link #set}으로 주체를 명시할 수 있다.
 *
 * <p>Virtual thread(요청당 1스레드) 전제라 풀 누수 위험은 낮지만,
 * 세팅한 필터의 finally 에서 반드시 {@link #clear()}.
 */
public final class AuditorContext {

    /** 감사 주체 — userId 는 ADM_/MBR_ 접두 ID (미인증 시 null). */
    public record Auditor(String userId, String ip) {}

    private static final ThreadLocal<Auditor> HOLDER = new ThreadLocal<>();

    private AuditorContext() {}

    public static void set(String userId, String ip) {
        HOLDER.set(new Auditor(userId, ip));
    }

    public static String currentUserId() {
        Auditor auditor = HOLDER.get();
        if (auditor != null && auditor.userId() != null) {
            return auditor.userId();
        }
        return authenticatedUserId();
    }

    public static String currentIp() {
        Auditor auditor = HOLDER.get();
        return auditor == null ? null : auditor.ip();
    }

    /** 인증된 주체의 user_id — 미인증·요청 밖이면 null. */
    public static String authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof LoginPrincipal principal
                ? principal.userId() : null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
