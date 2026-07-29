package com.gonet.primary.file.dto;

import java.util.Set;

/**
 * 파일 그룹의 소유 도메인 — 다형 참조라 FK 가 없다(크로스 도메인 참조 금지 규약).
 *
 * <p>FK 가 없으므로 <b>존재하지 않는 entityId 를 가리키는 그룹</b>이 생길 수 있다.
 * 폼을 열고 저장하지 않은 경우가 대표적이며, 그런 고아 그룹은 정리 배치가 회수한다.
 */
public final class FileEntityType {

    public static final String BBS = "BBS";
    public static final String CONTENT = "CONTENT";
    public static final String BANNER = "BANNER";
    public static final String POPUP = "POPUP";
    public static final String MEMBER = "MEMBER";
    /** 위지윅 에디터가 본문에 넣는 이미지 — 첨부와 경로를 분리한다. */
    public static final String EDITOR = "EDITOR";
    public static final String ETC = "ETC";

    /**
     * 업로드 API 가 받아들이는 값의 전부 — <b>화이트리스트</b>.
     *
     * <p>처음에는 클라이언트가 보낸 문자열을 그대로 저장했는데, 임의 값이 그대로 들어갔다
     * (실측: {@code "아무거나../../etc"} 저장됨). 값이 자유로우면 통계·정리 배치·권한 판정이
     * 모두 예상 밖 데이터를 만나게 된다.
     */
    private static final Set<String> ALLOWED =
            Set.of(BBS, CONTENT, BANNER, POPUP, MEMBER, EDITOR, ETC);

    private FileEntityType() {
    }

    public static boolean isValid(String value) {
        return value != null && ALLOWED.contains(value);
    }

    public static Set<String> selectable() {
        return ALLOWED;
    }
}
