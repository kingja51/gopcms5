package com.gonet.primary.file.controller;

import com.gonet.common.file.security.FileStorage;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * 파일 스트리밍 — 사용자 프로그램 네임스페이스 {@code /file/**}.
 *
 * <p>파일은 웹루트 밖에 있어서 톰캣이 직접 내보내지 못한다. <b>그것이 의도</b>다 —
 * 모든 다운로드가 이 컨트롤러를 지나며 권한·검사상태 판정을 받는다.
 *
 * <p>{@code /file} 은 사이트 해석을 건너뛴다({@code SKIP_PREFIXES}) — 바이너리 응답이라
 * 사이트 컨텍스트도 템플릿도 필요 없기 때문이다.
 */
@Controller
@RequestMapping("/file")
@RequiredArgsConstructor
@Slf4j
public class FileDownUsrController {

    private final FileService fileService;
    private final FileStorage storage;

    /** 원본 다운로드 — 권한·검사상태를 통과해야 열린다. */
    @GetMapping("/{fileId}")
    public void download(@PathVariable String fileId, HttpServletResponse response) {
        FileItem item = fileService.openForDownload(fileId);
        stream(item, storage.resolve(item.getStoredPath()), response, true);
    }

    /**
     * 썸네일 — 목록·picker 미리보기용. 이미지의 축소본이라 원본보다 노출 위험이 낮지만,
     * <b>원본과 같은 권한 판정</b>을 받는다. 썸네일만 공개하면 비공개 자료의 내용이
     * 작게나마 새어 나간다.
     */
    @GetMapping("/{fileId}/thumb")
    public void thumbnail(@PathVariable String fileId, HttpServletResponse response) {
        FileItem item = fileService.openForDownload(fileId);
        if (item.getThumbnailPath() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        stream(item, storage.resolveThumb(item.getThumbnailPath()), response, false);
    }

    private void stream(FileItem item, Path path, HttpServletResponse response, boolean attachment) {
        if (!Files.isReadable(path)) {
            log.warn("파일 실체 없음 file={} path={}", item.getFileId(), path);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // 검출 MIME 을 그대로 쓰지 않는다 — 브라우저가 실행 가능한 타입으로 해석하면
        // 저장된 파일이 그 자리에서 동작할 수 있다. 첨부는 무조건 옥텟 스트림으로 내린다.
        response.setContentType(attachment
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : safeInlineType(item.getMimeDetected()));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition(item, attachment));
        // 기밀 자료가 공유 캐시에 남지 않게
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        try {
            response.setContentLengthLong(Files.size(path));
            try (OutputStream out = response.getOutputStream()) {
                Files.copy(path, out);
            }
        } catch (IOException e) {
            log.warn("다운로드 전송 중단 file={}: {}", item.getFileId(), e.toString());
        }
    }

    /**
     * 인라인으로 보여줘도 되는 것은 <b>래스터 이미지</b>뿐이다.
     *
     * <p>SVG 는 이미지처럼 보이지만 스크립트를 담을 수 있어 인라인으로 내보내면 XSS 가 된다.
     * 지금 화이트리스트에 svg 가 없어 업로드될 수 없지만, 나중에 누가 추가했을 때
     * 이 자리가 조용히 열리지 않도록 여기서도 막아 둔다.
     */
    private String safeInlineType(String detected) {
        if (detected == null || !detected.startsWith("image/")
                || detected.contains("svg")) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return detected;
    }

    private String disposition(FileItem item, boolean attachment) {
        String name = item.getOriginalName() == null ? item.getStoredName() : item.getOriginalName();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "%s; filename=\"%s\"; filename*=UTF-8''%s"
                .formatted(attachment ? "attachment" : "inline", encoded, encoded);
    }
}
