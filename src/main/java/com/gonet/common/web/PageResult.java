package com.gonet.common.web;

import java.util.List;

/**
 * 목록 1페이지 — 뷰(페이저 프래그먼트)가 참조하는 계약.
 *
 * @param content 이 페이지의 행
 * @param total   전체 건수(검색 조건 적용 후)
 * @param page    1부터 시작하는 현재 페이지
 * @param size    페이지 크기
 */
public record PageResult<T>(List<T> content, int total, int page, int size) {

    /** 표시할 페이지 번호 묶음의 크기 — 페이저가 한 번에 보여주는 블록. */
    private static final int BLOCK = 10;

    public int totalPages() {
        return total == 0 ? 1 : (total + size - 1) / size;
    }

    public boolean hasPrev() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < totalPages();
    }

    /** 현재 페이지가 속한 블록의 시작 번호 (1, 11, 21 …) */
    public int blockStart() {
        return ((page - 1) / BLOCK) * BLOCK + 1;
    }

    public int blockEnd() {
        return Math.min(blockStart() + BLOCK - 1, totalPages());
    }

    /** 뷰에서 {@code th:each} 로 돌릴 페이지 번호 목록. */
    public List<Integer> blockPages() {
        return java.util.stream.IntStream.rangeClosed(blockStart(), blockEnd()).boxed().toList();
    }

    /** 이 페이지 첫 행의 화면 표시용 순번(내림차순 번호 매기기에 사용). */
    public int firstRowNo() {
        return total - (page - 1) * size;
    }
}
