package com.gonet.logging.purge.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 개인정보 파기 이력 한 건 — {@code log_pii_purge}(insert-only).
 *
 * <p><b>정보주체를 다시 식별할 수 없어야 한다.</b> 파기했다는 사실을 남기려고 회원 ID 를
 * 평문으로 적으면, 파기 이력 자체가 "이 사람이 우리 회원이었다" 는 개인정보가 된다.
 * 그래서 {@code userIdHash} 만 둔다 — 같은 사람인지 대조는 되되 역추적은 안 된다.
 *
 * <p>이 기록은 다른 로그보다 오래(5년) 남는다. 파기했다는 증빙을 파기하면 소명할
 * 근거가 사라지기 때문이다(PLAN §P10-7).
 */
@Getter
@Setter
public class PiiPurgeLog {

    /** 보존기간 만료로 배치가 지웠다. */
    public static final String REASON_RETENTION_EXPIRED = "RETENTION_EXPIRED";
    /** 회원 요청·관리자 처리로 즉시 파기했다. */
    public static final String REASON_WITHDRAW = "WITHDRAW";

    public static final String USER_TYPE_MEMBER = "MEMBER";

    private String piiPurgeLogId;
    private String userType;
    /** HMAC-SHA256(user_id) — 평문 ID 는 절대 담지 않는다. */
    private String userIdHash;
    private LocalDateTime purgedAt;
    private String purgeReason;
    /** 실제로 손댄 테이블 CSV — 파기 범위의 증빙이다. */
    private String tableList;
    private String legalBasis;
    private String createdBy;
    private String createdIp;
}
