package com.gonet.primary.file.controller;

import com.gonet.common.file.security.FileStorage;
import com.gonet.logging.file.service.FileDownloadLogger;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.dto.ViewerKind;
import com.gonet.primary.file.service.DocumentViewService;
import com.gonet.primary.file.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final FileDownloadLogger downloadLogger;
    private final DocumentViewService documentViewService;

    /** 원본 다운로드 — 권한·검사상태를 통과해야 열린다. */
    @GetMapping("/{fileId}")
    public void download(@PathVariable String fileId,
            HttpServletRequest request, HttpServletResponse response) {
        FileItem item = open(fileId, request, FileDownloadLogger.TYPE_SINGLE);
        stream(item, storage.resolve(item.getStoredPath()), response, true);
        downloadLogger.write(request, item.getFileId(), item.getFileGroupId(),
                item.getOriginalName(), item.getExtension(), item.getSizeBytes(),
                FileDownloadLogger.TYPE_SINGLE, FileDownloadLogger.RESULT_SUCCESS);
    }

    /**
     * 권한 판정을 감싸 <b>거부도 기록</b>한다.
     *
     * <p>성공만 남기면 "한 계정이 남의 비공개 자료를 반복해서 두드리는" 패턴이 보이지 않는다.
     * 거부 기록이야말로 사고 조사에서 먼저 찾는 것이다.
     */
    private FileItem open(String fileId, HttpServletRequest request, String type) {
        try {
            return fileService.openForDownload(fileId);
        } catch (RuntimeException e) {
            downloadLogger.write(request, fileId, null, null, null, null,
                    type, FileDownloadLogger.RESULT_BLOCKED);
            throw e;
        }
    }

    /**
     * 썸네일 — 목록·picker 미리보기용. 이미지의 축소본이라 원본보다 노출 위험이 낮지만,
     * <b>원본과 같은 권한 판정</b>을 받는다. 썸네일만 공개하면 비공개 자료의 내용이
     * 작게나마 새어 나간다.
     */
    @GetMapping("/{fileId}/thumb")
    public void thumbnail(@PathVariable String fileId,
            HttpServletRequest request, HttpServletResponse response) {
        FileItem item = open(fileId, request, FileDownloadLogger.TYPE_SINGLE);
        if (item.getThumbnailPath() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        stream(item, storage.resolveThumb(item.getThumbnailPath()), response, false);
    }

    /**
     * 미리보기 — 브라우저가 직접 렌더할 수 있는 형태로 내보낸다.
     *
     * <p>다운로드와 <b>같은 권한 판정</b>을 받는다. 미리보기만 열어 두면 비공개 자료의 내용이
     * 그대로 새는 것이라, 여기서 느슨해지면 download_auth 정책 전체가 무의미해진다.
     *
     * <p>HWP 는 이 경로로 오지 않는다 — 클라이언트가 원본 바이트를 받아 WASM 으로 직접 연다.
     */
    @GetMapping("/{fileId}/view")
    public void view(@PathVariable String fileId,
            HttpServletRequest request, HttpServletResponse response) {
        FileItem item = open(fileId, request, FileDownloadLogger.TYPE_SINGLE);
        var preview = documentViewService.prepare(item);
        if (preview.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        streamInline(item, preview.get(), response);
    }

    /**
     * 인라인 응답 — 브라우저가 그 자리에서 연다.
     *
     * <p>{@code sandbox} 헤더를 붙이는 이유: PDF 안의 스크립트나 외부 자원 요청을 막는다.
     * 첨부는 어차피 옥텟 스트림으로만 내리지만, 미리보기는 브라우저가 실제로 렌더하므로
     * 여기서만 별도의 울타리가 필요하다.
     */
    private void streamInline(FileItem item, DocumentViewService.Preview preview,
            HttpServletResponse response) {
        if (!Files.isReadable(preview.path())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        response.setContentType(preview.contentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition(item, false));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        // 미리보기 응답 자체에도 울타리를 친다 — 문서가 스크립트를 품고 있어도 실행되지 않게
        response.setHeader("Content-Security-Policy",
                "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; sandbox");
        try {
            response.setContentLengthLong(Files.size(preview.path()));
            try (OutputStream out = response.getOutputStream()) {
                Files.copy(preview.path(), out);
            }
        } catch (IOException e) {
            log.warn("미리보기 전송 중단 file={}: {}", item.getFileId(), e.toString());
        }
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
