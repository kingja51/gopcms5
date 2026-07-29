package com.gonet.scheduler;

import com.gonet.primary.file.service.FilePurgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 백신 재검사 배치 — 결과가 확정되지 않은 채 오래 머문 파일을 다시 큐에 넣는다.
 *
 * <p>스캐너가 죽었거나 응답을 받지 못한 파일이 {@code PENDING}·{@code ERROR} 로 쌓인다.
 * 그대로 두면 다운로드가 계속 막히거나(ERROR), 검사받지 않은 채 열린다(PENDING).
 *
 * <p><b>백신을 켰을 때만 빈이 만들어진다.</b> 미연동 구성에서는 모든 파일이 영원히
 * PENDING 이라 재검사가 의미 없고, 도는 것만으로 로그와 DB 조회를 낭비한다.
 */
@Component
@ConditionalOnProperty(name = "gopcms.file.clamav.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class VirusScanRetryJob {

    private final FilePurgeService filePurgeService;

    @Scheduled(cron = "${gopcms.file.rescan.cron:0 */10 * * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "virusScanRetryJob", lockAtLeastFor = "PT30S", lockAtMostFor = "PT10M")
    public void retry() {
        try {
            filePurgeService.requeueStaleScans();
        } catch (RuntimeException e) {
            log.error("백신 재검사 배치 실패: {}", e.toString(), e);
        }
    }
}
