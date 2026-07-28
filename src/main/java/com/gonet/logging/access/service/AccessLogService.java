package com.gonet.logging.access.service;

import com.gonet.logging.access.dto.AccessLog;

public interface AccessLogService {

    /** 접근 로그 1건 적재 — 호출측(필터)은 실패를 삼켜야 한다(로그가 요청을 깨지 않음). */
    void write(AccessLog accessLog);
}
