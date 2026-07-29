package com.gonet.primary.member.service;

/** 비밀번호 찾기 — 임시 비밀번호 발급 + 메일 발송. */
public interface MemberPasswordResetService {

    /**
     * 임시 비밀번호를 발급해 등록된 이메일로 보낸다.
     *
     * <p>결과를 돌려주지 않는다 — 성공/실패가 화면에 갈리면 아이디·이메일 조합의
     * 가입 여부를 확인하는 도구가 된다. 화면은 언제나 같은 안내를 보여준다.
     */
    void issueTemporaryPassword(String siteId, String loginId, String email);
}
