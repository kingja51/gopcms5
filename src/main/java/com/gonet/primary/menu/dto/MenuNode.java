package com.gonet.primary.menu.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 메뉴 트리 노드 — 뷰 계약(P3 템플릿): {@code name · href · children} 만 참조한다.
 *
 * <p>동일 트리를 GNB(1~3뎁스)와 사이트맵(1~4뎁스)이 렌더 뎁스만 달리해 재사용.
 * href 는 menu_type 별 해석 결과: CONTENT=/{sc}/{slug} · URL=link_url ·
 * BOARD=게시판 페이즈(V4)에서 연결 · FOLDER=null(템플릿이 '#' 처리).
 */
@Getter
@Setter
public class MenuNode {

    private String menuId;
    private String parentMenuId;
    private String name;
    private String menuType;
    private String href;
    private int sortOrder;
    private int depth;

    /** 조회 전용 — CONTENT 메뉴의 대상 slug (href 해석 재료) */
    private String contentSlug;

    /** 조회 전용 — URL 메뉴의 직접 링크 */
    private String linkUrl;

    private List<MenuNode> children = new ArrayList<>();
}
