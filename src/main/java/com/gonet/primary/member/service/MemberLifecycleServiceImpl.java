package com.gonet.primary.member.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Mask;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.config.retention.RetentionProperties;
import com.gonet.logging.purge.dto.PiiPurgeLog;
import com.gonet.logging.purge.service.PiiPurgeLogService;
import com.gonet.primary.member.dto.MemberLifecycleTarget;
import com.gonet.primary.member.mapper.MemberLifecycleMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 생명주기 처리 — <b>셀프 탈퇴와 배치가 공유하는 유일한 경로</b>.
 *
 * <p>경로를 하나로 두는 이유: 탈퇴는 순서(원장 → PII NULL)와 보존기간 계산이 정책인데,
 * 화면용과 배치용을 따로 만들면 한쪽만 고쳐져 정책이 갈린다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
@Slf4j
public class MemberLifecycleServiceImpl extends AbstractCmsService
        implements MemberLifecycleService {

    /**
     * 탈퇴 회원의 작성자 표기.
     *
     * <p>설정으로 뺀 이유: 기관마다 쓰는 표현이 다르고("탈퇴한 회원" / "알 수 없음" /
     * "(삭제된 사용자)"), 화면에 그대로 노출되는 문구다.
     */
    @Value("${gopcms.member.anonymous-writer-name:탈퇴한 회원}")
    private String anonymousWriterName;

    private final MemberLifecycleMapper lifecycleMapper;
    private final RetentionProperties retentionProperties;
    private final PiiPurgeLogService piiPurgeLogService;

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void withdraw(String memberId, String reason, String withdrawType) {
        String actor = actor();
        LocalDateTime retentionExpireAt =
                LocalDateTime.now().plusMonths(retentionProperties.getWithdrawMonths());

        // ① 두 곳을 <b>모두</b> 먼저 읽는다. 한 회원의 PII 는 tb_member 와
        //    tb_member_dormant 양쪽에 있을 수 있다 — 휴면 전환이 복사 + soft-delete 라
        //    휴면 회원은 두 테이블에 동시에 존재한다.
        MemberLifecycleTarget active = lifecycleMapper.findNameSource(memberId);
        MemberLifecycleTarget dormant = lifecycleMapper.findDormantNameSource(memberId);
        if (active == null && dormant == null) {
            throw new IllegalArgumentException("탈퇴 대상 회원을 찾을 수 없습니다.");
        }
        // 이름은 지금 마스킹해 둔다 — 원본은 곧 파기되므로 이후에는 만들 수 없다.
        // 이 한 값을 원장(V11)과 파기 UPDATE 양쪽에 같이 쓴다.
        String masked = maskedName(active != null ? active : dormant);

        // ② 원장 먼저 — PII 파기는 되돌릴 수 없으므로 근거를 남기고 지운다.
        //    tb_member 행이 있으면 그쪽에서, 없으면 휴면 스냅샷에서 뜬다.
        int ledger = active != null
                ? lifecycleMapper.insertWithdrawLedger(memberId, masked, reason, withdrawType,
                        retentionExpireAt, "정보통신망법 제29조", actor, AuditorContext.currentIp())
                : lifecycleMapper.insertWithdrawLedgerFromDormant(memberId, masked, reason,
                        withdrawType, retentionExpireAt, "정보통신망법 제29조", actor,
                        AuditorContext.currentIp());
        if (ledger == 0) {
            throw new IllegalArgumentException("탈퇴 원장 적재에 실패했습니다.");
        }

        // ③ 파기 이력을 <b>먼저</b> — 크로스 DB 라 한 트랜잭션으로 못 묶는다.
        //    뒤에 남기면 파기가 성공하고 이력만 실패했을 때 흔적 없는 삭제가 된다.
        //    범위는 실제로 손댈 테이블만 적는다 — 증빙이므로 과대 기재도 곤란하다.
        piiPurgeLogService.writeMemberPurge(memberId, PiiPurgeLog.REASON_WITHDRAW,
                purgeScope(active != null, dormant != null));

        // ④ PII 파기 — 있는 쪽만 실제로 바뀐다(없으면 0건).
        //    <b>둘 다 부른다.</b> 전에는 원장이 적재된 쪽만 파기했는데, 휴면 회원은
        //    tb_member 행이 soft-delete 로 남아 있어 원장이 언제나 tb_member 에서
        //    적재됐다 — 그 결과 휴면 스냅샷의 PII(이름·이메일·전화·주소)가 통째로
        //    살아남았다. 실측으로 확인한 결함이다(2026-07-30).
        String purge = purgeName(masked);
        lifecycleMapper.nullifyPii(memberId, actor, purge);
        lifecycleMapper.nullifyDormantPii(memberId, purge);
        deleteOauthLinks(memberId);
        anonymizeWritings(memberId);
        log.info("탈퇴 처리 member={} type={} 휴면스냅샷={} retentionExpireAt={}",
                memberId, withdrawType, dormant != null ? "있음" : "없음", retentionExpireAt);
    }

    /** 파기 이력에 적을 테이블 목록 — 실제로 손대는 것만. */
    private String purgeScope(boolean hasActive, boolean hasDormant) {
        StringBuilder scope = new StringBuilder();
        if (hasActive) {
            scope.append("tb_member,");
        }
        if (hasDormant) {
            scope.append("tb_member_dormant,");
        }
        return scope.append("tb_member_oauth").toString();
    }

    /**
     * 마스킹한 이름 — 홍길동 → 홍*동. 값이 없으면 {@code null}.
     *
     * <p>{@code Mask.name()} 은 빈 값을 {@code "-"} 로 돌려주는데, 원장에는 그 문자열을
     * 넣지 않고 NULL 로 둔다 — "이름이 '-' 인 사람" 과 "이름을 모른다" 는 다르고,
     * 화면 표시는 어차피 NULL 을 '-' 로 그린다.
     */
    private String maskedName(MemberLifecycleTarget source) {
        if (source == null || source.getMemberName() == null
                || source.getMemberName().isBlank()) {
            return null;
        }
        return Mask.name(source.getMemberName());
    }

    /**
     * {@code tb_member.member_name} 에 넣을 파기 후 값 — <b>NULL 이 될 수 없다</b>(V12 NOT NULL).
     *
     * <p>원장은 NULL 을 허용하지만 이쪽은 아니다. 원본 이름을 못 읽은 경우(이미 파기된
     * 행을 다시 탈퇴시키는 등)에도 UPDATE 는 성공해야 한다 — 이름 하나 때문에 탈퇴가
     * 실패하면 파기가 막힌다.
     */
    private String purgeName(String masked) {
        return masked == null || masked.isBlank() ? "-" : masked;
    }

    /**
     * 소셜 계정 연결을 <b>행째로 지운다</b>.
     *
     * <p>탈퇴 사실은 원장(`tb_member_withdraw`)이 해시로 보관하므로 연결 행이 이력을
     * 대신할 이유가 없다. 그런데 남겨 두면 <b>재가입이 막힌다</b>:
     * {@code uk_oauth_provider_user (provider, provider_user_id, delete_yn)} 때문에
     * 같은 소셜 계정으로 다시 가입할 때 INSERT 가 중복 키로 실패하고, 연결 INSERT 가
     * 가입 트랜잭션 안에 있어 <b>가입 전체가 롤백</b>된다(코드 리뷰 2026-07-30 지적).
     *
     * <p>표시만 내리는 방식({@code use_yn='N'})으로는 이 문제가 해결되지 않는다 —
     * 조회에서는 빠지지만 UNIQUE 키에는 그대로 잡히기 때문이다.
     */
    private void deleteOauthLinks(String memberId) {
        int removed = lifecycleMapper.deleteOauth(memberId);
        if (removed > 0) {
            log.info("소셜 연결 삭제 member={} {}건", memberId, removed);
        }
    }

    /**
     * 작성한 글·댓글의 작성자명을 익명 표기로 바꾼다.
     *
     * <p>글은 지우지 않는다 — 대화의 맥락이 통째로 사라지면 남은 사람들의 글이
     * 읽히지 않는다. 지워야 하는 것은 <b>누가 썼는지</b>이지 <b>무엇을 썼는지</b>가 아니다.
     *
     * <p>탈퇴 트랜잭션 안에서 함께 처리한다. 별도 배치로 미루면 그 사이에 실명이
     * 노출된 채로 남고, 배치가 실패하면 영영 남는다.
     */
    private void anonymizeWritings(String memberId) {
        int articles = lifecycleMapper.anonymizeArticles(memberId, anonymousWriterName);
        int comments = lifecycleMapper.anonymizeComments(memberId, anonymousWriterName);
        if (articles > 0 || comments > 0) {
            log.info("작성자 익명화 member={} 글={}건 댓글={}건", memberId, articles, comments);
        }
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void moveToDormant(String memberId, String reason) {
        int copied = lifecycleMapper.copyToDormant(memberId, reason);
        if (copied == 0) {
            throw new IllegalArgumentException("휴면 대상 회원을 찾을 수 없습니다.");
        }
        // 원본 행은 감추기만 한다(delete_yn='Y') — 하드 삭제는 동의·비밀번호 이력의 FK 에
        // 막힌다. vw_user_login 이 delete_yn 을 거르므로 로그인은 차단된다.
        lifecycleMapper.softDeleteMemberRow(memberId);
        lifecycleMapper.deleteNoticesByMember(memberId);
        log.info("휴면 전환 member={} reason={}", memberId, reason);
    }

    private String actor() {
        String current = AuditorContext.currentUserId();
        return current == null || current.isBlank() ? "SYSTEM" : current;
    }
}
