package com.gonet.primary.member.service;

import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.logging.purge.dto.PiiPurgeLog;
import com.gonet.logging.purge.service.PiiPurgeLogService;
import com.gonet.primary.mail.service.MailService;
import com.gonet.primary.member.dto.MemberLifecycleTarget;
import com.gonet.primary.member.mapper.MemberLifecycleMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 생명주기 배치의 <b>단건 처리</b> — 잡과 별도 빈이다.
 *
 * <p>같은 빈에 두면 자기호출로 프록시를 우회해 {@code @Transactional} 이 통째로 무시된다
 * (CLAUDE.md 트랜잭션 함정). 그러면 배치 전체가 한 트랜잭션처럼 묶여, 한 건이 실패할 때
 * 앞서 처리한 수백 건이 함께 롤백된다.
 *
 * <p>{@code REQUIRES_NEW} 로 건마다 독립 커밋한다 — 1건 실패가 나머지를 막지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberLifecycleWorker {

    /** 완전 삭제가 손대는 테이블 — 파기 범위의 증빙이라 실제 순서와 같아야 한다. */
    private static final String PURGED_TABLES =
            "tb_member_consent,tb_member_password_history,tb_member_oauth,"
            + "tb_member_dormant_notice,tb_member_dormant,tb_member,tb_member_withdraw";

    private final MemberLifecycleService lifecycleService;
    private final PiiPurgeLogService piiPurgeLogService;
    private final MemberLifecycleMapper lifecycleMapper;
    private final MailService mailService;

    /** 휴면 전환 1건 — 실패하면 로그만 남기고 다음으로 넘어간다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            transactionManager = MyBatisConfig.PRIMARY_TX)
    public boolean dormantOne(MemberLifecycleTarget target) {
        try {
            lifecycleService.moveToDormant(target.getMemberId(), "장기 미접속(1년)");
            sendMail("ACCOUNT_DORMANT", target);
            return true;
        } catch (RuntimeException e) {
            log.error("휴면 전환 실패 member={} : {}", target.getMemberId(), e.toString());
            return false;
        }
    }

    /** 탈퇴 전환 1건. */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            transactionManager = MyBatisConfig.PRIMARY_TX)
    public boolean withdrawOne(MemberLifecycleTarget target) {
        try {
            // 메일을 먼저 보낸다 — PII 를 지우고 나면 보낼 주소가 없다.
            // 전용 템플릿을 쓴다(V921): 휴면 전환 문구를 재사용하면 "복원 링크로 다시
            // 쓸 수 있다" 는 사실과 다른 안내가 나간다 — 탈퇴는 되돌릴 수 없다.
            sendMail("ACCOUNT_WITHDRAW_NOTICE", target);
            lifecycleService.withdraw(target.getMemberId(), "휴면 1년 경과 자동 탈퇴",
                    "DORMANT_EXPIRED");
            return true;
        } catch (RuntimeException e) {
            log.error("탈퇴 전환 실패 member={} : {}", target.getMemberId(), e.toString());
            return false;
        }
    }

    /**
     * 완전 삭제 1건 — 자식 행부터 순서대로.
     *
     * <p>FK 를 가진 쪽을 먼저 지운다. 순서가 틀리면 제약 위반으로 실패하는데, 그건
     * 차라리 낫다 — 조용히 일부만 지워지는 것보다 낫기 때문이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            transactionManager = MyBatisConfig.PRIMARY_TX)
    public boolean purgeOne(String memberId) {
        try {
            // 이력을 먼저 남긴다 — 행이 사라지고 나면 무엇을 지웠는지 적을 근거가 없다.
            // logging_db 는 크로스 DB 라 이 트랜잭션에 묶이지 않으므로(REQUIRES_NEW),
            // 순서가 유일한 안전장치다.
            piiPurgeLogService.writeMemberPurge(memberId,
                    PiiPurgeLog.REASON_RETENTION_EXPIRED, PURGED_TABLES);
            lifecycleMapper.deleteConsents(memberId);
            lifecycleMapper.deletePasswordHistory(memberId);
            lifecycleMapper.deleteOauth(memberId);
            lifecycleMapper.deleteDormantNotices(memberId);
            lifecycleMapper.deleteDormantRow(memberId);
            lifecycleMapper.deleteMemberRow(memberId);
            // 원장은 마지막 — 여기까지 왔다는 것은 자식이 모두 정리됐다는 뜻이다
            lifecycleMapper.deleteWithdrawLedger(memberId);
            return true;
        } catch (RuntimeException e) {
            log.error("완전 삭제 실패 member={} : {}", memberId, e.toString());
            return false;
        }
    }

    /** 사전 안내 1건 — 이력 UNIQUE 가 중복 발송을 막는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            transactionManager = MyBatisConfig.PRIMARY_TX)
    public boolean noticeOne(MemberLifecycleTarget target, String stage, String templateCode) {
        try {
            // 이력을 먼저 적는다 — 발송이 비동기라 메일을 먼저 걸면 중복 판정을 못 한다
            lifecycleMapper.insertNotice(Uid.next(UidPrefix.MDN),
                    target.getMemberId(), stage);
            sendMail(templateCode, target);
            return true;
        } catch (RuntimeException e) {
            // UNIQUE 충돌 = 이미 보낸 단계. 오류가 아니라 정상 흐름이다
            log.debug("사전 안내 건너뜀 member={} stage={} : {}",
                    target.getMemberId(), stage, e.getMessage());
            return false;
        }
    }

    private void sendMail(String templateCode, MemberLifecycleTarget target) {
        if (target.getEmail() == null || target.getEmail().isBlank()) {
            return;
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("memberName", target.getMemberName());
        model.put("loginId", target.getLoginId());
        model.put("siteCode", target.getSiteCode());
        model.put("sentAt", java.time.LocalDateTime.now());
        mailService.sendAsync(templateCode, target.getEmail(), model);
    }
}
