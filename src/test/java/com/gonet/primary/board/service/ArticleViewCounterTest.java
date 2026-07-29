package com.gonet.primary.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 조회수 중복 방지 규칙 — 새로고침으로 조회수가 계속 오르지 않아야 한다. */
class ArticleViewCounterTest {

    private static final String A1 = "BBA_00000000-0000-7000-8000-000000000001";
    private static final String A2 = "BBA_00000000-0000-7000-8000-000000000002";

    private final BoardArticleService service = mock(BoardArticleService.class);
    private final ArticleViewCounter counter = new ArticleViewCounter(service);

    @Test
    @DisplayName("첫 열람은 세고, 같은 브라우저의 재열람은 세지 않는다")
    void countsOnlyOncePerBrowser() {
        MockHttpServletRequest first = new MockHttpServletRequest();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        assertThat(counter.countOnce(A1, first, firstResponse)).isTrue();

        // 응답에 실린 쿠키를 그대로 들고 다시 온 요청
        MockHttpServletRequest second = new MockHttpServletRequest();
        second.setCookies(firstResponse.getCookies());
        assertThat(counter.countOnce(A1, second, new MockHttpServletResponse())).isFalse();

        verify(service).increaseViewCount(A1);
    }

    @Test
    @DisplayName("다른 글은 별개로 센다")
    void countsPerArticle() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        counter.countOnce(A1, new MockHttpServletRequest(), response);

        MockHttpServletRequest next = new MockHttpServletRequest();
        next.setCookies(response.getCookies());
        assertThat(counter.countOnce(A2, next, new MockHttpServletResponse())).isTrue();
    }

    @Test
    @DisplayName("부분 문자열이 우연히 겹쳐도 읽은 것으로 보지 않는다")
    void noSubstringFalsePositive() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // A1 을 접두어로 갖는 다른 ID 가 쿠키에 있는 상황
        request.setCookies(new Cookie("GOPCMS_BBS_VIEW", A1 + "9"));

        assertThat(counter.countOnce(A1, request, new MockHttpServletResponse())).isTrue();
    }

    @Test
    @DisplayName("쿠키는 무한히 자라지 않는다 — 상한을 넘으면 오래된 것부터 버린다")
    void capsCookieLength() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            // A1·A2 와 겹치지 않는 대역(9…)으로 채운다 — 겹치면 "이미 읽음" 이 되어
            // 상한 로직까지 가지도 못한다
            many.append("BBA_00000000-0000-7000-8000-9%011d".formatted(i)).append('|');
        }
        request.setCookies(new Cookie("GOPCMS_BBS_VIEW", many.substring(0, many.length() - 1)));

        MockHttpServletResponse response = new MockHttpServletResponse();
        counter.countOnce(A1, request, response);

        Cookie written = response.getCookie("GOPCMS_BBS_VIEW");
        assertThat(written).isNotNull();
        assertThat(written.getValue().split("\\|")).hasSizeLessThanOrEqualTo(80);
        // 방금 읽은 글은 반드시 남아 있어야 한다(버려지면 다음 새로고침에 또 센다)
        assertThat(written.getValue()).contains(A1);
        assertThat(written.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("쿠키가 아예 없는 첫 방문도 정상 처리된다")
    void noCookieAtAll() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies((Cookie[]) null);

        assertThat(counter.countOnce(A1, request, new MockHttpServletResponse())).isTrue();
        verify(service, never()).increaseViewCount(A2);
        verify(service).increaseViewCount(anyString());
    }
}
