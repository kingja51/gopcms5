package com.gonet.primary.auth.mapper;

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
}
