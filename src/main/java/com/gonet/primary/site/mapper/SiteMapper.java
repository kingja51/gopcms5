package com.gonet.primary.site.mapper;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.site.dto.SiteAdmDto;
import com.gonet.primary.site.dto.SiteContext;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/**
 * 사이트 조회 매퍼 — eGov {@code @EgovMapper} + MapperConfigurer 스캔 (호환성 가이드 p.7).
 * SQL 은 콜로케이션 XML(SiteMapper.xml), 전량 {@code #{}} 바인딩.
 */
@EgovMapper
public interface SiteMapper {

    /** site_code 로 렌더 컨텍스트 조회 (템플릿·테마·레이아웃 조인) — 미존재 시 null. */
    SiteContext findBySiteCode(String siteCode);

    /** 기본 사이트(default_yn='Y') — 도메인·경로 미해석 시 폴백. */
    SiteContext findDefaultSite();

    /** 활성 사이트 전체 컨텍스트 — 기동 스모크(레이아웃·CSS 존재 검증)용. */
    List<SiteContext> findAllActive();

    /* ── 관리 CRUD (P7) ─────────────────────────────────────────────────── */

    List<SiteAdmDto> findPage(PageRequest cond);

    int countPage(PageRequest cond);

    SiteAdmDto findAdmById(String siteId);

    /** 코드 중복 확인 — 수정 시 자기 자신은 제외한다. */
    int countByCode(@Param("siteCode") String siteCode, @Param("excludeId") String excludeId);

    int insert(SiteAdmDto site);

    int update(SiteAdmDto site);

    /** soft-delete — 물리 삭제 금지(감사·복구). */
    int softDelete(@Param("siteId") String siteId);

    /** 기본 사이트 단일성 보장 — 지정 사이트 외 전부 'N'. */
    int clearDefaultExcept(@Param("siteId") String siteId);

    /** 3축 선택이 폴백까지 거쳐 실제로 해석되는 코드 — 저장 전 물리 자원 확인용. */
    SiteAdmDto findEffectiveCodes(@Param("templateId") String templateId,
            @Param("layoutId") String layoutId);

    /** 선택 목록용 — 활성 사이트 (code, name) */
    List<SiteAdmDto> findAllForSelect();
}
