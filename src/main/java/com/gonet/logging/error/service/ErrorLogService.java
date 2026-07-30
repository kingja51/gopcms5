package com.gonet.logging.error.service;

import com.gonet.common.web.PageResult;
import com.gonet.logging.error.dto.ErrorLog;
import java.util.List;

/** 에러 로그 — 적재(REQUIRES_NEW)와 관리자 조회. */
public interface ErrorLogService {

    /**
     * 적재 — 별도 트랜잭션.
     *
     * <p><b>예외를 삼키지 않는다.</b> 삼키는 판단은 {@link ErrorLogger} 가 한다 —
     * 이 계층이 조용해지면 "에러 로그도 안 남는" 상태를 아무도 모른다.
     */
    void write(ErrorLog log);

    PageResult<ErrorLog> getPage(ErrorLog.Search search);

    /** 최근 {@code days} 일의 분류별 건수 — 목록보다 먼저 보여 줄 진단 요약. */
    List<ErrorLog.ClassCount> getClassCounts(int days);

    ErrorLog get(Long logErrorId);
}
