package com.gonet.primary.board.dto;

import java.util.List;

/**
 * 게시판 유형 — V9 {@code chk_bbs_master_type} 의 8값과 1:1.
 *
 * <p>유형이 <b>사용자 화면 폴더</b>를 정한다({@code front/board/{TYPE}/…}).
 * 그래서 값을 늘리려면 DDL CHECK·이 목록·화면 3벌이 함께 움직여야 한다.
 */
public final class BbsType {

    public static final String NOTICE = "NOTICE";
    public static final String BODO = "BODO";
    public static final String FREE = "FREE";
    public static final String FAQ = "FAQ";
    public static final String QNA = "QNA";
    public static final String GALLERY = "GALLERY";
    public static final String FILE = "FILE";
    public static final String YOUTUBE = "YOUTUBE";

    /** 화면 선택지 겸 검증 집합. */
    public static final List<String> ALL =
            List.of(NOTICE, BODO, FREE, FAQ, QNA, GALLERY, FILE, YOUTUBE);

    /** 화면에 보여줄 한글 이름 — 코드만으로는 운영자가 고르기 어렵다. */
    public static String label(String type) {
        return switch (type == null ? "" : type) {
            case NOTICE -> "공지";
            case BODO -> "보도자료";
            case FREE -> "자유";
            case FAQ -> "FAQ";
            case QNA -> "Q&A";
            case GALLERY -> "갤러리";
            case FILE -> "자료실";
            case YOUTUBE -> "영상";
            default -> type;
        };
    }

    private BbsType() {
    }

    public static boolean isValid(String type) {
        return type != null && ALL.contains(type);
    }
}
