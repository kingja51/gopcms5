package com.gonet.primary.file.dto;

import java.util.List;

/**
 * 첨부 다운로드 권한 7단계 — V9 의 {@code chk_filegroup_download_auth} 와 1:1.
 *
 * <p>{@code ROLE_EMPLOYEE} 는 CHECK 제약에는 남아 있지만 <b>쓰지 않기로 확정</b>했다
 * (직원 역할을 도입하지 않는다). 그래서 화면 선택지에서 제외한다 — DB 가 허용한다고
 * 앱이 반드시 써야 하는 것은 아니다.
 */
public final class DownloadAuth {

    public static final String ANONYMOUS = "ANONYMOUS";
    public static final String ROLE_MEMBER = "ROLE_MEMBER";
    public static final String ROLE_STAFF = "ROLE_STAFF";
    public static final String OWNER_PRIVACY = "OWNER_PRIVACY";
    public static final String ROLE_MANAGER = "ROLE_MANAGER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    /** 화면 선택지 — 넓은 쪽부터. */
    public static final List<String> SELECTABLE = List.of(
            ANONYMOUS, ROLE_MEMBER, ROLE_STAFF, OWNER_PRIVACY, ROLE_MANAGER, ROLE_ADMIN);

    private DownloadAuth() {
    }

    public static boolean isValid(String value) {
        return value != null && SELECTABLE.contains(value);
    }
}
