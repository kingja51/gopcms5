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

    /**
     * 원장에 남길 이름의 <b>원본</b>을 읽는다 — 복호화된 평문이다.
     *
     * <p>PII NULL 처리({@link #nullifyPii}) 전에만 값이 있다. 마스킹은 SQL 이 못 한다
     * (저장값이 암호문 {@code {AG}…}) — 서비스가 {@code Mask.name()} 을 거쳐 넣는다.
     *
     * <p>이름 외 컬럼은 채우지 않는다(대상 DTO 를 재사용할 뿐이다).
     */
    MemberLifecycleTarget findNameSource(@Param("memberId") String memberId);

    /** 휴면 테이블 쪽 원본 — 휴면 경유 탈퇴 경로에서 쓴다. */
    MemberLifecycleTarget findDormantNameSource(@Param("memberId") String memberId);

    /**
     * 원장 적재 — 해시만 남긴다(재가입 제한·분쟁 대응의 유일한 근거).
     *
     * @param memberName <b>마스킹된</b> 이름. 평문을 넣으면 파기의 의미가 사라진다.
     */
    int insertWithdrawLedger(@Param("memberId") String memberId,
                             @Param("memberName") String memberName,
                             @Param("reason") String reason,
                             @Param("withdrawType") String withdrawType,
                             @Param("retentionExpireAt") LocalDateTime retentionExpireAt,
                             @Param("legalBasis") String legalBasis,
                             @Param("actor") String actor,
                             @Param("clientIp") String clientIp);

    /** 휴면 회원의 원장 적재 — 원본이 tb_member_dormant 에 있다. */
    int insertWithdrawLedgerFromDormant(@Param("memberId") String memberId,
                                        @Param("memberName") String memberName,
                                        @Param("reason") String reason,
                                        @Param("withdrawType") String withdrawType,
                                        @Param("retentionExpireAt") LocalDateTime retentionExpireAt,
                                        @Param("legalBasis") String legalBasis,
                                        @Param("actor") String actor,
                                        @Param("clientIp") String clientIp);

    /**
     * PII 파기 — 되돌릴 수 없다. 원장 INSERT 이후에만 부른다.
     *
     * <p>이름만 예외다: NULL 이 아니라 <b>마스킹된 값</b>을 남긴다({@code member_name} 은
     * V12 부터 NOT NULL). 나머지 PII 는 전부 NULL 이 된다.
     *
     * @param maskedName {@code Mask.name()} 을 거친 값. <b>평문을 넣으면 파기가 아니다.</b>
     */
    int nullifyPii(@Param("memberId") String memberId, @Param("actor") String actor,
                   @Param("maskedName") String maskedName);

    /** 휴면 스냅샷의 PII 파기 — 이름은 같은 이유로 마스킹된 값을 남긴다. */
    int nullifyDormantPii(@Param("memberId") String memberId,
                          @Param("maskedName") String maskedName);

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
     * 본인인증(DI)으로 휴면 계정 찾기 — 실명인증 복원 경로의 진입점.
     *
     * <p>아이디·비밀번호를 묻지 않는다. 비밀번호를 잊어 못 들어오는 경우가 휴면의
     * 흔한 사정이라, 그것을 요구하면 복원 수단이 하나 더 필요해진다. DI 는 그 자체로
     * 본인 확인이 끝난 값이다.
     *
     * <p>DI 는 암호문이라 {@code =} 를 걸 수 없어 해시로 찾는다.
     */
    com.gonet.primary.member.dto.MemberDto findDormantByDiHash(@Param("siteId") String siteId,
            @Param("diHash") String diHash);

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

    /**
     * 게시글 작성자 익명화 — 탈퇴 시 {@code writer_name} 을 표시용 문구로 바꾼다.
     *
     * <p>{@code writer_name} 은 그 자체로 개인정보다. 회원 본체의 PII 를 파기하면서
     * 게시판에 실명이 그대로 남아 있으면 "즉시 파기" 가 말뿐이 된다.
     *
     * <p>{@code writer_user_id} 는 <b>남긴다</b>. 회원 행이 사라진 뒤에는 그 값으로
     * 사람을 되짚을 수 없어 식별정보가 아니고, 같은 작성자의 글을 묶어 보는
     * 운영·중재 기능이 여기에 걸려 있다.
     *
     * @return 바뀐 행 수
     */
    int anonymizeArticles(@Param("memberId") String memberId,
            @Param("anonymousName") String anonymousName);

    /** 댓글 작성자 익명화 — 게시글과 같은 이유. */
    int anonymizeComments(@Param("memberId") String memberId,
            @Param("anonymousName") String anonymousName);
}
