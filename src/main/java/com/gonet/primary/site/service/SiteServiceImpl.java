package com.gonet.primary.site.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.CacheConfig;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.menu.service.MenuService;
import com.gonet.primary.site.dto.SiteContext;
import com.gonet.primary.site.mapper.SiteMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트 컨텍스트 서비스 — 3축 폴백의 단일 적용 지점.
 *
 * <p>폴백 규칙 (template-resolver-design.md §2): 템플릿 미선택 → {@code krds},
 * 테마 미선택 → {@code ''}(기본 브랜드), 레이아웃 미선택 → 템플릿 기본
 * (SQL COALESCE) → 그마저 없으면 {@code layout-001}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class SiteServiceImpl extends AbstractCmsService implements SiteService {

    /** 시스템 최종 폴백 — V2 시드(krds 템플릿·layout-001)와 1:1. */
    public static final String FALLBACK_TEMPLATE = "krds";
    public static final String FALLBACK_LAYOUT = "layout-001";

    private final SiteMapper siteMapper;
    private final MenuService menuService;

    @Override
    @Cacheable(cacheNames = CacheConfig.SITE_CONTEXT, unless = "#result == null")
    public SiteContext getSiteContext(String siteCode) {
        return applyFallbacks(siteMapper.findBySiteCode(siteCode));
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.SITE_CONTEXT, key = "'__default__'",
            unless = "#result == null")
    public SiteContext getDefaultSiteContext() {
        return applyFallbacks(siteMapper.findDefaultSite());
    }

    @Override
    public List<SiteContext> getAllActiveContexts() {
        return siteMapper.findAllActive().stream().map(this::applyFallbacks).toList();
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public void evictSiteContext(String siteCode) {
        // 사이트 수 소규모 — 전체 evict 로 단순화 (default 키 동시 무효화 겸용)
    }

    private SiteContext applyFallbacks(SiteContext context) {
        if (context == null) {
            return null;
        }
        if (context.getTemplateCode() == null) {
            context.setTemplateCode(FALLBACK_TEMPLATE);
        }
        if (context.getThemeClass() == null) {
            context.setThemeClass("");
        }
        if (context.getLayoutCode() == null) {
            context.setLayoutCode(FALLBACK_LAYOUT);
        }
        // 메뉴 트리 동반 적재 — siteContext 캐시에 트리째 실려 evict 시 함께 갱신 (P4)
        context.setMenuTree(menuService.getMenuTree(context.getSiteId(), context.getSiteCode()));
        return context;
    }
}
