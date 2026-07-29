package com.gonet.common.file.security;

import com.gonet.common.file.dto.UploadCategory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * 방어 ② — 매직바이트로 <b>실제</b> 형식을 판별하고 확장자와 교차 검증한다.
 *
 * <p>확장자와 Content-Type 은 둘 다 사용자가 정한다. 실제 내용만이 사용자가 못 속이는 값이라,
 * 여기서 나온 MIME 이 이후 모든 판단의 기준값이 된다.
 *
 * <h3>교차 검증은 <b>모든</b> 확장자에 대해 필수다 — fail-closed</h3>
 * 처음 구현에서는 일부 확장자에만 기대 MIME 을 두고 나머지는 통과시켰다.
 * 그 결과 <b>웹쉘을 {@code .docx}·{@code .hwp}·{@code .xlsx} 로 이름만 바꾸면 그대로
 * 업로드됐다</b>(실측 확인). 지금은 표에 없는 확장자를 <b>거부</b>한다 — 화이트리스트에
 * 확장자를 추가하면서 여기를 잊으면 조용히 열리는 대신 눈에 띄게 막힌다.
 *
 * <h3>표의 값은 추측이 아니라 실측이다</h3>
 * 이 프로젝트는 {@code tika-core} 만 쓴다(파서 없음). 그래서 컨테이너 계열은 개별 포맷이
 * 아니라 <b>컨테이너 종류</b>로 판별된다 — OLE2(doc·xls·ppt·hwp)는
 * {@code application/x-tika-msoffice}, OOXML(docx·xlsx·pptx·hwpx)은
 * {@code application/x-tika-ooxml}. 이게 방어에 충분한 이유: 웹쉘은 텍스트라
 * 이 값들과 절대 일치하지 않는다. 파서를 추가하면 더 구체적인 값이 나오므로
 * 구체 타입도 함께 허용해 둔다.
 */
@Component
@Slf4j
public class TikaMimeDetector {

    private static final Tika TIKA = new Tika();

    /** 컨테이너 계열 실측값 (tika-core, 파서 없음). */
    private static final String OLE2 = "application/x-tika-msoffice";
    private static final String OOXML = "application/x-tika-ooxml";

    /**
     * 확장자별 허용 MIME. <b>화이트리스트(application.yml)와 이 표는 항상 같은 집합이어야
     * 한다</b> — 여기 없는 확장자는 거부된다.
     */
    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            // 이미지
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("gif", Set.of("image/gif")),
            Map.entry("webp", Set.of("image/webp")),
            Map.entry("bmp", Set.of("image/bmp", "image/x-ms-bmp")),
            // 문서
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("doc", Set.of(OLE2, "application/msword")),
            Map.entry("xls", Set.of(OLE2, "application/vnd.ms-excel")),
            Map.entry("ppt", Set.of(OLE2, "application/vnd.ms-powerpoint")),
            Map.entry("hwp", Set.of(OLE2, "application/x-hwp", "application/haansofthwp")),
            Map.entry("docx", Set.of(OOXML, "application/zip",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xlsx", Set.of(OOXML, "application/zip",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("pptx", Set.of(OOXML, "application/zip",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("hwpx", Set.of(OOXML, "application/zip", "application/hwp+zip")),
            // 텍스트 — 스크립트성 하위 타입은 아래에서 따로 거른다
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("csv", Set.of("text/plain", "text/csv")),
            // 압축 — 우리는 절대 풀지 않는다(내용 검사 불가라 푸는 순간 방어가 무의미해진다)
            Map.entry("zip", Set.of("application/zip", OOXML)),
            // 영상
            Map.entry("mp4", Set.of("video/mp4", "video/quicktime", "video/x-m4v")),
            Map.entry("mov", Set.of("video/quicktime", "video/mp4")),
            Map.entry("webm", Set.of("video/webm", "video/x-matroska", "application/x-matroska")));

    /**
     * 형식이 서로 어긋나지 않는지 확인하고 검출 MIME 을 돌려준다.
     *
     * @throws UploadValidationException 표에 없는 확장자거나 내용이 확장자와 어긋날 때
     */
    public String detectAndVerify(Path file, String extension, UploadCategory category) {
        String detected;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            detected = TIKA.detect(in);
        } catch (IOException e) {
            throw new UploadValidationException("파일 형식을 확인할 수 없습니다.");
        }
        String mime = detected == null ? "application/octet-stream" : detected.toLowerCase(Locale.ROOT);

        Set<String> allowed = ALLOWED.get(extension);
        if (allowed == null) {
            // 화이트리스트에는 있는데 여기 표에 없다 = 설정 실수. 통과시키지 않는다.
            log.error("교차검증 표에 없는 확장자 — 업로드 거부 ext={} mime={}", extension, mime);
            throw new UploadValidationException("허용되지 않는 확장자입니다. (%s)".formatted(extension));
        }
        if (!allowed.contains(mime)) {
            // 내용과 확장자가 다르다 — 위장 업로드의 전형이다
            log.warn("확장자 위장 의심 업로드 거부 ext={} detected={}", extension, mime);
            throw new UploadValidationException(
                    "파일 내용이 확장자와 일치하지 않습니다. (%s)".formatted(extension));
        }
        if (!category.mimeMatches(mime)) {
            throw new UploadValidationException("이 항목에 올릴 수 없는 형식입니다.");
        }
        return mime;
    }
}
