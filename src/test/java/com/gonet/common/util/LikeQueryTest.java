package com.gonet.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.gonet.common.web.PageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * LIKE 이스케이프 규칙 고정.
 *
 * <p>매퍼가 {@code ESCAPE '|'} 를 함께 쓴다는 전제가 깔려 있다 — 이스케이프 문자를 바꾸면
 * 이 테스트와 26곳의 매퍼가 동시에 움직여야 한다.
 */
class LikeQueryTest {

    @Test
    @DisplayName("와일드카드는 글자가 된다 — %는 전체 조회가 아니라 퍼센트 문자")
    void escapesWildcards() {
        assertThat(LikeQuery.escape("100%")).isEqualTo("100|%");
        assertThat(LikeQuery.escape("a_b")).isEqualTo("a|_b");
        assertThat(LikeQuery.escape("%_%")).isEqualTo("|%|_|%");
    }

    @Test
    @DisplayName("이스케이프 문자 자신도 이스케이프한다 — 순서가 틀리면 %가 깨진다")
    void escapesEscapeCharFirst() {
        assertThat(LikeQuery.escape("a|b")).isEqualTo("a||b");
        // | 를 나중에 처리하면 "|%" 가 "||%" 로 뭉개져 % 가 다시 와일드카드가 된다
        assertThat(LikeQuery.escape("|%")).isEqualTo("|||%");
    }

    @Test
    @DisplayName("평범한 검색어는 그대로 — 한글·공백 포함")
    void leavesPlainTextAlone() {
        assertThat(LikeQuery.escape("공지 사항")).isEqualTo("공지 사항");
        assertThat(LikeQuery.escape("")).isEmpty();
        assertThat(LikeQuery.escape(null)).isNull();
    }

    @Test
    @DisplayName("PageRequest 는 화면용(getKeyword)과 검색용(getKeywordLike)을 나눠 준다")
    void pageRequestSplitsDisplayAndQuery() {
        PageRequest cond = new PageRequest();
        cond.setKeyword("  50% 할인  ");

        // 화면 입력창에 되돌릴 값 — 이스케이프 흔적이 없어야 한다
        assertThat(cond.getKeyword()).isEqualTo("50% 할인");
        // 매퍼가 쓸 값
        assertThat(cond.getKeywordLike()).isEqualTo("50|% 할인");
    }

    @Test
    @DisplayName("공백만 있는 검색어는 둘 다 null — 매퍼의 if 분기 기준을 유지한다")
    void blankKeywordStaysNull() {
        PageRequest cond = new PageRequest();
        cond.setKeyword("   ");

        assertThat(cond.getKeyword()).isNull();
        assertThat(cond.getKeywordLike()).isNull();
    }
}
