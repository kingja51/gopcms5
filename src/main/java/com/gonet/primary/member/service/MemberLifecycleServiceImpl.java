package com.gonet.primary.member.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.datasource.MyBatisConfig;
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

    /** 탈퇴 원장 보존기간(개월) — 사용자 확정 36개월(PLAN §P10-7). */
    @Value("${gopcms.retention.withdraw-months:36}")
    private int withdrawRetentionMonths;

    private final MemberLifecycleMapper lifecycleMapper;

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void withdraw(String memberId, String reason, String withdrawType) {
        String actor = actor();
        LocalDateTime retentionExpireAt =
                LocalDateTime.now().plusMonths(withdrawRetentionMonths);

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
            // ② 휴면 원본의 PII 파기
            lifecycleMapper.nullifyDormantPii(memberId);
            log.info("탈퇴 처리(휴면 경유) member={} retentionExpireAt={}",
                    memberId, retentionExpireAt);
            return;
        }
        // ② 활성 원본의 PII 파기
        lifecycleMapper.nullifyPii(memberId, actor);
        log.info("탈퇴 처리 member={} reason={} retentionExpireAt={}",
                memberId, reason, retentionExpireAt);
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
