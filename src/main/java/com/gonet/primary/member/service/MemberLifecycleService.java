package com.gonet.primary.member.service;

/**
 * 회원 생명주기 — 휴면 · 탈퇴 · 완전삭제.
 *
 * <pre>
 * ACTIVE ──[마지막 로그인 +1년]──▶ 휴면(tb_member_dormant)
 * 휴면   ──[휴면 전환 +1년]──────▶ 탈퇴(원장 + tb_member PII 전부 NULL)
 * 탈퇴   ──[탈퇴 +1년]───────────▶ 완전 삭제(hard delete)
 * </pre>
 *
 * <p>셀프 탈퇴와 배치 탈퇴가 <b>같은 메서드</b>를 탄다. 경로가 둘이면 한쪽만 고쳐져
 * 정책이 갈린다(원장 순서·보존기간 계산 등).
 */
public interface MemberLifecycleService {

    /**
     * 탈퇴 처리 — 한 트랜잭션.
     *
     * <p>순서가 중요하다: ① 원장(`tb_member_withdraw`) INSERT → ② `tb_member` PII NULL.
     * PII 삭제는 되돌릴 수 없으므로 원장을 먼저 남기지 않으면 사고 시 복구·소명 근거가
     * 사라진다.
     *
     * @param reason       탈퇴 사유 — 셀프 탈퇴는 사용자 입력, 배치는 정책 문구
     * @param withdrawType 탈퇴 유형 — {@code USER_REQUEST}(셀프) ·
     *                     {@code DORMANT_EXPIRED}(휴면 만료 배치) · {@code ADMIN_FORCE}(강제).
     *                     컬럼명이 {@code withdraw_status} 지만 실제로는 <b>유형</b>이다
     *                     (CHECK 제약이 이 셋만 허용 — 실측)
     */
    void withdraw(String memberId, String reason, String withdrawType);

    /** 휴면 전환 — tb_member 행을 tb_member_dormant 로 옮긴다(뷰에서 자동으로 사라진다). */
    void moveToDormant(String memberId, String reason);
}
