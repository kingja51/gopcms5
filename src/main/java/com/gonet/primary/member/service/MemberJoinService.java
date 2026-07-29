package com.gonet.primary.member.service;

import com.gonet.primary.member.dto.MemberJoinForm;

/** 회원 가입. */
public interface MemberJoinService {

    /**
     * 가입 처리 — 검증 → 암호화 저장 → 동의 이력까지 한 트랜잭션.
     *
     * @return 생성된 member_id
     */
    String join(MemberJoinForm form, String siteId, String userAgent);
}
