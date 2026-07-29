package com.gonet.primary.board.dto;

import java.util.List;

/**
 * 좋아요·신고의 대상 유형 — V9 {@code chk_like_target_type}·{@code chk_report_target_type} 과 1:1.
 *
 * <p>게시글·댓글·컨텐츠를 한 테이블로 받는 다형 참조라 FK 가 없다. 그래서 <b>값 검증을
 * 앱이 책임진다</b> — DB 가 잡아 주지 않으므로 서비스 진입점에서 반드시 확인한다.
 */
public final class ReactionTarget {

    public static final String ARTICLE = "ARTICLE";
    public static final String COMMENT = "COMMENT";
    public static final String CONTENT = "CONTENT";

    public static final List<String> ALL = List.of(ARTICLE, COMMENT, CONTENT);

    private ReactionTarget() {
    }

    public static boolean isValid(String value) {
        return value != null && ALL.contains(value);
    }

    /** 카운트 비정규화 컬럼을 가진 대상인지 — CONTENT 는 게시판 밖이라 별도다. */
    public static boolean hasCounter(String value) {
        return ARTICLE.equals(value) || COMMENT.equals(value);
    }
}
