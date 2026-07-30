package com.gonet.logging.error.dto;

import com.gonet.common.web.PageRequest;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 애플리케이션 에러 로그 한 건 — {@code log_error}.
 *
 * <p>주 용도는 <b>조용히 삼킨 실패를 드러내는 것</b>이다. 부가 기록(접근·개인정보취급·파기
 * 이력) 적재가 깨져도 본 업무는 계속 진행하는 것이 이 프로젝트의 원칙인데, 그러면 실패가
 * 애플리케이션 로그 파일에만 남아 아무도 보지 않게 된다. 그 실패를 관리자 화면까지
 * 끌어올리기 위한 테이블이다.
 *
 * <p>미처리 예외 기록에도 같은 구조를 쓴다.
 */
@Getter
@Setter
public class ErrorLog {

    private Long logErrorId;

    private String siteId;
    private String siteCode;
    private String menuId;

    private String actorUserId;
    private String actorUserType;
    private String actorLoginId;

    private String requestUri;
    private String httpMethod;
    /** <b>민감 파라미터를 가린 뒤</b> 담는다 — 쿼리스트링에 토큰·사유가 실려 온다. */
    private String queryString;
    private String clientIp;
    private String userAgent;

    /** 예외 FQCN — NOT NULL. 예외가 아닌 경우에도 분류 문자열을 반드시 채운다. */
    private String errorClass;
    private String errorMessage;
    private String stackTrace;
    private Integer statusCode;

    /** MDC traceId — log_access 와 대조하는 키. */
    private String traceId;
    private String sessionId;

    private LocalDateTime loggedAt;
    private String createdBy;
    private String createdIp;

    /** 관리자 목록 검색 조건. */
    @Getter
    @Setter
    public static class Search extends PageRequest {
        /** 예외 클래스·메시지·URI 부분 일치. */
        private String errorClass;
    }

    /**
     * 분류별 건수 — 관리자 화면 상단의 빠른 진단용.
     *
     * <p>목록을 넘겨 보기 전에 "지금 무엇이 제일 많이 깨지는가" 를 먼저 보여 준다.
     * 삼킨 실패는 한 건이 아니라 <b>반복</b>으로 나타나는 경우가 많아서, 집계가
     * 개별 행보다 먼저 눈에 들어와야 한다.
     */
    @Getter
    @Setter
    public static class ClassCount {
        private String errorClass;
        private long total;
        private LocalDateTime lastAt;
    }
}
