package com.gonet.primary.auth.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Csv;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.config.CacheConfig;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.auth.dto.UrlAccessRule;
import com.gonet.primary.auth.mapper.UrlAccessMapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * URL 접근제어 규칙 공급 — 요청마다 DB 를 때리지 않도록 캐시 뒤에 둔다.
 *
 * <p>규칙은 인가 판정의 임계 경로에 있으므로 CSV 파싱까지 캐시 적재 시 1회로 끝낸다
 * ({@link UrlAccessRule#parseCsv()}). 규칙 편집 후에는 {@link #evictCache()} 로 즉시 반영.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class UrlAccessServiceImpl extends AbstractCmsService implements UrlAccessService {

    /** DB CHECK(chk 제약)과 같은 목록 — 화면 선택지도 여기서 나온다. */
    public static final List<String> ACCESS_TYPES = List.of(
            "PERMIT_ALL", "AUTHENTICATED", "ANONYMOUS", "ROLE", "AUTH", "IP_ONLY", "DENY");

    /** http_method 선택지 — ALL 은 "메서드 무관". */
    public static final List<String> HTTP_METHODS = List.of(
            "ALL", "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private final UrlAccessMapper urlAccessMapper;

    @Override
    @Cacheable(value = CacheConfig.URL_ACCESS, key = "'rules'")
    public List<UrlAccessRule> getActiveRules() {
        return urlAccessMapper.findActiveRules().stream().map(UrlAccessRule::parseCsv).toList();
    }

    @Override
    @Cacheable(value = CacheConfig.URL_ACCESS, key = "'auths:' + #roleIdsCsv")
    public Set<String> getAuthIds(String roleIdsCsv) {
        Set<String> roleIds = Csv.toSet(roleIdsCsv);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(urlAccessMapper.findAuthIdsByRoleIds(roleIds));
    }

    @Override
    @CacheEvict(value = CacheConfig.URL_ACCESS, allEntries = true)
    public void evictCache() {
        // 캐시 무효화 전용 — 규칙·권한 두 키 계열을 함께 비운다
    }

    /* ── 관리 CRUD (P7-3) ───────────────────────────────────────────────── */

    @Override
    public PageResult<UrlAccessRule> getAdmPage(PageRequest cond) {
        return new PageResult<>(urlAccessMapper.findPage(cond), urlAccessMapper.countPage(cond),
                cond.getPage(), cond.getSize());
    }

    @Override
    public UrlAccessRule getAdm(String urlAccessId) {
        return urlAccessMapper.findById(urlAccessId);
    }

    /** 쓰기 — writable override. 저장과 동시에 캐시를 비워 다음 요청부터 새 규칙이 선다. */
    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(value = CacheConfig.URL_ACCESS, allEntries = true)
    public String saveAdm(UrlAccessRule rule) {
        validate(rule);
        if (rule.getUrlAccessId() == null || rule.getUrlAccessId().isBlank()) {
            rule.setUrlAccessId(Uid.next(UidPrefix.RUA));
            urlAccessMapper.insert(rule);
        } else {
            urlAccessMapper.update(rule);
        }
        return rule.getUrlAccessId();
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(value = CacheConfig.URL_ACCESS, allEntries = true)
    public void deleteAdm(String urlAccessId) {
        // 규칙이 0건이 되면 무매칭 DENY 로 로그인 화면까지 닫힌다(RbacSmokeRunner 가 기동도 막는다)
        if (urlAccessMapper.findActiveRules().size() <= 1) {
            throw new IllegalArgumentException(
                    "마지막 남은 규칙은 삭제할 수 없습니다. 규칙이 0건이 되면 모든 요청이 차단됩니다.");
        }
        urlAccessMapper.softDelete(urlAccessId);
    }

    /**
     * 규칙은 잘못 저장하면 화면이 통째로 닫히거나 열려버린다 — 저장 전에 최대한 막는다.
     * 타입별 필수값은 DB CHECK 도 걸려 있지만 그건 500 이라, 여기서 안내 문구로 돌린다.
     */
    private void validate(UrlAccessRule rule) {
        if (rule.getUrlPattern() == null || !rule.getUrlPattern().startsWith("/")) {
            throw new IllegalArgumentException("URL 패턴은 '/' 로 시작해야 합니다 (예: /adm/**).");
        }
        if (!ACCESS_TYPES.contains(rule.getAccessType())) {
            throw new IllegalArgumentException("알 수 없는 접근 유형입니다: " + rule.getAccessType());
        }
        if ("ROLE".equals(rule.getAccessType()) && isBlank(rule.getRequiredRoles())) {
            throw new IllegalArgumentException("ROLE 유형은 허용 역할을 하나 이상 골라야 합니다.");
        }
        if ("AUTH".equals(rule.getAccessType()) && isBlank(rule.getRequiredAuths())) {
            throw new IllegalArgumentException("AUTH 유형은 필요한 권한 ID(CSV)가 필요합니다.");
        }
        if ("IP_ONLY".equals(rule.getAccessType()) && isBlank(rule.getAllowedIps())) {
            throw new IllegalArgumentException("IP_ONLY 유형은 허용 IP(CSV)가 필요합니다.");
        }
        if (urlAccessMapper.countByPattern(rule.getSiteId(), rule.getUrlPattern(),
                rule.getHttpMethod(), rule.getUrlAccessId()) > 0) {
            throw new IllegalArgumentException(
                    "같은 사이트·패턴·메서드의 규칙이 이미 있습니다. 기존 규칙을 수정하세요.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
