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
}
