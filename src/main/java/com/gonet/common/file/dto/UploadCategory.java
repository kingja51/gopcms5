package com.gonet.common.file.dto;

import java.util.Locale;

/**
 * 업로드 카테고리 — 허용 확장자 목록과 <b>MIME 대분류</b>를 함께 규정한다.
 *
 * <p>확장자만 보거나 MIME 만 보면 각각 우회가 쉽다. 확장자가 {@code jpg} 인데 매직바이트가
 * 실행파일이거나, MIME 이 {@code image/*} 라고 신고됐지만 실제 내용은 스크립트인 경우가 그렇다.
 * 그래서 두 축을 <b>교차 검증</b>한다 — 확장자가 목록에 있고 <i>동시에</i> 실제 MIME 이
 * 이 카테고리의 대분류와 맞아야 통과다.
 */
public enum UploadCategory {

    /** 문서 — 한글·오피스·PDF 등. MIME 대분류가 제각각이라 접두 검사를 하지 않는다. */
    DOCUMENT("document", null),

    IMAGE("image", "image/"),

    VIDEO("video", "video/"),

    /** 범용 — 문서·이미지·영상을 함께 받는 첨부. 확장자 화이트리스트가 유일한 관문이다. */
    ANY("any", null);

    private final String key;
    private final String mimePrefix;

    UploadCategory(String key, String mimePrefix) {
        this.key = key;
        this.mimePrefix = mimePrefix;
    }

    public String key() {
        return key;
    }

    /** 실제 MIME 이 이 카테고리와 맞는가. 접두 규정이 없는 카테고리는 항상 통과. */
    public boolean mimeMatches(String detectedMime) {
        if (mimePrefix == null) {
            return true;
        }
        return detectedMime != null && detectedMime.toLowerCase(Locale.ROOT).startsWith(mimePrefix);
    }

    public static UploadCategory of(String raw) {
        if (raw == null || raw.isBlank()) {
            return ANY;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ANY;                        // 알 수 없는 값은 가장 좁은 화이트리스트로 떨어뜨린다
        }
    }
}
