package com.gonet.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 배치 스케줄 활성화 — 잡은 {@code com.gonet.scheduler} 패키지.
 *
 * <p>개별 잡의 cron 은 프로퍼티로 주입하고, 되돌릴 수 없는 잡은 기본값을
 * {@code -}(비활성)로 둔다 — 운영에서 켤 잡만 명시적으로 켠다.
 *
 * <h3>ShedLock — 다중 인스턴스에서 한 번만 돌게</h3>
 * 잡이 인스턴스마다 도는 것은 잡의 성격에 따라 결과가 다르다. 멱등한 잡(역할 재전개)은
 * 낭비로 끝나지만, <b>파일 물리 삭제처럼 되돌릴 수 없는 잡은 동시 실행이 곧 사고</b>다.
 * 그래서 락을 앱 밖(DB)에 둔다.
 *
 * <p>락 테이블은 <b>logging_db</b> 에 있다. 업무 데이터(primary)와 섞지 않는 것은,
 * 락이 업무 트랜잭션과 같은 커넥션 풀·트랜잭션 경계를 공유하면 잡이 길어질 때
 * 업무 쪽을 굶길 수 있기 때문이다.
 *
 * <p>{@code defaultLockAtMostFor} 는 <b>안전망</b>이다 — 잡이 죽거나 인스턴스가 내려가
 * 락을 반납하지 못했을 때 그 시간이 지나면 강제로 풀린다. 잡의 정상 소요시간보다
 * 넉넉해야 하고(중복 실행 방지), 무한히 길면 안 된다(영구 정지 방지).
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(@Qualifier("loggingDataSource") DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .withTableName("shedlock")
                        // 락 시각은 DB 것을 쓴다 — 인스턴스 간 시계가 어긋나면
                        // 락이 예정보다 일찍 풀려 중복 실행이 난다
                        .usingDbTime()
                        .build());
    }
}
