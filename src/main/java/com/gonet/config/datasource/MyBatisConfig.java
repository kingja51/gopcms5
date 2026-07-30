package com.gonet.config.datasource;

import com.gonet.common.audit.AuditInterceptor;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.egovframe.rte.psl.dataaccess.mapper.MapperConfigurer;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 3-DB MyBatis 구성 — DB 별 SqlSessionFactory · TxManager · eGov MapperConfigurer.
 *
 * <p>eGov 데이터 액세스 규칙(호환성 가이드 p.7): Mapper 인터페이스는
 * {@code @EgovMapper} + 표준프레임워크 {@code MapperConfigurer} 로 스캔한다
 * (mybatis-spring-boot 자동 {@code @Mapper} 스캔은 사용하지 않음 — 커스텀
 * SqlSessionFactory 존재로 자동구성은 스스로 물러난다).
 *
 * <p>규약 (conventions.md §3):
 * <ul>
 *   <li>TxManager 빈 이름 = primary/secondary/loggingTransactionManager — 서비스의
 *       {@code @Transactional(transactionManager=…)} 가 참조하는 고정 상수</li>
 *   <li>매퍼 XML 은 인터페이스 옆 콜로케이션 + <b>벤더 접미어</b>
 *       ({@code com/gonet/{db}/**&#47;mapper/*_maria.xml}) — pom 의 src/main/java
 *       리소스 필터가 xml 을 복사</li>
 *   <li>로드 대상은 {@code gopcms.datasource.vendor}(기본 maria) 가 고른 <b>한 벌뿐</b>이다.
 *       두 벌을 동시에 읽으면 같은 namespace 가 중복 등록돼 기동이 깨진다</li>
 *   <li>같은 벤더 안의 소소한 분기는 파일을 더 만들지 말고 databaseId 로 처리</li>
 * </ul>
 *
 * <h3>기동 시 두 가지를 강제한다</h3>
 * <ol>
 *   <li><b>벤더 일관성</b> — 매퍼 선택은 전역 프로퍼티 하나인데 DataSource 는 3개다. 셋 중
 *       하나만 다른 벤더를 보고 있으면 그 DB 에는 <b>남의 벤더 SQL</b> 이 실린다. Flyway 는
 *       DataSource 별 URL 로 폴더를 고르므로(FlywayConfig), 마이그레이션과 매퍼가 서로 다른
 *       벤더를 보는 상태도 가능하다. 그래서 각 DataSource 의 실제 URL 과 대조한다</li>
 *   <li><b>매퍼 XML 0건 차단</b> — 벤더 오타나 리소스 필터 누락이면 XML 을 한 장도 못 찾는데,
 *       {@code SqlSessionFactory} 는 그래도 만들어진다. 기동은 성공하고 첫 질의에서
 *       "statement not found" 로 터진다 — 그 사이가 조용한 장애 구간이다</li>
 * </ol>
 */
@Configuration
@EnableTransactionManagement
public class MyBatisConfig {

    /** 매퍼 XML 벤더 접미어 — maria(기본) / postgres. 파일명 {@code *Mapper_{vendor}.xml} 과 1:1. */
    @Value("${gopcms.datasource.vendor:maria}")
    private String vendor;

    public static final String PRIMARY_TX = "primaryTransactionManager";
    public static final String SECONDARY_TX = "secondaryTransactionManager";
    public static final String LOGGING_TX = "loggingTransactionManager";

    /* ── primary_db ─────────────────────────────────────────────────────── */

    @Bean
    @Primary
    public SqlSessionFactory primarySqlSessionFactory(
            @Qualifier("primaryDataSource") DataSource dataSource) throws Exception {
        return buildFactory(dataSource, "primary");
    }

    @Bean(PRIMARY_TX)
    @Primary
    public DataSourceTransactionManager primaryTransactionManager(
            @Qualifier("primaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /** static — BeanDefinitionRegistryPostProcessor 는 컨테이너 초기에 등록돼야 한다. */
    @Bean
    public static MapperConfigurer primaryMapperConfigurer() {
        return mapperConfigurer("com.gonet.primary", "primarySqlSessionFactory");
    }

    /* ── secondary_db ───────────────────────────────────────────────────── */

    @Bean
    public SqlSessionFactory secondarySqlSessionFactory(
            @Qualifier("secondaryDataSource") DataSource dataSource) throws Exception {
        return buildFactory(dataSource, "secondary");
    }

    @Bean(SECONDARY_TX)
    public DataSourceTransactionManager secondaryTransactionManager(
            @Qualifier("secondaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public static MapperConfigurer secondaryMapperConfigurer() {
        return mapperConfigurer("com.gonet.secondary", "secondarySqlSessionFactory");
    }

    /* ── logging_db ─────────────────────────────────────────────────────── */

    @Bean
    public SqlSessionFactory loggingSqlSessionFactory(
            @Qualifier("loggingDataSource") DataSource dataSource) throws Exception {
        return buildFactory(dataSource, "logging");
    }

    @Bean(LOGGING_TX)
    public DataSourceTransactionManager loggingTransactionManager(
            @Qualifier("loggingDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public static MapperConfigurer loggingMapperConfigurer() {
        return mapperConfigurer("com.gonet.logging", "loggingSqlSessionFactory");
    }

    /* ── 공통 빌더 ──────────────────────────────────────────────────────── */

    /**
     * 벤더 한 벌만 읽는다 — {@code *_maria.xml} / {@code *_postgres.xml} 를 함께 읽으면
     * 동일 namespace 중복으로 기동이 실패한다. 전환은 이 프로퍼티 하나로 끝난다.
     */
    private String mapperPattern(String db) {
        return "classpath*:com/gonet/%s/**/mapper/*_%s.xml"
                .formatted(db, DbVendor.ofMapperSuffix(vendor).mapperSuffix());
    }

    private static MapperConfigurer mapperConfigurer(String basePackage, String factoryBeanName) {
        MapperConfigurer configurer = new MapperConfigurer();
        configurer.setBasePackage(basePackage);
        configurer.setSqlSessionFactoryBeanName(factoryBeanName);
        return configurer;
    }

    /**
     * 설정 벤더와 실제 접속 벤더가 같은지 — 다르면 기동을 세운다.
     *
     * <p>DB 3개가 서로 다른 벤더를 봐도 접속 자체는 성공한다. 매퍼는 전역 프로퍼티 한 벌만
     * 읽으므로 어긋난 DB 에는 남의 벤더 문법 SQL 이 실리고, 그 사실은 <b>그 DB 를 처음 쓰는
     * 질의</b>에서야 드러난다. 벤더를 섞어 쓸 일이 생기면 프로퍼티를 DataSource 별로 쪼개야
     * 하며, 그때 이 검사도 함께 고쳐야 한다.
     */
    private void requireVendorMatch(DataSource dataSource, String db) {
        DbVendor configured = DbVendor.ofMapperSuffix(vendor);
        DbVendor actual = DbVendor.ofDataSource(dataSource);
        if (configured != actual) {
            throw new IllegalStateException("""
                    %s_db 벤더 불일치 — 기동 중단.
                      설정(gopcms.datasource.vendor) = %s  → 매퍼 %s 한 벌을 읽는다
                      실제 접속(gopcms.datasource.%s.url) = %s
                    3개 DB 는 같은 벤더여야 한다(매퍼 선택이 전역 프로퍼티 하나이기 때문)."""
                    .formatted(db, configured.mapperSuffix(), configured.mapperSuffix(),
                            db, actual.mapperSuffix()));
        }
    }

    private SqlSessionFactory buildFactory(DataSource dataSource, String db) throws Exception {
        requireVendorMatch(dataSource, db);

        String mapperPattern = mapperPattern(db);
        Resource[] mappers = new PathMatchingResourcePatternResolver().getResources(mapperPattern);
        // secondary 는 아직 매퍼가 없다(테이블 미확정) — 0건이 정상인 유일한 DB 다.
        // primary/logging 이 0건이면 벤더 오타이거나 pom 의 xml 리소스 필터가 빠진 것이다.
        if (mappers.length == 0 && !"secondary".equals(db)) {
            throw new IllegalStateException("""
                    %s_db 매퍼 XML 을 한 장도 찾지 못했다 — 기동 중단.
                      패턴: %s
                    벤더 접미어 오타이거나(gopcms.datasource.vendor), pom 의 src/main/java
                    xml 리소스 필터가 빠진 것이다. 이대로 뜨면 첫 질의에서 statement not found
                    로 터진다.""".formatted(db, mapperPattern));
        }

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(mappers);

        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true); // site_code → siteCode
        configuration.setJdbcTypeForNull(JdbcType.NULL);
        configuration.addInterceptor(new AuditInterceptor()); // 감사컬럼 자동 세팅
        /*
         * SQL 로거 이름에 접두어를 붙인다 — logback 이 SQL 만 따로 뽑아낼 수 있게 하려는 것이다.
         *
         * MyBatis 는 SQL(Preparing / Parameters / Total)을 <b>매퍼 인터페이스 FQN</b> 로거로
         * 찍는다(예: com.gonet.primary.member.mapper.MemberMapper.findById). 그래서 접두어가
         * 없으면 SQL 을 잡으려면 com.gonet 전체를 DEBUG 로 열어야 하고, 그러면 애플리케이션
         * 로그와 SQL 이 한 파일에 섞여 둘 다 읽기 어려워진다.
         *
         * 접두어를 붙이면 로거가 gopcms.sql.com.gonet…MemberMapper.findById 가 되어
         * logback 의 "gopcms.sql" 로거 하나로 전부 잡아 sql.log 로만 보낼 수 있다
         * (logback-spring.xml, additivity=false).
         *
         * ※ 부작용: 이제 SQL 로거는 com.gonet 하위가 아니다. application.yml 의
         *   logging.level.com.gonet 을 DEBUG 로 해도 SQL 은 켜지지 않는다 —
         *   SQL 수준은 logback 의 gopcms.sql 로거가 정한다.
         */
        configuration.setLogPrefix("gopcms.sql.");
        factoryBean.setConfiguration(configuration);

        // 벤더 분기: <if test="_databaseId == 'mariadb'"> / databaseId 속성
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties vendors = new Properties();
        vendors.setProperty("MariaDB", "mariadb");
        vendors.setProperty("PostgreSQL", "postgresql");
        databaseIdProvider.setProperties(vendors);
        factoryBean.setDatabaseIdProvider(databaseIdProvider);

        return factoryBean.getObject();
    }
}
