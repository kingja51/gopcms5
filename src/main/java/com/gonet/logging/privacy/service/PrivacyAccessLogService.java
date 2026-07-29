package com.gonet.logging.privacy.service;

import com.gonet.logging.privacy.dto.PrivacyAccessLog;
import java.util.List;

/** 개인정보 접근 이력 — 적재는 별도 트랜잭션, 조회는 관리자 화면용. */
public interface PrivacyAccessLogService {

    /** 적재 — {@code REQUIRES_NEW} 로 주 트랜잭션과 분리한다. */
    void write(PrivacyAccessLog log);

    List<PrivacyAccessLog> recentByTarget(String targetEntity, String targetId, int limit);
}
