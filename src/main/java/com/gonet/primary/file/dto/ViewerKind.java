package com.gonet.primary.file.dto;

import java.util.Locale;
import java.util.Set;

/**
 * 미리보기 방식 — 확장자마다 <b>어디서 파싱하느냐</b>가 다르다.
 *
 * <p>이 구분이 곧 보안 설계다. 문서 파서(LibreOffice·오피스 계열)는 원격코드실행 이력이
 * 길고, 우리가 여는 문서는 전부 외부에서 들어온 것이다. 그래서 원칙을 세운다:
 * <b>가능한 한 서버에서 열지 않는다.</b>
 *
 * <ul>
 *   <li>{@link #IMAGE}·{@link #VIDEO} — 브라우저가 자체적으로 연다. 서버는 바이트를 흘릴 뿐</li>
 *   <li>{@link #TEXT} — 원문 그대로. 우리도 브라우저도 <b>해석하지 않는다</b>(텍스트로만 표시)</li>
 *   <li>{@link #PDF} — 브라우저가 연다. 우리 JVM 은 바이트를 흘릴 뿐</li>
 *   <li>{@link #HWP} — rhwp(WASM)가 <b>브라우저 샌드박스 안에서</b> 연다. 서버는 관여하지 않는다</li>
 *   <li>{@link #OFFICE} — 유일하게 서버가 연다(LibreOffice). 그래서 별도 프로세스·타임아웃·
 *       전용 프로파일로 격리하고, 기본값은 꺼짐이다</li>
 *   <li>{@link #NONE} — 미리보기 없이 내려받기만</li>
 * </ul>
 *
 * <p><b>지원 범위는 업로드 화이트리스트({@code any})가 정한다</b>
 * (application.yml {@code gopcms.file.upload.allowed-extensions.any}).
 * 목록에 없는 확장자는 여기서 어떻게 분류되든 {@link DocumentViewService} 가 NONE 으로
 * 떨어뜨린다 — 미리보기 대상이 업로드 정책보다 넓어지는 일이 없게 하려는 것이다.
 */
public enum ViewerKind {

    IMAGE,
    VIDEO,
    TEXT,
    PDF,
    HWP,
    OFFICE,
    NONE;

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    /** 브라우저가 자체 재생하는 형식만. mov 는 코덱에 따라 못 여는 경우가 있어 시도만 한다. */
    private static final Set<String> VIDEO_EXT = Set.of("mp4", "webm", "mov");
    private static final Set<String> TEXT_EXT = Set.of("txt", "csv");
    private static final Set<String> HWP_EXT = Set.of("hwp", "hwpx");
    /** LibreOffice 로 PDF 변환할 대상. */
    private static final Set<String> OFFICE_EXT =
            Set.of("doc", "docx", "xls", "xlsx", "ppt", "pptx");

    /** 확장자만 보고 분류한다 — 업로드 화이트리스트 대조는 호출자가 한다. */
    public static ViewerKind of(String extension) {
        if (extension == null) {
            return NONE;
        }
        String ext = extension.toLowerCase(Locale.ROOT);
        if (IMAGE_EXT.contains(ext)) {
            return IMAGE;
        }
        if (VIDEO_EXT.contains(ext)) {
            return VIDEO;
        }
        if (TEXT_EXT.contains(ext)) {
            return TEXT;
        }
        if ("pdf".equals(ext)) {
            return PDF;
        }
        if (HWP_EXT.contains(ext)) {
            return HWP;
        }
        if (OFFICE_EXT.contains(ext)) {
            return OFFICE;
        }
        return NONE;
    }

    public boolean isPreviewable() {
        return this != NONE;
    }
}
