package com.gonet.config.datasource;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * DB 별 Flyway 3개 빈 — doc/flyway-migration.md §3.
 *
 * <p>Boot 자동구성은 단일 DataSource 전제라 사용 불가({@code spring.flyway.enabled: false}).
 * 각 DB 가 자체 {@code flyway_schema_history} 를 가지며 버전은 폴더별 독립 채번.
 * 실행 순서: primary → secondary → logging ({@code @DependsOn} + initMethod=migrate).
 *
 * <p>dev 시드(db/devdata/primary, V900+)는 {@code gopcms.flyway.devdata=true}(local/dev
 * 프로파일)일 때만 primary 위치에 추가된다.
 */
@Configuration
public class FlywayConfig {

    @Value("${gopcms.flyway.devdata:false}")
    private boolean devdata;

    @Bean(initMethod = "migrate")
    public Flyway primaryFlyway(@Qualifier("primaryDataSource") DataSource dataSource) {
        return build(dataSource, "primary", devdata);
    }

    @Bean(initMethod = "migrate")
    @DependsOn("primaryFlyway")
    public Flyway secondaryFlyway(@Qualifier("secondaryDataSource") DataSource dataSource) {
        return build(dataSource, "secondary", false);
    }

    @Bean(initMethod = "migrate")
    @DependsOn("secondaryFlyway")
    public Flyway loggingFlyway(@Qualifier("loggingDataSource") DataSource dataSource) {
        return build(dataSource, "logging", false);
    }

    private Flyway build(DataSource dataSource, String db, boolean includeDevdata) {
        String vendor = DbVendor.ofDataSource(dataSource).migrationFolder();
        List<String> locations = new ArrayList<>();
        locations.add("classpath:db/migration/%s/%s".formatted(db, vendor));
        if (includeDevdata) {
            locations.add("classpath:db/devdata/" + db);
        }
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(locations.toArray(String[]::new))
                .validateOnMigrate(true)
                // 우리는 Flyway 플레이스홀더를 쓰지 않는다. 켜 두면 SQL 안의 ${…} 를 전부
                // 치환 대상으로 보는데, 메일 템플릿 본문의 Thymeleaf 식(${siteName})이
                // 그대로 걸려 마이그레이션이 실패한다 (V910 실측).
                .placeholderReplacement(false)
                .cleanDisabled(true) // 운영 사고 방지 — 전 프로파일 고정 (로컬 리셋은 DROP DATABASE)
                .failOnMissingLocations(false) // secondary/logging 은 마이그레이션 0건 허용 (README 만 존재)
                // devdata(V900+) 가 스키마 버전(V4~)보다 먼저 이력에 남으므로, devdata 를
                // 포함하는 local/dev 만 out-of-order 허용 — 운영(devdata 미포함)은 엄격 순차 유지.
                // (P5 실측: V900 적용 후 V4 추가 시 "out of order" 로 기동 실패)
                .outOfOrder(includeDevdata)
                .load();
    }
}
