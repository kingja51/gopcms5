package com.gonet.primary.member.service;

import com.gonet.primary.member.dto.JoinSession;
import com.gonet.primary.member.dto.MemberJoinForm;

/** 회원 가입. */
public interface MemberJoinService {

    /**
     * 가입 처리 — 검증 → 암호화 저장 → 동의 이력까지 한 트랜잭션.
     *
     * <p>{@code form} 은 사용자가 친 값이고 {@code wizard} 는 <b>서버가 확정한 값</b>이다
     * (약관 동의·본인인증 결과·소셜 프로필). 이름·생년월일처럼 양쪽에 다 있는 항목은
     * 인증을 거친 wizard 값이 이긴다 — 폼은 언제든 조작될 수 있다.
     *
     * @return 생성된 member_id
     */
    String join(MemberJoinForm form, JoinSession wizard, String siteId, String userAgent);
}
