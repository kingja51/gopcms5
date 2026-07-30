package com.gonet.logging.audit.service;

import com.gonet.common.web.PageResult;
import com.gonet.logging.audit.dto.AuditLog;

/** 감사 로그 — 적재(REQUIRES_NEW)와 관리자 조회. */
public interface AuditLogService {

    /**
     * 적재 — 별도 트랜잭션.
     *
     * <p><b>예외를 삼키지 않는다.</b> 삼키는 판단은 {@link AuditTrailRecorder} 가 한다 —
     * 이 계층이 조용해지면 "감사 로그가 안 쌓이는" 상태를 아무도 모른다.
     * 실제로 이 테이블은 코드 없이 스키마만 있던 동안 <b>0건</b>이었고, 그것을 알려 주는
     * 신호가 어디에도 없었다.
     */
    void write(AuditLog log);

    PageResult<AuditLog> getPage(AuditLog.Search search);

    AuditLog get(Long logAuditId);
}
