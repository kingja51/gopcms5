package com.gonet.common.util;

/**
 * LIKE 검색어 이스케이프 — <b>단일 지점</b>.
 *
 * <p>사용자가 친 {@code %} 를 그대로 넘기면 검색이 아니라 전체 조회가 되고, {@code _} 는
 * 한 글자 아무거나와 맞는다. 사용자가 친 문자는 문자여야 한다.
 *
 * <p>이스케이프 문자로 역슬래시 대신 {@code |} 를 쓴다 — {@code ESCAPE '\\'} 는 MariaDB 와
 * PostgreSQL 이 문자열 리터럴을 다르게 해석해(standard_conforming_strings) 벤더마다 값이
 * 달라진다. 멀티 벤더가 전제인 이 프로젝트에서는 어느 쪽에서도 특별하지 않은 문자가 안전하다.
 *
 * <p><b>매퍼는 반드시 {@code ESCAPE '|'} 와 짝지어 쓴다</b> —
 * {@code LIKE CONCAT('%', #{keywordLike}, '%') ESCAPE '|'}.
 */
public final class LikeQuery {

    private LikeQuery() {
    }

    /** null 은 그대로 — 매퍼의 {@code <if test="keyword != null">} 분기를 유지한다. */
    public static String escape(String keyword) {
        if (keyword == null) {
            return null;
        }
        // 이스케이프 문자 자신을 먼저 — 순서가 바뀌면 % 가 |% → ||% 로 깨진다
        return keyword.replace("|", "||").replace("%", "|%").replace("_", "|_");
    }
}
