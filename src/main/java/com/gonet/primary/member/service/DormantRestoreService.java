package com.gonet.primary.member.service;

/**
 * 휴면 복원 — 이메일 인증번호(OTP) 경로.
 *
 * <p>실명인증(NICE) 경로는 계약 정보가 들어오는 시점에 같은 자리에 붙인다.
 * 어느 수단으로 확인했든 <b>복원 처리는 하나</b>({@link #restore})를 탄다.
 */
public interface DormantRestoreService {

    /**
     * 휴면 여부 확인 — <b>아이디와 비밀번호가 모두 맞을 때만</b> 휴면으로 판정한다.
     *
     * <p>아이디만으로 "휴면 계정입니다" 를 알려 주면 그 자체가 계정 존재 확인 도구가 된다.
     *
     * @return 휴면 계정의 member_id, 아니면 null
     */
    String findDormantMemberId(String siteId, String loginId, String rawPassword);

    /**
     * 인증번호 발급 — 등록된 이메일로 보낸다.
     *
     * <p>이전 코드는 즉시 만료시킨다. 살아 있는 코드가 둘이면 시도 횟수 제한이 무의미해진다.
     *
     * @throws IllegalStateException 재발송 쿨다운 중
     */
    void issueCode(String memberId);

    /**
     * 인증번호 검증 + 복원.
     *
     * <p>실패 사유를 구분해 돌려주지 않는다 — 만료인지 오답인지 알려 주면 대입에 도움이 된다.
     *
     * @return 복원 성공 여부
     */
    boolean verifyAndRestore(String memberId, String code);
}
