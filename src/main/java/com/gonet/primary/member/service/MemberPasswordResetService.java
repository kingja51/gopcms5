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

    /**
     * 관리자 발급 — 회원 ID 만으로 임시 비밀번호를 낸다.
     *
     * <p>본인 확인 절차가 다를 뿐 <b>발급 자체는 같은 경로</b>다(만료 시각을 과거로 두고
     * 메일로만 전달). 경로가 둘이면 한쪽만 고쳐져 "관리자가 낸 임시 비밀번호는 만료가
     * 안 걸리는" 식으로 정책이 갈린다.
     *
     * @throws IllegalStateException 회원이 없거나 메일 주소가 없을 때 — 관리자에게는
     *         실패를 알려야 한다(사용자 경로처럼 조용히 끝내면 발급된 줄 안다)
     */
    void issueTemporaryPasswordByAdmin(String memberId);
}
