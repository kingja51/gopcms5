package com.gonet.primary.auth.service;

/** 비밀번호 변경 — 구성 규칙·재사용 금지·이력 적재를 한 트랜잭션으로 처리. */
public interface PasswordService {

    /**
     * 본인 비밀번호 변경.
     *
     * @param userType    ADMIN | MEMBER
     * @param userId      ADM_/MBR_ ID
     * @param currentRaw  현재 비밀번호(본인 확인)
     * @param newRaw      새 비밀번호
     * @throws IllegalArgumentException 현재 비밀번호 불일치 · 구성 규칙 위반 · 최근 이력 재사용
     */
    void change(String userType, String userId, String currentRaw, String newRaw);
}
