package com.gonet.primary.auth.service;

import com.gonet.primary.auth.dto.LoginHistory;

/** 로그인 이력 적재 — 인증 흐름과 독립된 트랜잭션(REQUIRES_NEW). */
public interface LoginHistoryService {

    /** PK 채번 후 1건 적재. 호출은 {@code LoginHistoryRecorder} 경유(실패 격리). */
    void record(LoginHistory history);
}
