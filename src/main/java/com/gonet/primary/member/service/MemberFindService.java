package com.gonet.primary.member.service;

/** 아이디 찾기 — 이름 + 이메일로 조회해 <b>마스킹된</b> 아이디를 돌려준다. */
public interface MemberFindService {

    /**
     * 아이디 찾기.
     *
     * <p>못 찾아도 예외를 던지지 않고 빈 결과를 준다 — "그런 계정 없음" 과 "찾았음" 이
     * 다른 형태로 나가면 이메일 존재 여부를 확인하는 도구가 된다. 화면 문구도 같아야 한다.
     *
     * @return 마스킹된 아이디, 없으면 null
     */
    String findLoginIdMasked(String siteId, String memberName, String email);
}
