package com.gonet.logging.retention.dto;

import java.util.List;

/**
 * 파기 대상 테이블 등록부 — <b>제외 대상까지 명시적으로 적는다</b>.
 *
 * <p>제외를 "목록에 안 적음" 으로 표현하면, 나중에 보는 사람은 빠뜨린 것인지 일부러 뺀
 * 것인지 알 수 없다. 그래서 {@link #purgeable} 을 두고 전부 나열한다 — 이 파일 하나가
 * "어느 테이블을 언제까지 두는가" 에 대한 답이다.
 *
 * <p>보존 <b>기간</b>은 여기 없다. 기간은 설정({@code gopcms.retention.*})이 갖고,
 * 이 등록부는 어느 설정 키를 쓰는지({@link #retentionKey})만 가리킨다. 값이 코드에
 * 박히면 정책이 바뀔 때 빌드를 다시 해야 한다.
 *
 * @param table         테이블명
 * @param timeColumn    보존기간 판정 기준 컬럼(각 테이블마다 이름이 다르다)
 * @param retentionKey  적용할 보존기간 — {@link RetentionKey}
 * @param purgeable     배치가 실제로 지우는가
 * @param note          제외라면 그 이유. 목록만 보고도 판단할 수 있어야 한다
 */
public record RetentionTarget(String table, String timeColumn, RetentionKey retentionKey,
        boolean purgeable, String note) {

    /** 어느 보존기간 설정을 쓰는지 — 5년/36개월이 한 배치 안에 섞여 있다. */
    public enum RetentionKey {
        /** 개인정보 접근·파기 이력 — 5년. */
        PRIVACY,
        /** 일반 로그 — 36개월. */
        LOG,
        /** 로그인 이력 — 36개월이지만 primary_db 다. */
        LOGIN_HISTORY,
        /** 파기하지 않는다. */
        NONE
    }

    private static RetentionTarget purge(String table, String timeColumn, RetentionKey key) {
        return new RetentionTarget(table, timeColumn, key, true, null);
    }

    private static RetentionTarget keep(String table, String note) {
        return new RetentionTarget(table, null, RetentionKey.NONE, false, note);
    }

    /**
     * {@code logging_db} 대상.
     *
     * <p>{@code stat_*} 는 아예 등장하지 않는다 — 영구 보존이라 이 배치의 관심사가
     * 아니고, 목록에 넣으면 언젠가 누군가 기간을 붙인다.
     */
    public static List<RetentionTarget> loggingTargets() {
        return List.of(
                purge("log_access", "logged_at", RetentionKey.LOG),
                purge("log_audit", "logged_at", RetentionKey.LOG),
                purge("log_error", "logged_at", RetentionKey.LOG),
                purge("log_security", "logged_at", RetentionKey.LOG),
                purge("log_file_download", "downloaded_at", RetentionKey.LOG),
                // 접근 이력은 5년 — 사고가 늦게 드러나도 소명할 수 있어야 한다
                purge("log_privacy_access", "accessed_at", RetentionKey.PRIVACY),
                // 파기 기록을 파기하면 "지웠다" 는 증빙이 사라진다. 보존기간(5년)은
                // 정책상 존재하지만 이 배치는 손대지 않는다 — 자기가 남긴 기록을
                // 같은 배치가 지우는 구조 자체를 만들지 않는다(PLAN §P10-7).
                keep("log_pii_purge", "파기 증빙 — 배치가 자기 기록을 지우지 않는다(5년 보존 정책은 별도 절차)")
        );
    }

    /**
     * {@code primary_db} 대상 — <b>다른 DataSource·TxManager</b> 를 탄다.
     *
     * <p>성격은 로그인데 위치가 primary 다(P6-3). 한 배치가 두 DB 를 오간다는 사실이
     * 코드에서 보여야 크로스 DB 트랜잭션을 실수로 묶지 않는다.
     */
    public static List<RetentionTarget> primaryTargets() {
        return List.of(
                purge("tb_login_history", "attempted_at", RetentionKey.LOGIN_HISTORY)
        );
    }
}
