package com.gonet.primary.auth.mapper;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.auth.dto.UrlAccessRule;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** URL 접근제어 규칙 조회 — DynamicAuthorizationManager 원천 (읽기 전용, 캐시 경유). */
@EgovMapper
public interface UrlAccessMapper {

    /** 활성 규칙 전량 — 평가 순서(priority ASC → 사이트 우선 → 긴 패턴)대로 정렬해 반환. */
    List<UrlAccessRule> findActiveRules();

    /** 역할 집합이 보유한 세부 권한 ID — access_type=AUTH 규칙 판정용. */
    List<String> findAuthIdsByRoleIds(@Param("roleIds") Collection<String> roleIds);

    /* ── 관리 CRUD (P7-3) ───────────────────────────────────────────────── */

    /** 관리 목록 — 사용 중지·삭제분도 보이도록 별도 조회(인가 판정용 findActiveRules 와 분리). */
    List<UrlAccessRule> findPage(PageRequest cond);

    int countPage(PageRequest cond);

    UrlAccessRule findById(@Param("urlAccessId") String urlAccessId);

    /** 같은 (사이트, 패턴, 메서드) 중복 방지 — uk_role_url_access 와 1:1. */
    int countByPattern(@Param("siteId") String siteId, @Param("urlPattern") String urlPattern,
            @Param("httpMethod") String httpMethod, @Param("excludeId") String excludeId);

    int insert(UrlAccessRule rule);

    int update(UrlAccessRule rule);

    int softDelete(@Param("urlAccessId") String urlAccessId,
                   @Param("updatedBy") String updatedBy,
                   @Param("updatedIp") String updatedIp);
}
