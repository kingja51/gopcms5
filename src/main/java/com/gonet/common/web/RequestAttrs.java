package com.gonet.common.web;

/** 요청 스코프 attribute 키 상수 — 문자열 산재 금지. */
public final class RequestAttrs {

    /** 해석된 현재 메뉴 ID (MNU_…) — 컨트롤러가 세팅, 접근 로그(log_access.menu_id)가 소비. */
    public static final String MENU_ID = "gopcms.menuId";

    private RequestAttrs() {}
}
