package com.gonet.scheduler;

import com.gonet.primary.file.service.FilePurgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 파일 정리 배치 — 보존기간이 지난 삭제분의 물리 제거 + 고아 그룹 회수.
 *
 * <p>기본 스케줄은 새벽 4시다. 되돌릴 수 없는 작업이므로 <b>기본값은 dry-run</b>
 * ({@code gopcms.file.purge.dry-run: true}) — 스케줄은 돌지만 대상만 로그에 남고
 * 실제로 지우지 않는다. 운영자가 로그를 확인한 뒤 명시적으로 꺼야 삭제가 시작된다.
 * 배치를 처음 켜는 순간 오래된 파일이 한꺼번에 사라지는 사고를 막으려는 것이다.
 *
 * <p>{@code lockAtLeastFor} 를 두는 이유: 잡이 아주 빨리 끝나면(대상 0건) 락이 즉시
 * 풀려 다른 인스턴스가 곧바로 같은 잡을 돌 수 있다. 최소 보유 시간으로 그 창을 없앤다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FilePurgeJob {

    private final FilePurgeService filePurgeService;

    @Scheduled(cron = "${gopcms.file.purge.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "filePurgeJob", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void purge() {
        try {
            filePurgeService.purge();          // 결과 요약 로그는 서비스가 남긴다
        } catch (RuntimeException e) {
            // 배치가 죽어도 애플리케이션은 계속 떠 있어야 한다. 다음 회차에 재시도된다.
            log.error("파일 정리 배치 실패: {}", e.toString(), e);
        }
    }
}
