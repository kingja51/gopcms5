package com.gonet.primary.auth.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Csv;
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
}
