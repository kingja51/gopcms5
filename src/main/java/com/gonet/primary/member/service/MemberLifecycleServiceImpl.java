package com.gonet.primary.member.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.config.retention.RetentionProperties;
import com.gonet.logging.purge.dto.PiiPurgeLog;
import com.gonet.logging.purge.service.PiiPurgeLogService;
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

        // ① 원장 먼저 — PII NULL 은 되돌릴 수 없으므로 근거를 남기고 지운다
        int ledger = lifecycleMapper.insertWithdrawLedger(memberId, reason, withdrawType,
                retentionExpireAt, "정보통신망법 제29조", actor, AuditorContext.currentIp());
        if (ledger == 0) {
            // 활성 회원이 아니면 휴면 테이블에서 찾는다(배치 탈퇴 전환 경로)
            ledger = lifecycleMapper.insertWithdrawLedgerFromDormant(memberId, reason,
                    withdrawType, retentionExpireAt, "정보통신망법 제29조", actor,
                    AuditorContext.currentIp());
            if (ledger == 0) {
                throw new IllegalArgumentException("탈퇴 대상 회원을 찾을 수 없습니다.");
            }
            // ② 파기 이력을 <b>먼저</b> — 크로스 DB 라 한 트랜잭션으로 못 묶는다.
            //    뒤에 남기면 파기가 성공하고 이력만 실패했을 때 흔적 없는 삭제가 된다.
            piiPurgeLogService.writeMemberPurge(memberId,
                    PiiPurgeLog.REASON_WITHDRAW, "tb_member_dormant");
            // ③ 휴면 원본의 PII 파기
            lifecycleMapper.nullifyDormantPii(memberId);
            anonymizeWritings(memberId);
            log.info("탈퇴 처리(휴면 경유) member={} retentionExpireAt={}",
                    memberId, retentionExpireAt);
            return;
        }
        // ② 파기 이력을 먼저(위와 같은 이유)
        piiPurgeLogService.writeMemberPurge(memberId,
                PiiPurgeLog.REASON_WITHDRAW, "tb_member");
        // ③ 활성 원본의 PII 파기
        lifecycleMapper.nullifyPii(memberId, actor);
        anonymizeWritings(memberId);
        log.info("탈퇴 처리 member={} reason={} retentionExpireAt={}",
                memberId, reason, retentionExpireAt);
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
