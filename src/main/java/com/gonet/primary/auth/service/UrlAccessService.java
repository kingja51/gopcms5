package com.gonet.primary.auth.service;

import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
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

    /* ── 관리 CRUD (P7-3) ───────────────────────────────────────────────── */

    PageResult<UrlAccessRule> getAdmPage(PageRequest cond);

    UrlAccessRule getAdm(String urlAccessId);

    /**
     * 등록·수정. 저장하면 캐시를 비워 <b>다음 요청부터</b> 새 규칙으로 판정한다.
     *
     * @throws IllegalArgumentException 패턴·타입 위반, 중복, 타입별 필수값 누락
     */
    String saveAdm(UrlAccessRule rule);

    /**
     * 삭제 — 마지막 남은 최후 규칙(/**)을 지우면 무매칭 DENY 로 전 사이트가 닫힌다.
     * 그래서 활성 규칙이 하나뿐이면 거부한다.
     */
    void deleteAdm(String urlAccessId);
}
