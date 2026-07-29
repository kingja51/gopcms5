package com.gonet.scheduler;

import com.gonet.config.retention.RetentionProperties;
import com.gonet.logging.retention.service.LogRetentionService;
import com.gonet.logging.retention.service.LogRetentionService.PurgeResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 로그 보존기간 파기 배치 (P10-7).
 *
 * <p>보존기간이 <b>대상마다 다르다</b> — 개인정보 접근·파기 이력은 5년, 나머지 로그는
 * 36개월, 통계는 영구다. 값은 전부 {@code gopcms.retention.*} 에 있고 이 잡은 결과만
 * 요약한다.
 *
 * <p><b>기본이 dry-run</b>이다(파일 정리·회원 생명주기 배치와 같은 방식). 배치를 처음
 * 켜는 순간 몇 년치 로그가 한꺼번에 사라지는 것이 가장 흔한 사고라, 운영자가 건수를
 * 확인하고 명시적으로 꺼야 삭제가 시작된다.
 *
 * <p>1회 상한이 있어 오래된 것부터 조금씩 지운다. 남은 것은 다음 회차가 이어받으므로
 * 상한에 걸렸다는 사실을 요약 로그에 반드시 남긴다 — 안 남기면 "다 지워졌겠지" 라고
 * 믿게 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogRetentionJob {

    private final LogRetentionService retentionService;
    private final RetentionProperties properties;

    @Scheduled(cron = "${gopcms.retention.purge.cron:0 20 4 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "logRetentionJob", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void purge() {
        if (!properties.getPurge().isEnabled()) {
            log.info("로그 파기 배치 — 비활성(gopcms.retention.purge.enabled=false)");
            return;
        }
        try {
            summarize(retentionService.purgeAll());
        } catch (RuntimeException e) {
            // 배치가 죽어도 애플리케이션은 계속 떠 있어야 한다. 다음 회차에 재시도된다.
            log.error("로그 파기 배치 실패: {}", e.toString(), e);
        }
    }

    /**
     * 요약 — 테이블마다 <b>어떤 보존기간이 적용됐는지</b>까지 남긴다.
     *
     * <p>5년과 36개월이 한 배치에 섞여 있어서, 건수만 찍으면 나중에 "이 테이블은 왜
     * 아직 남아 있나" 를 로그로 답할 수 없다.
     */
    private void summarize(List<PurgeResult> results) {
        boolean dryRun = properties.getPurge().isDryRun();
        int totalExpired = 0;
        int totalDeleted = 0;
        int failed = 0;

        for (PurgeResult r : results) {
            totalExpired += r.expired();
            totalDeleted += r.deleted();
            if (r.failed()) {
                failed++;
                continue;
            }
            if (r.expired() == 0) {
                // 0건도 남긴다 — 아무것도 안 찍히면 "돌았는데 0건" 과 "안 돌았다" 가 같아 보인다
                log.info("  · {} — 보존 {}개월, 대상 없음", r.table(), r.months());
                continue;
            }
            log.info("  · {} — 보존 {}개월, 대상 {}건{}{}",
                    r.table(), r.months(), r.expired(),
                    dryRun ? " (dry-run: 지우지 않음)" : " / 삭제 " + r.deleted() + "건",
                    r.truncated() ? " ※ 상한에 걸려 남음 — 다음 회차가 이어서 지웁니다" : "");
        }

        log.info("로그 파기 배치 — 대상 {}건 / 삭제 {}건 / 실패 {}개 테이블 (상한 {}){}",
                totalExpired, totalDeleted, failed, properties.getPurge().getBatchSize(),
                dryRun ? " — dry-run: 실제로 지우지 않았습니다."
                        + " gopcms.retention.purge.dry-run=false 로 바꾸면 처리합니다." : "");
    }
}
