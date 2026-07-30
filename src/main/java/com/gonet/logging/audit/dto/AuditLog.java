package com.gonet.logging.audit.dto;

import com.gonet.common.web.PageRequest;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 감사 로그 1건 — {@code logging_db.log_audit}.
 *
 * <p>테이블 주석이 범위를 정한다: <b>"관리자 CUD 전수"</b>. 그래서 적재는 도메인 서비스가
 * 각자 부르는 방식이 아니라 {@code /adm/**} 의 <b>비-GET 요청을 한 곳에서 가로채</b>
 * 기록한다({@code AuditTrailInterceptor}). 서비스마다 호출을 심는 방식은 "전수" 를
 * 보장할 수 없다 — 새 화면을 만들 때 한 줄 빠뜨리면 그 화면만 조용히 감사에서 빠진다.
 *
 * <p>접근 로그({@code log_access})와 겹치지 않는다. 접근 로그는 <b>모든 요청</b>을
 * 성능·트래픽 관점으로 남기고, 감사 로그는 <b>상태를 바꾼 행위</b>만 행위자·대상 중심으로
 * 남긴다. 둘은 {@code trace_id} 로 이어 붙인다.
 */
/*
 * @NoArgsConstructor 가 <b>반드시 있어야 한다</b>. @Builder 만 붙이면 Lombok 이
 * 전 인자 생성자만 만들고 기본 생성자를 없애는데, 그러면 MyBatis 가 setter 대신
 * <b>생성자 자동 매핑</b>으로 빠진다 — 컬럼을 골라 읽는 목록 쿼리(13개)와 생성자
 * 인자(18개)가 어긋나 조회가 500 으로 깨진다(실측 2026-07-30: 상세는 SELECT * 라
 * 우연히 통과하고 목록만 실패했다).
 * 형제 DTO 인 AccessLog 는 적재 전용이라 이 함정을 밟지 않았다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    private Long logAuditId;

    private String actorUserId;
    private String actorUserType;
    private String actorLoginId;

    /** 행위 — CREATE · UPDATE · DELETE · 그 외는 URL 마지막 마디를 대문자로. */
    private String action;
    /** 대상 엔티티 — {@code /adm/{entity}} 의 두 번째 마디를 대문자로(BOARD·MEMBER…). */
    private String targetEntity;
    /** 대상 ID — 경로/파라미터에서 찾은 {@code XXX_UUIDv7}. 못 찾으면 NULL. */
    private String targetId;

    private String requestUri;
    private String httpMethod;
    private String clientIp;
    private String userAgent;

    /**
     * 변경 전/후 스냅샷 — <b>현재는 항상 NULL</b>이다.
     *
     * <p>인터셉터는 요청이 끝난 뒤에 돌아 "전" 상태를 볼 수 없다. 채우려면 도메인
     * 서비스가 자기 트랜잭션 안에서 넘겨 줘야 하는데, 그러면 "전수" 를 보장하던 단일
     * 창구가 다시 도메인별 호출로 흩어진다. 지금은 <b>누가·무엇을·언제</b>까지만
     * 보장하고 값 스냅샷은 도입하지 않았다.
     *
     * <p>DDL 에 {@code json_valid} CHECK 이 걸려 있으므로 채울 때는 반드시 유효한
     * JSON 이어야 한다 — 빈 문자열은 제약 위반이다.
     */
    private String beforeJson;
    private String afterJson;

    /** SUCCESS | FAIL | ERROR — DDL CHECK 3값과 1:1. */
    private String result;

    /** MDC traceId — {@code log_access} · {@code sql.log} 와 대조하는 키. */
    private String traceId;

    private LocalDateTime loggedAt;
    private String createdBy;
    private String createdIp;

    /** 관리자 조회 조건. */
    @Getter
    @Setter
    public static class Search extends PageRequest {
        /** 행위 정확 일치 (CREATE·UPDATE·DELETE…). */
        private String action;
        /** 대상 엔티티 정확 일치 (BOARD·MEMBER…). */
        private String targetEntity;
        /** 결과 정확 일치 (SUCCESS·FAIL·ERROR). */
        private String result;
        /** 행위자 로그인 ID 정확 일치. */
        private String actorLoginId;
    }
}
