package com.gonet.scheduler;

import com.gonet.primary.member.dto.MemberLifecycleTarget;
import com.gonet.primary.member.mapper.MemberLifecycleMapper;
import com.gonet.primary.member.service.MemberLifecycleWorker;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 회원 생명주기 배치 — 휴면 · 탈퇴 · 완전삭제 <b>3개 잡을 분리</b>한다.
 *
 * <pre>
 * ACTIVE ──[마지막 로그인 +1년]──▶ 휴면 ──[+1년]──▶ 탈퇴 ──[+1년]──▶ 완전 삭제
 * </pre>
 *
 * <p>하나로 묶지 않는 이유: 셋의 위험도가 다르다. 휴면은 되돌릴 수 있고(P10-5 복원),
 * 탈퇴는 PII 가 사라지며, 완전 삭제는 행 자체가 없어진다. 따로 켜고 끌 수 있어야 한다.
 *
 * <p><b>기본이 dry-run</b> — 스케줄은 돌지만 대상만 로그에 남는다. 배치를 처음 켜는 순간
 * 오래된 계정이 한꺼번에 사라지는 것이 가장 흔한 사고라, 운영자가 로그로 대상을 확인하고
 * 명시적으로 꺼야 실제 처리가 시작된다(P8-5 파일 정리 배치와 같은 방식).
 *
 * <p>1회 처리 건수에 상한을 둔다. 되돌릴 수 없는 배치에는 상한이 있어야 한다 —
 * 잘못 돌아도 피해가 한 배치로 제한된다.
 *
 * <p>{@code logging_db} 는 건드리지 않는다. 크로스 DB 이고 보존주기가 별개다(PLAN §P10-7).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberLifecycleJob {

    @Value("${gopcms.member.lifecycle.dry-run:true}")
    private boolean dryRun;

    @Value("${gopcms.member.lifecycle.batch-size:200}")
    private int batchSize;

    /** 휴면 전환 기준(일) — 마지막 로그인(없으면 가입일)로부터. */
    @Value("${gopcms.member.lifecycle.dormant-days:365}")
    private int dormantDays;

    /** 탈퇴 전환 기준(일) — 휴면 전환일로부터. */
    @Value("${gopcms.member.lifecycle.withdraw-days:365}")
    private int withdrawDays;

    private final MemberLifecycleMapper lifecycleMapper;
    private final MemberLifecycleWorker worker;

    /**
     * ① 사전 안내 — 휴면 30일 / 7일 / 1일 전.
     *
     * <p>전환보다 먼저 돌려야 의미가 있다. 같은 날 전환과 안내가 같이 돌면 안내를 받은
     * 그날 휴면이 되는 계정이 생긴다.
     */
    @Scheduled(cron = "${gopcms.member.lifecycle.notice-cron:0 10 3 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "memberDormantNoticeJob", lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT30M")
    public void sendDormantNotices() {
        notice("30D", 30, "ACCOUNT_DORMANT_NOTICE_30D");
        notice("7D", 7, "ACCOUNT_DORMANT_NOTICE_7D");
        notice("1D", 1, "ACCOUNT_DORMANT_NOTICE_1D");
    }

    /** ② 휴면 전환. */
    @Scheduled(cron = "${gopcms.member.lifecycle.dormant-cron:0 30 3 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "memberDormantJob", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void transferToDormant() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(dormantDays);
        List<MemberLifecycleTarget> targets =
                lifecycleMapper.findDormantTargets(cutoff, batchSize);
        if (targets.isEmpty()) {
            // 침묵하지 않는다 — 관리자 화면의 수동 실행은 로그가 유일한 확인 수단이라
            // 아무것도 안 찍히면 "돌았는데 0건" 과 "안 돌았다" 를 구분할 수 없다
            log.info("[휴면 전환] 대상 없음 (기준 {} 이전 미접속)", cutoff.toLocalDate());
            return;
        }
        if (dryRun) {
            log.info("[휴면 전환 dry-run] 대상 {}건 (기준 {} 이전 미접속) — 실제 처리 안 함. "
                    + "gopcms.member.lifecycle.dry-run=false 로 바꾸면 처리합니다.",
                    targets.size(), cutoff.toLocalDate());
            targets.forEach(t -> log.info("  · member={} site={} 마지막활동={}",
                    t.getMemberId(), t.getSiteCode(), t.getBaseAt()));
            return;
        }
        int done = 0;
        for (MemberLifecycleTarget target : targets) {
            if (worker.dormantOne(target)) {
                done++;
            }
        }
        log.info("휴면 전환 완료 {}/{}건 (상한 {})", done, targets.size(), batchSize);
    }

    /** ③ 탈퇴 전환 — PII 가 사라진다. */
    @Scheduled(cron = "${gopcms.member.lifecycle.withdraw-cron:0 40 3 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "memberWithdrawJob", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void transferToWithdraw() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(withdrawDays);
        List<MemberLifecycleTarget> targets =
                lifecycleMapper.findWithdrawTargets(cutoff, batchSize);
        if (targets.isEmpty()) {
            log.info("[탈퇴 전환] 대상 없음 (휴면 {} 이전)", cutoff.toLocalDate());
            return;
        }
        if (dryRun) {
            log.info("[탈퇴 전환 dry-run] 대상 {}건 (휴면 {} 이전) — 실제 처리 안 함",
                    targets.size(), cutoff.toLocalDate());
            targets.forEach(t -> log.info("  · member={} 휴면전환={}",
                    t.getMemberId(), t.getBaseAt()));
            return;
        }
        int done = 0;
        for (MemberLifecycleTarget target : targets) {
            if (worker.withdrawOne(target)) {
                done++;
            }
        }
        log.warn("탈퇴 전환 완료 {}/{}건 — 개인정보가 파기되었습니다", done, targets.size());
    }

    /** ④ 완전 삭제 — 행 자체가 없어진다. */
    @Scheduled(cron = "${gopcms.member.lifecycle.purge-cron:0 50 3 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "memberPurgeJob", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void purgeWithdrawn() {
        List<String> targets = lifecycleMapper.findPurgeTargets(LocalDateTime.now(), batchSize);
        if (targets.isEmpty()) {
            log.info("[완전 삭제] 보존기한 경과 대상 없음");
            return;
        }
        if (dryRun) {
            log.info("[완전 삭제 dry-run] 보존기한 경과 {}건 — 실제 처리 안 함", targets.size());
            targets.forEach(id -> log.info("  · member={}", id));
            return;
        }
        int done = 0;
        for (String memberId : targets) {
            if (worker.purgeOne(memberId)) {
                done++;
            }
        }
        log.warn("완전 삭제 완료 {}/{}건 — 되돌릴 수 없습니다", done, targets.size());
    }

    /** 사전 안내 한 단계 — 휴면까지 {@code daysBefore} 일 남은 구간을 하루치로 자른다. */
    private void notice(String stage, int daysBefore, String templateCode) {
        LocalDateTime base = LocalDateTime.now().minusDays(dormantDays - (long) daysBefore);
        LocalDateTime from = base.minusDays(1);
        List<MemberLifecycleTarget> targets =
                lifecycleMapper.findNoticeTargets(from, base, stage, batchSize);
        if (targets.isEmpty()) {
            log.info("[휴면 안내] stage={} 대상 없음", stage);
            return;
        }
        if (dryRun) {
            log.info("[휴면 안내 dry-run] stage={} 대상 {}건 — 발송 안 함", stage, targets.size());
            return;
        }
        int sent = 0;
        for (MemberLifecycleTarget target : targets) {
            if (worker.noticeOne(target, stage, templateCode)) {
                sent++;
            }
        }
        log.info("휴면 사전 안내 stage={} {}건 발송", stage, sent);
    }
}
