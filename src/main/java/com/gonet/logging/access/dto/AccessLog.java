package com.gonet.logging.access.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 접근 로그 1건 — logging_db.log_access 와 1:1 (bigint AUTO_INCREMENT — DTO 는 적재 전용).
 * logged_at/created_at 은 DB DEFAULT. actor_* 는 P6(Security) 에서 인증 주체 연결.
 */
@Getter
@Setter
@Builder
public class AccessLog {

    private String siteId;
    private String siteCode;
    private String menuId;
    private String actorUserId;
    private String actorUserType;
    private String actorLoginId;
    private String requestUri;
    private String httpMethod;
    private String queryString;   // 민감 파라미터 마스킹 완료 값
    private String referer;
    private String clientIp;
    private String userAgent;     // 500자 절단 완료 값
    private Integer statusCode;
    private Integer responseMs;
    private Long bytesSent;
    private String sessionId;     // 세션 ID 마지막 8자
    private String traceId;       // MDC traceId
}
