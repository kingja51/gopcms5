package com.gonet.primary.menu.dto;

import com.gonet.common.audit.Auditable;
import lombok.Getter;
import lombok.Setter;

/**
 * 메뉴 관리 DTO — tb_menu.
 *
 * <p>{@code depth} 는 입력받지 않는다: 상위 메뉴가 정해지면 자동으로 결정되는 파생값이라
 * 사람이 넣으면 트리와 어긋나기만 한다(서비스가 계산).
 *
 * <p>{@code menuType} 별로 의미 있는 필드가 다르다 —
 * CONTENT=linkTargetId(컨텐츠) · URL=linkUrl · FOLDER=둘 다 없음 · BOARD=게시판 페이즈.
 */
@Getter
@Setter
public class MenuAdmDto extends Auditable {

    private String menuId;
    private String siteId;
    private String siteCode;
    private String parentMenuId;
    private String menuName;

    /** FOLDER | CONTENT | URL | BOARD */
    private String menuType;

    private String linkTargetId;
    private String linkUrl;
    private int sortOrder;
    private int depth;
    private String authRequiredYn;
    private String useYn;

    /** 목록 표시용 — 트리 들여쓰기와 대상 이름 */
    private String parentMenuName;
    private String targetTitle;
    private int childCount;
}
