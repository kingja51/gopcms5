package com.gonet.common.audit;

/**
 * 현재 요청의 감사 주체(사용자 ID·IP) ThreadLocal 홀더.
 *
 * <p>P6(Security) 전까지는 필터가 IP 만 채우고 userId 는 null 일 수 있다 —
 * P6 에서 인증 주체(ADM_/MBR_ ID)를 연결한다. Virtual thread(요청당 1스레드) 전제라
 * 풀 누수 위험은 낮지만, 세팅한 필터의 finally 에서 반드시 {@link #clear()}.
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
        return auditor == null ? null : auditor.userId();
    }

    public static String currentIp() {
        Auditor auditor = HOLDER.get();
        return auditor == null ? null : auditor.ip();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
