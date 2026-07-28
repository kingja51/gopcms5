package com.gonet.common.web;

import com.gonet.primary.site.dto.SiteContext;

/**
 * 요청 스코프 SiteContext 홀더 — ThreadLocal(렌더 파이프라인) + request attribute(안전망)
 * 이중 바인딩 (template-resolver-design.md §2.1).
 *
 * <p>세팅·해제는 {@code SiteResolveFilter} 만 수행 (finally clear 필수).
 * Virtual thread(요청당 1스레드) 전제라 풀 누수 위험은 낮다.
 */
public final class SiteContextHolder {

    /** request attribute 키 — htmx 부분요청·비동기 경계에서의 보조 접근용. */
    public static final String REQUEST_ATTR = SiteContextHolder.class.getName() + ".CTX";

    private static final ThreadLocal<SiteContext> HOLDER = new ThreadLocal<>();

    private SiteContextHolder() {}

    public static void set(SiteContext context) {
        HOLDER.set(context);
    }

    /** 미해석 요청(정적 자원·adm 등)에서는 null. */
    public static SiteContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
