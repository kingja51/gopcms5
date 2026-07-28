package com.gonet.common.web;

/** 요청 스코프 attribute 키 상수 — 문자열 산재 금지. */
public final class RequestAttrs {

    /** 해석된 현재 메뉴 ID (MNU_…) — 컨트롤러가 세팅, 접근 로그(log_access.menu_id)가 소비. */
    public static final String MENU_ID = "gopcms.menuId";

    /**
     * 인증 주체 스냅샷({@link LoginPrincipal}) — 시큐리티 체인 안쪽의 ActorCaptureFilter 가
     * 세팅하고, 체인 <b>바깥</b>의 AccessLogFilter 가 소비한다(그 시점엔 SecurityContext 가
     * 이미 비워져 있다).
     */
    public static final String ACTOR = "gopcms.actor";

    /** CSP nonce — SecurityHeadersFilter 가 세팅, 뷰의 script/style 태그가 소비. */
    public static final String CSP_NONCE = "gopcms.cspNonce";

    private RequestAttrs() {}
}
