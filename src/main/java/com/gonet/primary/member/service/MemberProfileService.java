package com.gonet.primary.member.service;

import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.dto.MemberProfileForm;

/** 마이페이지 — 조회·수정. */
public interface MemberProfileService {

    /** 본인 정보 조회(복호화된 평문). */
    MemberDto get(String memberId);

    /**
     * 개인정보 수정.
     *
     * <p>바꿀 수 있는 값만 폼에 있다 — 본인확인 근간값은 여기로 들어올 수 없다.
     * 이메일이 바뀌면 해시도 다시 만들고 인증 상태를 되돌린다.
     */
    void update(String memberId, MemberProfileForm form, String userAgent);
}
