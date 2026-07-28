package com.gonet.primary.site.service;

import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.primary.site.dto.SiteAdmDto;
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

    /* ── 관리 CRUD (P7) ─────────────────────────────────────────────────── */

    PageResult<SiteAdmDto> getAdmPage(PageRequest cond);

    /** 편집용 원본 — 3축 폴백 적용 전(NULL=템플릿 기본) 값. 미존재 시 null. */
    SiteAdmDto getAdm(String siteId);

    /** 다른 화면의 사이트 선택 상자용 (활성 사이트만). */
    List<SiteAdmDto> getAllForSelect();

    /**
     * 등록·수정 — siteId 유무로 갈린다. 저장 성공 시 siteContext 캐시를 비워
     * <b>재기동 없이</b> 사이트 화면에 반영한다.
     *
     * @return 저장된 site_id
     * @throws IllegalArgumentException 코드 형식·중복·예약어 위반
     */
    String saveAdm(SiteAdmDto site);

    /** soft-delete — 물리 삭제하지 않는다. */
    void deleteAdm(String siteId);
}
