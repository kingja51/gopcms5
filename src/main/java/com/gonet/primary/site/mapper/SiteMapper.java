package com.gonet.primary.site.mapper;

import com.gonet.primary.site.dto.SiteContext;
import java.util.List;
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
}
