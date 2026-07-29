package com.gonet.primary.member.mapper;

import com.gonet.primary.member.dto.MemberLifecycleTarget;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 회원 생명주기 — 휴면·탈퇴·완전삭제 대상 조회와 이관. */
@EgovMapper
public interface MemberLifecycleMapper {

    /* ── 대상 조회 ─────────────────────────────────────────────────────── */

    /**
     * 휴면 대상 — 마지막 로그인이 기준일보다 오래된 활성 회원.
     *
     * <p>{@code last_login_at} 이 NULL 인 계정(가입 후 미로그인)은 {@code created_at} 을
     * 본다. 빠뜨리면 그 계정만 <b>영원히 늙지 않는다</b>.
     */
    List<MemberLifecycleTarget> findDormantTargets(@Param("cutoff") LocalDateTime cutoff,
                                                   @Param("limit") int limit);

    /** 사전 안내 대상 — 휴면까지 남은 일수가 특정 구간에 든 회원(중복 발송은 UNIQUE 로 차단). */
    List<MemberLifecycleTarget> findNoticeTargets(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to,
                                                  @Param("stage") String stage,
                                                  @Param("limit") int limit);

    /** 탈퇴 전환 대상 — 휴면 전환 후 기준일이 지난 휴면 회원. */
    List<MemberLifecycleTarget> findWithdrawTargets(@Param("cutoff") LocalDateTime cutoff,
                                                    @Param("limit") int limit);

    /** 완전 삭제 대상 — 원장의 보존기한이 지난 탈퇴 회원. */
    List<String> findPurgeTargets(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /* ── 휴면 ──────────────────────────────────────────────────────────── */

    /** tb_member → tb_member_dormant 복사(컬럼 대응은 XML 이 명시). */
    int copyToDormant(@Param("memberId") String memberId, @Param("reason") String reason);

    /**
     * 휴면 전환용 — 행을 <b>지우지 않고</b> delete_yn='Y' 로 감춘다.
     *
     * <p>하드 삭제는 불가능하다: 동의 이력·비밀번호 이력이 FK 로 tb_member 를 참조하는데,
     * 그 둘은 증빙이라 함께 지울 수 없다(실측 — 가입 절차를 거친 회원은 전환이 실패했다).
     * {@code vw_user_login} 이 {@code delete_yn='N'} 을 거르므로 목적(로그인 차단)은 같다.
     */
    int softDeleteMemberRow(@Param("memberId") String memberId);

    /** 완전 삭제용 하드 삭제 — 자식 행을 모두 지운 뒤에만 부른다. */
    int deleteMemberRow(@Param("memberId") String memberId);

    /* ── 탈퇴 ──────────────────────────────────────────────────────────── */

    /** 원장 적재 — 해시만 남긴다(재가입 제한·분쟁 대응의 유일한 근거). */
    int insertWithdrawLedger(@Param("memberId") String memberId,
                             @Param("reason") String reason,
                             @Param("withdrawType") String withdrawType,
                             @Param("retentionExpireAt") LocalDateTime retentionExpireAt,
                             @Param("legalBasis") String legalBasis,
                             @Param("actor") String actor,
                             @Param("clientIp") String clientIp);

    /** 휴면 회원의 원장 적재 — 원본이 tb_member_dormant 에 있다. */
    int insertWithdrawLedgerFromDormant(@Param("memberId") String memberId,
                                        @Param("reason") String reason,
                                        @Param("withdrawType") String withdrawType,
                                        @Param("retentionExpireAt") LocalDateTime retentionExpireAt,
                                        @Param("legalBasis") String legalBasis,
                                        @Param("actor") String actor,
                                        @Param("clientIp") String clientIp);

    /** PII 전부 NULL — 되돌릴 수 없다. 원장 INSERT 이후에만 부른다. */
    int nullifyPii(@Param("memberId") String memberId, @Param("actor") String actor);

    int nullifyDormantPii(@Param("memberId") String memberId);

    /* ── 완전 삭제 ─────────────────────────────────────────────────────── */

    int deleteConsents(@Param("memberId") String memberId);

    int deletePasswordHistory(@Param("memberId") String memberId);

    int deleteOauth(@Param("memberId") String memberId);

    int deleteDormantNotices(@Param("memberId") String memberId);

    int deleteDormantRow(@Param("memberId") String memberId);

    int deleteWithdrawLedger(@Param("memberId") String memberId);

    /* ── 휴면 복원 (P10-5) ─────────────────────────────────────────────── */

    /**
     * 휴면 계정 조회 — 아이디로 찾는다.
     *
     * <p>이 조회 결과만으로 "휴면입니다" 를 알려 주면 안 된다. 비밀번호까지 맞을 때만
     * 안내로 분기한다 — 아이디만으로 알려 주면 계정 존재가 새어 나간다.
     */
    com.gonet.primary.member.dto.MemberDto findDormantByLoginId(
            @Param("siteId") String siteId, @Param("loginId") String loginId);

    /** 휴면 계정 단건 — 코드 발급·복원 안내에 쓴다(PII 복호화). */
    com.gonet.primary.member.dto.MemberDto findDormantById(@Param("memberId") String memberId);

    /**
     * 복원 — 감춰 둔 tb_member 행을 되살린다(status=ACTIVE, delete_yn='N').
     *
     * <p>휴면 전환이 소프트 삭제라 행이 그대로 있다. 마지막 로그인을 지금으로 찍지 않으면
     * 복원한 그날 다시 휴면 대상이 된다.
     */
    int restoreToMember(@Param("memberId") String memberId);

    int markRestored(@Param("memberId") String memberId);

    /* ── 사전 안내 이력 ────────────────────────────────────────────────── */

    int insertNotice(@Param("noticeId") String noticeId, @Param("memberId") String memberId,
                     @Param("stage") String stage);

    /** 로그인하면 안내 이력을 지운다 — 다음 사이클에 다시 보내야 한다. */
    int deleteNoticesByMember(@Param("memberId") String memberId);
}
