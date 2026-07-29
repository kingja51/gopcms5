package com.gonet.primary.member.service;

/**
 * 휴면 복원 — 수단은 둘, 처리는 하나.
 *
 * <ul>
 *   <li><b>이메일 인증번호</b> — 아이디·비밀번호로 본인을 가린 뒤 메일로 받은 코드</li>
 *   <li><b>실명인증(NICE)</b> — DI 하나로 확인이 끝난다</li>
 * </ul>
 *
 * <p>어느 수단으로 확인했든 <b>복원 처리는 하나</b>를 탄다. 경로가 갈리면 한쪽만
 * 고쳐져 "이 수단으로 복원하면 안내 이력이 안 지워지는" 식의 차이가 생긴다.
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

    /**
     * 실명인증 결과로 복원 — DI 가 일치하는 휴면 계정을 되살린다.
     *
     * <p>아이디·비밀번호를 묻지 않는다. 비밀번호를 잊어 못 들어오는 것이 휴면의 흔한
     * 사정이라, 그걸 요구하면 복원 수단이 하나 더 필요해진다.
     *
     * <p>실명인증 없이 가입한 계정({@code di_hash} 가 없는 계정)은 이 경로로 복원할 수
     * 없다 — 대조할 값이 없다. 그런 계정은 이메일 인증번호 경로를 쓴다.
     *
     * @param di NICE 가 돌려준 중복가입 확인정보
     * @return 복원된 회원의 로그인 ID, 대상이 없으면 null
     */
    String restoreByIdentity(String siteId, String di);
}
