package com.gonet.primary.site.service;

import com.gonet.primary.site.dto.SiteContext;
import java.util.List;

/**
 * 사이트 컨텍스트 서비스 인터페이스 — 컨트롤러·필터는 이 인터페이스만 주입
 * (eGov MVC 규칙: Mapper 직접 호출 금지).
 */
public interface SiteService {

    /** site_code 해석 (Caffeine siteContext 캐시, 3축 폴백 적용) — 미존재 시 null. */
    SiteContext getSiteContext(String siteCode);

    /** 기본 사이트(default_yn='Y') 폴백 — 미설정 시 null. */
    SiteContext getDefaultSiteContext();

    /** 활성 사이트 전체 — 기동 스모크용 (캐시 없음). */
    List<SiteContext> getAllActiveContexts();

    /** 사이트 설정 변경 시 캐시 무효화 — 사이트관리(P7) 저장 훅. */
    void evictSiteContext(String siteCode);
}
