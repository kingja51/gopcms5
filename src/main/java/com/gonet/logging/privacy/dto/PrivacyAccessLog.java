package com.gonet.logging.privacy.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 개인정보 접근 이력 한 건 — {@code log_privacy_access}(개인정보보호법 §29 접속기록).
 *
 * <p>"누가 · 언제 · 무엇을 · 왜" 를 남긴다. 특히 <b>왜</b>({@code accessReason})가 핵심이다 —
 * 대량 조회·내려받기·마스킹 해제는 그 자체로는 정상 업무와 구분되지 않고, 사유가 있어야
 * 사후에 소명할 수 있다.
 *
 * <p>상수는 DDL 의 CHECK 제약과 1:1 이다. 임의 문자열을 넣으면 제약 위반으로 적재가
 * 조용히 실패한다(다운로드 이력에서 실제로 겪은 유형이다 — 기록만 사라지고 기능은 돈다).
 */
@Getter
@Setter
public class PrivacyAccessLog {

    /* access_action — chk_privacy_action */
    /** 특정 정보주체의 상세를 열었다. */
    public static final String ACTION_READ = "READ";
    /** 목록·검색으로 여러 건을 훑었다. */
    public static final String ACTION_SEARCH = "SEARCH";
    /** 파일로 내보냈다 — 통제 밖으로 나간 것이라 가장 무거운 기록이다. */
    public static final String ACTION_EXPORT = "EXPORT";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    /** 마스킹을 풀어 원본을 봤다. */
    public static final String ACTION_DECRYPT = "DECRYPT";

    /* result — chk_privacy_result */
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL = "FAIL";
    /** 사유 미기재·권한 미달 등으로 막았다 — 막은 것도 기록이다. */
    public static final String RESULT_DENIED = "DENIED";
    public static final String RESULT_ERROR = "ERROR";

    private String actorUserId;
    private String actorUserType;
    private String actorLoginId;
    private String clientIp;
    private String userAgent;
    private String sessionId;
    private String requestUri;
    private String httpMethod;
    private String traceId;

    private String targetEntity;
    private String targetUserType;
    /** 목록 조회는 대상이 여럿이라 NULL. */
    private String targetId;
    private int targetCount = 1;
    /** 취급한 PII 항목 CSV — 무엇까지 봤는지가 범위 소명의 근거다. */
    private String piiFields;

    private String accessAction;
    private String accessReason;
    private String result = RESULT_SUCCESS;
    private String failReason;

    /** 적재 시각 — 조회 전용(INSERT 는 DB 의 NOW() 를 쓴다). */
    private java.time.LocalDateTime accessedAt;
}
