/**
 * 3-DB 데이터 계층 구성 (conventions.md §3) — primary(tb_*) / secondary(tn_*) /
 * logging(log_*·stat_*) 각각 DataSource·TxManager·SqlSessionFactory·MapperConfigurer·Flyway 빈.
 *
 * <p>TxManager 빈 이름 상수는 {@link com.gonet.config.datasource.MyBatisConfig} 의
 * PRIMARY_TX / SECONDARY_TX / LOGGING_TX — 서비스의 @Transactional 이 참조하는 유일한 출처.
 */
package com.gonet.config.datasource;
