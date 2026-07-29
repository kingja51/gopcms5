package com.gonet.primary.file.controller;

import com.gonet.common.file.dto.UploadCategory;
import com.gonet.common.file.security.UploadValidationException;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.service.FileService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 업로드 REST — file-picker 프래그먼트가 부르는 유일한 업로드 경로.
 *
 * <p>업로드 경로를 도메인마다 만들지 않는 것이 핵심이다. 경로가 여럿이면 그중 하나만
 * 방어가 허술해도 전체가 뚫린다(선행 프로젝트 웹쉘 침해의 구조적 원인).
 *
 * <p>응답은 순수 JSON — 화면 조각은 Usr/Adm 컨트롤러의 몫이라는 규약을 지킨다.
 */
@RestController
@RequestMapping("/api/v1/file")
@RequiredArgsConstructor
public class FileApiController {

    private final FileService fileService;

    /**
     * 단일 파일 업로드. picker 는 여러 파일을 <b>병렬 요청</b>으로 보낸다 —
     * 한 요청에 20개를 묶으면 1개가 거부될 때 나머지도 함께 실패하고,
     * 진행률도 파일 단위로 보여줄 수 없다.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam("entityType") String entityType,
            @RequestParam("entityId") String entityId,
            @RequestParam(value = "siteId", required = false) String siteId) {

        // downloadAuth 는 <b>받지 않는다</b> — 업로드 요청이 공개 범위를 정할 수 있으면
        // 남의 첨부 묶음을 공개로 낮출 수 있다(실측으로 확인한 결함). 정책은 서버가 정한다.
        FileItem saved = fileService.upload(file, UploadCategory.of(category),
                entityType, entityId, siteId);
        return ResponseEntity.ok(toJson(saved));
    }

    /** 에디터 본문 이미지 — 이미지 전용 + ROLE_STAFF. 첨부와 경로를 분리한다. */
    @PostMapping("/image")
    public ResponseEntity<Map<String, Object>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("entityId") String entityId,
            @RequestParam(value = "siteId", required = false) String siteId) {

        FileItem saved = fileService.upload(file, UploadCategory.IMAGE,
                com.gonet.primary.file.dto.FileEntityType.EDITOR, entityId, siteId);
        return ResponseEntity.ok(toJson(saved));
    }

    private Map<String, Object> toJson(FileItem f) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileId", f.getFileId());
        body.put("fileGroupId", f.getFileGroupId());
        body.put("originalName", f.getOriginalName());
        body.put("sizeBytes", f.getSizeBytes());
        body.put("extension", f.getExtension());
        body.put("image", f.isImage());
        body.put("url", "/file/" + f.getFileId());
        body.put("thumbUrl", f.isImage() ? "/file/" + f.getFileId() + "/thumb" : null);
        return body;
    }

    /** 정책 위반은 400 + 사용자에게 보여줄 문장. 스택은 내보내지 않는다. */
    @ExceptionHandler(UploadValidationException.class)
    public ResponseEntity<Map<String, Object>> onValidation(UploadValidationException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> onDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
    }
}
