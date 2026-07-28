package com.gonet.primary.site.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.config.CacheConfig;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.config.web.SiteResolveFilter;
import com.gonet.primary.menu.service.MenuService;
import com.gonet.primary.site.dto.SiteAdmDto;
import com.gonet.primary.site.dto.SiteContext;
import com.gonet.primary.site.mapper.SiteMapper;
import com.gonet.primary.theme.service.ThemeService;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
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

    /** conventions §5 — site_code 는 URL 첫 세그먼트라 slug 와 같은 문자 집합. */
    private static final Pattern SITE_CODE_PATTERN = Pattern.compile("^[a-z0-9-]{1,30}$");

    private final SiteMapper siteMapper;
    private final MenuService menuService;
    private final ThemeService themeService;

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

    /* ── 관리 CRUD (P7) ─────────────────────────────────────────────────── */

    @Override
    public PageResult<SiteAdmDto> getAdmPage(PageRequest cond) {
        return new PageResult<>(siteMapper.findPage(cond), siteMapper.countPage(cond),
                cond.getPage(), cond.getSize());
    }

    @Override
    public SiteAdmDto getAdm(String siteId) {
        return siteMapper.findAdmById(siteId);
    }

    @Override
    public List<SiteAdmDto> getAllForSelect() {
        return siteMapper.findAllForSelect();
    }

    /** 쓰기 — writable override (트랜잭션 함정 규약). 저장·기본사이트 정리·캐시 무효화를 원자로. */
    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public String saveAdm(SiteAdmDto site) {
        validate(site);
        if (site.getSiteId() == null || site.getSiteId().isBlank()) {
            site.setSiteId(Uid.next(UidPrefix.SIT));
            siteMapper.insert(site);
        } else {
            siteMapper.update(site);
        }
        // 기본 사이트는 하나뿐 — 새로 지정되면 나머지를 내린다(경로 미해석 시 폴백 대상)
        if ("Y".equals(site.getDefaultYn())) {
            siteMapper.clearDefaultExcept(site.getSiteId());
        }
        return site.getSiteId();
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public void deleteAdm(String siteId) {
        SiteAdmDto site = siteMapper.findAdmById(siteId);
        if (site == null) {
            throw new IllegalArgumentException("이미 삭제되었거나 없는 사이트입니다.");
        }
        if ("Y".equals(site.getDefaultYn())) {
            // 기본 사이트가 사라지면 루트(/)·미해석 경로가 갈 곳을 잃는다
            throw new IllegalArgumentException(
                    "기본 사이트는 삭제할 수 없습니다. 다른 사이트를 기본으로 지정한 뒤 삭제하세요.");
        }
        siteMapper.softDelete(siteId);
    }

    /**
     * site_code 는 URL 첫 세그먼트라 제약이 URL 계약에서 나온다 (conventions §5):
     * 소문자·숫자·하이픈만, 그리고 <b>정적 자원·관리자 네임스페이스와 겹치면 안 된다</b>
     * — 겹치면 그 사이트는 영영 열리지 않는다(SiteResolveFilter 가 해석을 건너뛴다).
     */
    private void validate(SiteAdmDto site) {
        String code = site.getSiteCode();
        if (code == null || !SITE_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "사이트 코드는 소문자·숫자·하이픈 1~30자여야 합니다.");
        }
        if (SiteResolveFilter.SKIP_PREFIXES.contains(code)) {
            throw new IllegalArgumentException(
                    "'" + code + "' 는 시스템 예약 경로라 사이트 코드로 쓸 수 없습니다.");
        }
        if (site.getSiteName() == null || site.getSiteName().isBlank()) {
            throw new IllegalArgumentException("사이트 이름은 필수입니다.");
        }
        if (siteMapper.countByCode(code, site.getSiteId()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 사이트 코드입니다: " + code);
        }
        if (site.getSiteId() != null && site.getSiteId().equals(site.getParentSiteId())) {
            throw new IllegalArgumentException("자기 자신을 상위 사이트로 지정할 수 없습니다.");
        }
        // 테마는 템플릿 종속(복합 FK) — DB 도 막지만 그건 500 이라, 여기서 안내 문구로 돌린다
        if (!themeService.belongsToTemplate(site.getThemeId(), site.getTemplateId())) {
            throw new IllegalArgumentException(
                    "선택한 테마는 이 템플릿의 테마가 아닙니다. 템플릿에 속한 테마를 고르세요.");
        }
        validateRenderableAssets(site);
    }

    /**
     * 저장하려는 3축 조합의 <b>물리 자원</b>이 있는지 확인한다.
     *
     * <p>기동 시점에는 LayoutSmokeRunner 가 같은 검사를 하지만, 관리 화면이 생기면서
     * <b>운영 중에</b> 없는 조합을 고를 수 있게 됐다 — 실제로 layout.html 이 없는 레이아웃으로
     * 바꿔 사이트가 500 이 된 사례를 P7 검증에서 확인했다. 저장을 막는 편이 낫다.
     */
    private void validateRenderableAssets(SiteAdmDto site) {
        SiteAdmDto codes = siteMapper.findEffectiveCodes(site.getTemplateId(), site.getLayoutId());
        String layoutCode = codes == null || codes.getLayoutCode() == null
                ? FALLBACK_LAYOUT : codes.getLayoutCode();
        String templateCode = codes == null || codes.getTemplateCode() == null
                ? FALLBACK_TEMPLATE : codes.getTemplateCode();

        if (!new ClassPathResource("templates/layouts/%s/layout.html".formatted(layoutCode))
                .exists()) {
            throw new IllegalArgumentException(
                    "레이아웃 '%s' 의 화면 파일(templates/layouts/%s/layout.html)이 없어 저장할 수 없습니다."
                            .formatted(layoutCode, layoutCode));
        }
        if (!new ClassPathResource("static/tmpl/css/%s.css".formatted(templateCode)).exists()) {
            throw new IllegalArgumentException(
                    "템플릿 '%s' 의 CSS(/tmpl/css/%s.css)가 없어 저장할 수 없습니다."
                            .formatted(templateCode, templateCode));
        }
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
