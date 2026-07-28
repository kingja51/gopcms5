package com.gonet.primary.site.dto;

import com.gonet.primary.menu.dto.MenuNode;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 사이트 렌더 컨텍스트 — 요청당 1회 해석되어 Resolver·Advice·뷰가 공유 (Caffeine 캐시).
 *
 * <p>3축 폴백은 {@code SiteServiceImpl} 이 적용한 최종값이다:
 * templateCode(CSS)=krds 폴백 · themeClass=''(기본 브랜드) · layoutCode(뷰 폴더)=layout-001.
 * template-resolver-design.md §2 의 확정 조회 쿼리와 1:1.
 */
@Getter
@Setter
public class SiteContext {

    private String siteId;
    private String siteCode;
    private String siteName;
    private String defaultLang;

    /** 사이트 설명 — 홈 히어로 서브카피 등 (null 허용) */
    private String description;

    /** 시각 언어 — CSS 링크 {@code /tmpl/css/{templateCode}.css} */
    private String templateCode;

    /** 색 변형 — {@code <html class>} ('' = 템플릿 기본 브랜드) */
    private String themeClass;

    /** 구조 — 뷰 폴더 {@code layouts/{layoutCode}/**} + 레이아웃 파일 */
    private String layoutCode;

    /** 사이트별 head 삽입 조각 (관리자 편집, th:utext) */
    private String headMeta;

    /** 사이트별 footer copyright (th:utext) */
    private String copyright;

    /** 메뉴 트리(depth 1~4, href 해석 완료) — siteContext 캐시에 트리째 실림 (P4) */
    private List<MenuNode> menuTree;
}
