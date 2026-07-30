package com.gonet.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Arrays;
import javax.sql.DataSource;

/**
 * DB 벤더 — 마이그레이션 폴더명·매퍼 XML 접미어·JDBC URL 접두어를 <b>한 자리에</b> 묶는다.
 *
 * <p>같은 "벤더" 를 표기법 셋으로 부른다: Flyway 는 폴더명({@code mariadb}), MyBatis 는 파일
 * 접미어({@code *_maria.xml}), 접속은 URL 접두어({@code jdbc:mariadb:}). 이 대응표가 두 군데
 * 흩어져 있으면 한쪽만 고쳤을 때 <b>조용히</b> 어긋난다 — 마이그레이션은 PostgreSQL 폴더로
 * 도는데 매퍼는 MariaDB 한 벌을 읽는 식이다.
 *
 * <p>{@link #ofDataSource} 가 실제 접속 URL 에서 벤더를 읽으므로, 설정 프로퍼티
 * ({@code gopcms.datasource.vendor})가 실제와 맞는지 기동 시 대조할 수 있다
 * ({@code MyBatisConfig.requireVendorMatch}).
 */
public enum DbVendor {

    MARIA("maria", "mariadb", "jdbc:mariadb:"),
    POSTGRES("postgres", "postgresql", "jdbc:postgresql:");

    private final String mapperSuffix;
    private final String migrationFolder;
    private final String jdbcPrefix;

    DbVendor(String mapperSuffix, String migrationFolder, String jdbcPrefix) {
        this.mapperSuffix = mapperSuffix;
        this.migrationFolder = migrationFolder;
        this.jdbcPrefix = jdbcPrefix;
    }

    /** 매퍼 XML 접미어 — {@code SiteMapper_maria.xml} 의 {@code maria}. */
    public String mapperSuffix() {
        return mapperSuffix;
    }

    /** 마이그레이션 폴더명 — {@code db/migration/primary/mariadb}. */
    public String migrationFolder() {
        return migrationFolder;
    }

    /** {@code gopcms.datasource.vendor} 값 → 벤더. */
    public static DbVendor ofMapperSuffix(String value) {
        return Arrays.stream(values())
                .filter(v -> v.mapperSuffix.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("""
                        지원하지 않는 gopcms.datasource.vendor '%s' — maria | postgres 만 쓴다.
                        오타를 그대로 두면 매퍼 XML 을 한 장도 못 찾은 채 기동해, 첫 질의에서야
                        드러난다.""".formatted(value)));
    }

    /** 실제 접속 URL → 벤더. */
    public static DbVendor ofDataSource(DataSource dataSource) {
        String url = dataSource instanceof HikariDataSource hikari ? hikari.getJdbcUrl() : "";
        return Arrays.stream(values())
                .filter(v -> url != null && url.startsWith(v.jdbcPrefix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "지원하지 않는 JDBC URL (mariadb/postgresql 만): " + url));
    }
}
