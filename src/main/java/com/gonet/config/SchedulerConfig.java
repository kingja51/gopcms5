package com.gonet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 배치 스케줄 활성화 — 잡은 {@code com.gonet.scheduler} 패키지.
 *
 * <p>개별 잡의 cron 은 프로퍼티로 주입하고 기본값을 {@code -}(비활성)로 둔다 —
 * 운영에서 켤 잡만 명시적으로 켠다. 다중 인스턴스 배포 시 ShedLock 적용이 선행돼야 한다.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
