package com.gonet.primary.file.service;

import com.gonet.common.file.config.FileUploadProperties;
import com.gonet.common.file.security.FileStorage;
import com.gonet.common.file.viewer.DocumentConversionException;
import com.gonet.common.file.viewer.OfficeConverter;
import com.gonet.common.file.viewer.OfficeConverterProperties;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.dto.ViewerKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 미리보기 자원 해석 — "이 파일을 화면에 보여주려면 어떤 바이트를 내보내야 하는가".
 *
 * <p>형식마다 파싱 위치가 다르다({@link ViewerKind}). 이 서비스가 하는 일은
 * <b>서버가 열어야 하는 경우에만</b> 변환을 수행하고, 나머지는 원본 경로를 그대로 돌려주는 것이다.
 *
 * <p>변환 결과는 파일 해시 기준으로 캐시한다. 파일 ID 가 아니라 <b>해시</b>를 쓰는 이유:
 * 같은 문서가 여러 번 올라와도 변환은 한 번이면 되고, 반대로 내용이 바뀌면 캐시가 자동으로
 * 갈린다(같은 ID 로 내용만 바뀌는 일은 없지만, 규칙을 내용에 걸어 두는 편이 안전하다).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentViewService {

    private final FileStorage storage;
    private final FileUploadProperties uploadProperties;
    private final OfficeConverterProperties officeProperties;
    /** 오피스 변환은 꺼져 있을 수 있다 — 빈이 없어도 나머지 미리보기는 동작해야 한다. */
    private final ObjectProvider<OfficeConverter> officeConverter;

    /** 미리보기로 내보낼 실제 파일과 그 Content-Type. */
    public record Preview(Path path, String contentType) {
    }

    /**
     * 이 파일을 어떻게 보여줄지 판정한다.
     *
     * <p>먼저 <b>업로드 화이트리스트({@code any})</b>와 대조한다. 미리보기 대상이
     * 업로드 정책보다 넓어지면, 정책을 좁혀도 뷰어 경로로 여전히 열리는 구멍이 생긴다.
     * 목록은 설정 한 곳(application.yml)이 원천이므로 여기서 따로 관리하지 않는다.
     */
    public ViewerKind kindOf(FileItem file) {
        String ext = file.getExtension() == null
                ? "" : file.getExtension().toLowerCase(java.util.Locale.ROOT);
        if (!uploadProperties.extensionsOf("any").contains(ext)) {
            return ViewerKind.NONE;
        }
        ViewerKind kind = ViewerKind.of(ext);
        if (kind == ViewerKind.OFFICE && !officeEnabled()) {
            // 변환기가 없으면 미리보기를 약속하지 않는다 — 눌렀을 때 실패하는 것보다 낫다
            return ViewerKind.NONE;
        }
        return kind;
    }

    public boolean officeEnabled() {
        return officeProperties.isEnabled() && officeConverter.getIfAvailable() != null;
    }

    /**
     * 브라우저가 직접 렌더할 수 있는 형태로 준비한다.
     *
     * <p>TEXT·HWP 는 여기 오지 않는다 — 클라이언트가 원본 바이트를 받아 직접 처리한다
     * (텍스트는 그대로 표시, HWP 는 WASM 으로 연다).
     *
     * @return 내보낼 파일. 미리보기를 만들 수 없으면 empty
     */
    public Optional<Preview> prepare(FileItem file) {
        ViewerKind kind = kindOf(file);
        return switch (kind) {
            case IMAGE -> Optional.of(new Preview(
                    storage.resolve(file.getStoredPath()), safeInlineType(file.getMimeDetected(), "image/")));
            case VIDEO -> Optional.of(new Preview(
                    storage.resolve(file.getStoredPath()), safeInlineType(file.getMimeDetected(), "video/")));
            case PDF -> Optional.of(new Preview(
                    storage.resolve(file.getStoredPath()), "application/pdf"));
            case OFFICE -> convertOffice(file).map(p -> new Preview(p, "application/pdf"));
            // TEXT·HWP 는 클라이언트가 원본 바이트를 직접 받아 처리한다 — 서버는 열지 않는다
            case TEXT, HWP, NONE -> Optional.empty();
        };
    }

    /**
     * 인라인으로 내보내도 되는 타입인지 확인하고, 아니면 옥텟 스트림으로 떨어뜨린다.
     *
     * <p>SVG 는 이미지처럼 보이지만 스크립트를 담는다 — 인라인이면 XSS 다. 지금 화이트리스트에
     * 없어 올라올 수 없지만, 나중에 열렸을 때 이 자리가 조용히 뚫리지 않게 막아 둔다.
     * 판별값(매직바이트)을 쓰는 것도 같은 이유다 — 확장자는 사용자가 정하지만 내용은 못 속인다.
     */
    private String safeInlineType(String detected, String expectedPrefix) {
        if (detected == null || !detected.startsWith(expectedPrefix) || detected.contains("svg")) {
            return "application/octet-stream";
        }
        return detected;
    }

    /** 변환 결과를 캐시에서 찾고, 없으면 만든다. */
    private Optional<Path> convertOffice(FileItem file) {
        OfficeConverter converter = officeConverter.getIfAvailable();
        if (converter == null) {
            return Optional.empty();
        }
        String hash = file.getFileHash();
        // 해시를 두 단계로 쪼개 한 디렉터리에 파일이 몰리지 않게 한다
        Path cached = Path.of(officeProperties.getCacheDir())
                .resolve(hash.substring(0, 2)).resolve(hash + ".pdf");
        if (Files.isReadable(cached)) {
            return Optional.of(cached);
        }
        try {
            converter.convertToPdf(storage.resolve(file.getStoredPath()), cached);
            log.info("문서 변환 완료 file={} ext={}", file.getFileId(), file.getExtension());
            return Optional.of(cached);
        } catch (DocumentConversionException e) {
            // 사유는 로그에만 — 어떤 문서가 파서를 어떻게 흔드는지 알려주지 않는다
            log.warn("문서 변환 실패 file={} ext={}: {}",
                    file.getFileId(), file.getExtension(), e.getMessage());
            return Optional.empty();
        }
    }
}
