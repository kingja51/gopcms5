package com.gonet.common.web;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * URL 첫 세그먼트의 <b>단일 원천</b> — 사이트 해석·컨텐츠 slug 예약·사이트코드 예약이 모두 여기를 본다.
 *
 * <p>이 목록이 여러 곳에 흩어져 있으면 하나만 빠졌을 때 <b>조용히</b> 깨진다.
 * 예를 들어 {@code bbs} 를 예약어에서 빠뜨리면 누군가 site_code 를 {@code bbs} 로 만들 수 있고,
 * 그 순간 {@code /bbs/…} 가 게시판인지 그 사이트인지 구분되지 않는다.
 *
 * <h3>두 종류를 구분한다</h3>
 * <ul>
 *   <li>{@link #SKIP} — 사이트 개념이 아예 없는 자리. 관리자·API·정적자원·파일 스트리밍.
 *       사이트 컨텍스트도 템플릿도 필요 없으므로 해석을 건너뛴다</li>
 *   <li>{@link #PROGRAM} — 여러 사이트가 <b>같은 화면·같은 코드</b>를 공유하는 프로그램.
 *       사이트는 <b>두 번째</b> 세그먼트에 온다({@code /bbs/{siteCode}/{bbsCode}}).
 *       건너뛰면 안 된다 — 사이트 컨텍스트가 서야 3축(layout·template·theme)이 적용된다</li>
 * </ul>
 *
 * <p>자리 순서가 컨텐츠({@code /{siteCode}/{slug}})와 반대인 것은 의도다. 프로그램은
 * 사이트마다 화면이 같고 데이터 범위만 다르므로 프로그램을 앞에 두면 라우팅이 프로그램
 * 단위로 모인다. 컨텐츠는 사이트마다 slug 가 다른 고유 페이지라 사이트가 앞이다
 * (conventions.md §5.1).
 */
public final class UrlNamespaces {

    /** 사이트 해석을 건너뛰는 첫 세그먼트. */
    public static final Set<String> SKIP = Set.of(
            "adm", "api", "actuator", "error", "favicon.ico",
            "css", "js", "fonts", "images", "tmpl", "webjars",
            "swagger-ui", "v3",
            // 파일 스트리밍 — 바이너리 응답이라 사이트 컨텍스트도 템플릿도 필요 없다
            "file");

    /**
     * 사용자 프로그램 — 사이트코드가 <b>두 번째</b> 세그먼트에 온다.
     *
     * <p>새 프로그램을 열 때 여기에 추가하면 사이트 해석·예약어가 함께 따라온다.
     * 잊으면 그 프로그램 URL 이 사이트코드로 해석돼 404 가 된다.
     */
    public static final Set<String> PROGRAM = Set.of("bbs", "prg");

    /**
     * site_code 로 쓸 수 없는 값 = 위 둘의 합 + 고정 라우트.
     *
     * <p>같은 자리를 다투기 때문이다. 컨텐츠 slug 예약 목록도 이 값을 쓴다 —
     * {@code /{siteCode}/{slug}} 의 slug 가 프로그램 이름과 겹치면 어느 쪽인지 알 수 없다.
     */
    public static final Set<String> RESERVED;

    static {
        Set<String> reserved = new LinkedHashSet<>(SKIP);
        reserved.addAll(PROGRAM);
        // 사이트 안의 고정 라우트 — slug 로도, 사이트코드로도 쓸 수 없다
        reserved.add("index");
        reserved.add("sitemap");
        reserved.add("search");
        reserved.add("member");
        RESERVED = Set.copyOf(reserved);
    }

    private UrlNamespaces() {
    }

    public static boolean isSkip(String firstSegment) {
        return firstSegment != null && SKIP.contains(firstSegment);
    }

    /** 사이트코드를 두 번째 세그먼트에서 읽어야 하는 경로인가. */
    public static boolean isProgram(String firstSegment) {
        return firstSegment != null && PROGRAM.contains(firstSegment);
    }

    public static boolean isReserved(String value) {
        return value != null && RESERVED.contains(value);
    }

    /**
     * 경로에서 n번째 세그먼트를 꺼낸다(1부터).
     *
     * <p>비어 있는 마디는 건너뛴다 — {@code //bbs//ai/} 같은 경로도 같은 결과를 내야
     * 우회 시도가 다른 판정을 받지 않는다.
     */
    public static String segment(String uri, int index) {
        if (uri == null || index < 1) {
            return null;
        }
        int seen = 0;
        int from = 0;
        while (from < uri.length()) {
            while (from < uri.length() && uri.charAt(from) == '/') {
                from++;
            }
            int to = from;
            while (to < uri.length() && uri.charAt(to) != '/') {
                to++;
            }
            if (to > from) {
                seen++;
                if (seen == index) {
                    return uri.substring(from, to);
                }
            }
            from = to;
        }
        return null;
    }

    /**
     * 이 요청에서 사이트코드가 있어야 할 세그먼트를 돌려준다.
     *
     * @return 프로그램 경로면 2번째, 아니면 1번째. 해당 세그먼트가 없으면 null
     */
    public static String siteCodeSegment(String uri) {
        String first = segment(uri, 1);
        return isProgram(first) ? segment(uri, 2) : first;
    }
}
