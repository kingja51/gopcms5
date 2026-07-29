package com.gonet.primary.board.dto;

import java.util.List;

/**
 * 게시판 읽기·쓰기 권한 — V9 CHECK 제약과 대응.
 *
 * <p>{@code EMPLOYEE} 는 CHECK 에 남아 있지만 <b>쓰지 않기로 확정</b>했다(직원 역할 미도입).
 * {@code GUEST} 쓰기도 제외한다 — 비로그인 작성은 스팸·추적 불가 문제가 커서
 * 컬럼만 남기고 화면에서는 고르지 못하게 한다.
 */
public final class BoardAuth {

    /** 읽기: 전체 공개 ~ 관리자. */
    public static final List<String> READ = List.of("ALL", "MEMBER", "ADMIN");

    /** 쓰기: 회원 이상만. GUEST 는 의도적으로 뺀다. */
    public static final List<String> WRITE = List.of("MEMBER", "ADMIN");

    private BoardAuth() {
    }

    public static boolean isValidRead(String value) {
        return value != null && READ.contains(value);
    }

    public static boolean isValidWrite(String value) {
        return value != null && WRITE.contains(value);
    }
}
