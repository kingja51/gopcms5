package com.gonet.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 본문 정화 규칙 고정.
 *
 * <p>allowlist 를 손볼 일이 생기면(에디터 교체 등) 이 테스트가 먼저 깨져야 한다 —
 * "무엇을 막기로 했는지" 를 코드가 아니라 여기에 적어 둔다.
 */
class HtmlSanitizerTest {

    @Test
    @DisplayName("스크립트 실행 경로는 전부 제거된다 — script·이벤트 속성·javascript: URL")
    void removesScriptVectors() {
        String dirty = "<p onclick=\"alert(1)\">글</p>"
                + "<script>alert(9)</script>"
                + "<a href=\"javascript:alert(2)\">링크</a>";
        String clean = HtmlSanitizer.sanitize(dirty);

        assertThat(clean).doesNotContain("<script", "onclick", "javascript:");
        assertThat(clean).contains("글");
    }

    @Test
    @DisplayName("iframe·object 는 허용하지 않는다 — 영상은 link_url 로 받는다")
    void removesEmbeds() {
        String clean = HtmlSanitizer.sanitize(
                "<iframe src=\"//x\"></iframe><object data=\"//y\"></object><p>본문</p>");

        assertThat(clean).doesNotContain("<iframe", "<object").contains("본문");
    }

    @Test
    @DisplayName("서식·표·이미지는 살린다 — 기존 본문이 저장할 때마다 깎이면 안 된다")
    void keepsFormatting() {
        String clean = HtmlSanitizer.sanitize(
                "<p><b>굵게</b> <i>기울임</i></p>"
                        + "<table summary=\"표\"><tr><th scope=\"col\">머리</th><td>값</td></tr></table>"
                        + "<img src=\"/file/FIL_1\" alt=\"대체텍스트\"/>");

        assertThat(clean).contains("<b>", "<i>", "<table", "scope=\"col\"", "alt=\"대체텍스트\"");
    }

    @Test
    @DisplayName("인라인 style 은 보존한다 — CrossEditor 출력(HTML4 계열)까지 담는 합집합 정책")
    void keepsInlineStyle() {
        String clean = HtmlSanitizer.sanitize("<p style=\"text-align:center\">가운데</p>");

        assertThat(clean).contains("text-align");
    }

    @Test
    @DisplayName("null 은 그대로 — 빈 값 판단은 호출측 몫")
    void nullPassthrough() {
        assertThat(HtmlSanitizer.sanitize(null)).isNull();
    }
}
