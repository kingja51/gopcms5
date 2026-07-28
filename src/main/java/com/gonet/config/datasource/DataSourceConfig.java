package com.gonet.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 3-DB DataSource 구성 (conventions.md §3).
 *
 * <p>primary_db(GOPCMS 기본, tb_*) / secondary_db(클라이언트, tn_*) / logging_db(로그·통계,
 * log_*·stat_*). 접속값은 application.yml 의 {@code gopcms.datasource.*} — 환경변수 주입,
 * password 는 기본값 없음(fail-fast).
 *
 * <p>커스텀 DataSource 빈이 존재하므로 Boot 의 단일 DataSource 자동구성은 스스로 물러난다.
 * TxManager·SqlSessionFactory·MapperConfigurer 는 P1 에서 DB 별로 추가된다.
 */
@Configuration
public class DataSourceConfig {

    /* ── primary_db ─────────────────────────────────────────────────────── */

    @Bean
    @Primary
    @ConfigurationProperties("gopcms.datasource.primary")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("gopcms.datasource.primary.hikari")
    public HikariDataSource primaryDataSource() {
        return primaryDataSourceProperties()
                .initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /* ── secondary_db ───────────────────────────────────────────────────── */

    @Bean
    @ConfigurationProperties("gopcms.datasource.secondary")
    public DataSourceProperties secondaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("gopcms.datasource.secondary.hikari")
    public HikariDataSource secondaryDataSource() {
        return secondaryDataSourceProperties()
                .initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /* ── logging_db ─────────────────────────────────────────────────────── */

    @Bean
    @ConfigurationProperties("gopcms.datasource.logging")
    public DataSourceProperties loggingDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("gopcms.datasource.logging.hikari")
    public HikariDataSource loggingDataSource() {
        return loggingDataSourceProperties()
                .initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}
