package com.gonet.common.util;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * 사용자 입력 HTML 정화 — <b>저장 시점 단일 지점</b>.
 *
 * <p>정화를 렌더 시점에 두면 화면마다 빠뜨릴 수 있고, DB 에는 위험한 마크업이 그대로
 * 남는다. 저장할 때 한 번 걸러 두면 이후 어떤 화면이 꺼내 쓰든 안전하다.
 *
 * <p>allowlist 는 <b>두 에디터 출력의 합집합</b>으로 잡는다(PLAN P9-2b). Tiptap 은
 * 시맨틱 태그를, CrossEditor 는 HTML4 계열 마크업과 인라인 스타일을 뱉는다. Tiptap 기준만
 * 잡으면 provider 를 바꾼 순간 기존 글이 저장할 때마다 깎여 나간다.
 *
 * <p>허용하지 않는 것은 명확하다 — {@code <script>}·이벤트 핸들러(onclick 등)·
 * {@code javascript:} URL·{@code <iframe>}·{@code <object>}. 영상 삽입은 태그를 직접
 * 허용하는 대신 게시판 타입(YOUTUBE)의 link_url 로 받는다.
 */
public final class HtmlSanitizer {

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)          // rel=nofollow 자동 부착
            .and(Sanitizers.TABLES)
            .and(Sanitizers.IMAGES)
            .and(Sanitizers.STYLES)         // CrossEditor 의 인라인 style 보존
            .and(new HtmlPolicyBuilder()
                    // 에디터가 흔히 쓰는 정렬·클래스 속성. class 는 KRDS 프리셋과
                    // 충돌할 수 있지만, 지우면 기존 본문의 레이아웃이 무너진다
                    .allowElements("span", "div", "figure", "figcaption", "hr", "pre", "code")
                    .allowAttributes("class", "align", "dir", "lang").globally()
                    .allowAttributes("width", "height", "alt", "title").onElements("img")
                    // 표 머리셀 정보는 접근성 판정(KWCAG) 대상이라 반드시 살린다.
                    // 요소를 이 빌더에서도 함께 허용해야 onElements 속성 규칙이 적용된다 —
                    // Sanitizers.TABLES 가 요소만 열어 줄 뿐 scope 는 떨어뜨린다(테스트 실측).
                    .allowElements("table", "thead", "tbody", "tfoot", "tr", "th", "td")
                    .allowAttributes("colspan", "rowspan", "headers", "scope", "abbr")
                    .onElements("td", "th")
                    .allowAttributes("summary").onElements("table")
                    .toFactory());

    private HtmlSanitizer() {
    }

    /**
     * HTML 본문 정화. null 은 그대로 돌려준다(빈 값 판단은 호출측 몫).
     *
     * <p>평문 본문({@code html_yn='N'})에는 쓰지 않는다 — 평문은 렌더에서
     * {@code th:text} 로 이스케이프하는 것이 맞다.
     */
    public static String sanitize(String html) {
        return html == null ? null : POLICY.sanitize(html);
    }
}
