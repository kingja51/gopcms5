package com.gonet.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine L1 캐시 — 캐시 이름은 상수로만 참조(문자열 산재 금지).
 *
 * <ul>
 *   <li>{@link #SITE_CONTEXT}: siteCode → SiteContext (템플릿·테마·레이아웃·menuTree).
 *       사이트관리 저장 시 evict — 템플릿 전환 즉시 반영의 근거 (template-resolver-design §6.1)</li>
 *   <li>{@link #VIEW_EXISTS}: layoutCode+뷰명 → 물리 템플릿 존재 여부.
 *       키가 코드 조합이라 전환 시 무효화 불필요</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SITE_CONTEXT = "siteContext";
    public static final String VIEW_EXISTS = "viewExists";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(SITE_CONTEXT, Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build());
        manager.registerCustomCache(VIEW_EXISTS, Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofHours(1))
                .build());
        return manager;
    }
}
