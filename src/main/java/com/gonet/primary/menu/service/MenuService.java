package com.gonet.primary.menu.service;

import com.gonet.primary.menu.dto.MenuAdmDto;
import com.gonet.primary.menu.dto.MenuNode;
import java.util.List;

/** 메뉴 트리 서비스 — 캐싱은 SiteContext(siteContext 캐시)에 트리째 실려 함께 관리된다. */
public interface MenuService {

    /** 활성 메뉴 트리(depth 1~4) — href 해석 완료 상태로 반환. */
    List<MenuNode> getMenuTree(String siteId, String siteCode);

    /** menuId 로 루트→해당 노드 경로 탐색 (breadcrumb) — 미발견 시 빈 리스트. */
    List<MenuNode> findPathByMenuId(List<MenuNode> tree, String menuId);

    /** href(현재 URI) 로 경로 탐색 (breadcrumb 폴백) — 미발견 시 빈 리스트. */
    List<MenuNode> findPathByHref(List<MenuNode> tree, String href);

    /* ── 관리 CRUD (P7) ─────────────────────────────────────────────────── */

    /**
     * 사이트의 메뉴 전량 — 트리 순서(depth·sort)로 평면 반환. 화면은 depth 로 들여쓴다.
     * 페이징하지 않는 이유: 트리를 페이지로 자르면 부모 없는 자식만 보이는 화면이 된다.
     */
    List<MenuAdmDto> getAdmList(String siteId, String keyword);

    MenuAdmDto getAdm(String menuId);

    /**
     * 등록·수정. depth 는 상위 메뉴에서 자동 계산하고, menu_type 에 맞지 않는
     * 링크 필드는 저장 전에 비운다(찌꺼기 값이 남아 나중에 오해를 만든다).
     *
     * @throws IllegalArgumentException 이름 누락 · 순환 참조 · 타입별 필수값 누락 · 깊이 초과
     */
    String saveAdm(MenuAdmDto menu);

    void deleteAdm(String menuId);
}
