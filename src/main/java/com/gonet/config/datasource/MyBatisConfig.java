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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
 *   <li>매퍼 XML 은 인터페이스 옆 콜로케이션({@code com/gonet/{db}/**&#47;mapper/*.xml}) —
 *       pom 의 src/main/java 리소스 필터가 xml 을 복사</li>
 *   <li>벤더 분기 SQL 은 databaseId(mariadb/postgresql) 로 — 3중 파일 금지</li>
 * </ul>
 */
@Configuration
@EnableTransactionManagement
public class MyBatisConfig {

    public static final String PRIMARY_TX = "primaryTransactionManager";
    public static final String SECONDARY_TX = "secondaryTransactionManager";
    public static final String LOGGING_TX = "loggingTransactionManager";

    /* ── primary_db ─────────────────────────────────────────────────────── */

    @Bean
    @Primary
    public SqlSessionFactory primarySqlSessionFactory(
            @Qualifier("primaryDataSource") DataSource dataSource) throws Exception {
        return buildFactory(dataSource, "classpath*:com/gonet/primary/**/mapper/*.xml");
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
        return buildFactory(dataSource, "classpath*:com/gonet/secondary/**/mapper/*.xml");
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
        return buildFactory(dataSource, "classpath*:com/gonet/logging/**/mapper/*.xml");
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

    private static MapperConfigurer mapperConfigurer(String basePackage, String factoryBeanName) {
        MapperConfigurer configurer = new MapperConfigurer();
        configurer.setBasePackage(basePackage);
        configurer.setSqlSessionFactoryBeanName(factoryBeanName);
        return configurer;
    }

    private SqlSessionFactory buildFactory(DataSource dataSource, String mapperPattern)
            throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources(mapperPattern));

        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true); // site_code → siteCode
        configuration.setJdbcTypeForNull(JdbcType.NULL);
        configuration.addInterceptor(new AuditInterceptor()); // 감사컬럼 자동 세팅
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
