package com.gonet.common.web;

import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 목록 공통 조회 조건 — 페이지·검색어. 컨트롤러가 {@code @ModelAttribute} 로 받는다.
 *
 * <p>페이지 번호는 <b>1부터</b>(사용자에게 보이는 값 그대로), SQL 은 {@link #getOffset()} 을 쓴다.
 * 범위를 벗어난 입력은 예외 대신 조용히 보정한다 — 관리자가 URL 을 직접 만지다 500 을 보는 것보다
 * 1페이지를 보는 편이 낫다.
 */
@Getter
@Setter
public class PageRequest {

    private static final int MAX_SIZE = 100;

    private int page = 1;
    private int size = 20;

    /** 검색어 — 도메인별로 어떤 컬럼에 걸지는 매퍼가 정한다. */
    private String keyword;

    public void setPage(int page) {
        this.page = Math.max(page, 1);
    }

    public void setSize(int size) {
        this.size = Math.min(Math.max(size, 1), MAX_SIZE);
    }

    public int getOffset() {
        return (page - 1) * size;
    }

    /** 공백만 있는 검색어는 없는 것으로 — 매퍼의 {@code <if test="keyword != null">} 분기 기준. */
    public String getKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    /**
     * LIKE 검색에 넣을 값 — 와일드카드를 <b>글자로</b> 취급하게 이스케이프한다.
     *
     * <p>{@code %} 를 그대로 넘기면 검색이 아니라 전체 조회가 되고, {@code _} 는 한 글자
     * 아무거나와 맞는다. 사용자가 친 문자는 문자여야 한다.
     *
     * <p>이스케이프 문자로 역슬래시 대신 {@code |} 를 쓴다 — {@code ESCAPE '\\'} 는
     * MariaDB 와 PostgreSQL 이 문자열 리터럴을 다르게 해석해(standard_conforming_strings)
     * 벤더마다 값이 달라진다. 매퍼는 반드시 {@code ESCAPE '|'} 와 짝지어 쓴다.
     *
     * <p>화면 표시는 {@link #getKeyword()} 를 쓴다 — 여기 값은 검색용이라
     * 입력창에 되돌려 주면 {@code |%} 같은 흔적이 보인다.
     */
    public String getKeywordLike() {
        String value = getKeyword();
        if (value == null) {
            return null;
        }
        // 이스케이프 문자 자신을 먼저 — 순서가 바뀌면 %가 |%→||% 로 깨진다
        return value.replace("|", "||").replace("%", "|%").replace("_", "|_");
    }
}
