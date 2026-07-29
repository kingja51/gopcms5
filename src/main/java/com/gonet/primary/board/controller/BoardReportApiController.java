package com.gonet.primary.board.controller;

import com.gonet.primary.board.service.BoardReportService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고 접수 REST.
 *
 * <p>응답에 <b>누적 신고 수를 담지 않는다</b>. 신고자가 "몇 건 남았는지" 를 알 수 있으면
 * 여럿이 맞춰 임계를 채우는 조직적 신고가 쉬워진다. 접수 여부만 알린다.
 */
@RestController
@RequestMapping("/api/v1/board/report")
@RequiredArgsConstructor
public class BoardReportApiController {

    private final BoardReportService boardReportService;

    @PostMapping
    public Map<String, Object> report(@RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam String reasonCode,
            @RequestParam(required = false) String reasonText,
            @RequestParam(required = false) String sourceUrl) {
        boardReportService.report(targetType, targetId, reasonCode, reasonText, sourceUrl);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accepted", true);
        body.put("message", "신고가 접수되었습니다. 관리자 검토 후 조치됩니다.");
        return body;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }

    /** 중복 신고 — 실패가 아니라 "이미 받았다" 는 상태다(409). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage()));
    }
}
