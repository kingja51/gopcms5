package com.gonet.logging.purge.service;

/** 개인정보 파기 이력 — 적재 전용. */
public interface PiiPurgeLogService {

    /**
     * 파기 이력 1건.
     *
     * @param memberId  파기된 회원 ID — <b>해시로 바꿔 저장한다</b>(평문 미보관)
     * @param reason    {@code RETENTION_EXPIRED} / {@code WITHDRAW}
     * @param tableList 실제로 손댄 테이블 CSV — 파기 범위의 증빙
     */
    void writeMemberPurge(String memberId, String reason, String tableList);
}
