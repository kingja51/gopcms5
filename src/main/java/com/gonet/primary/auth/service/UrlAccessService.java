package com.gonet.primary.auth.service;

import com.gonet.primary.auth.dto.UrlAccessRule;
import java.util.List;
import java.util.Set;

/** URL 접근제어 규칙 공급 — 캐시 소유자. 인가 판정 자체는 DynamicAuthorizationManager. */
public interface UrlAccessService {

    /** 활성 규칙 — 평가 순서대로 정렬된 불변 리스트 (Caffeine 캐시). */
    List<UrlAccessRule> getActiveRules();

    /** 역할 CSV 가 보유한 세부 권한 ID 집합 — access_type=AUTH 판정용 (캐시). */
    Set<String> getAuthIds(String roleIdsCsv);

    /** 규칙·권한 캐시 무효화 — 규칙 편집(P7 관리자) 저장 훅에서 호출. */
    void evictCache();
}
