package com.gonet.primary.menu.service;

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
}
