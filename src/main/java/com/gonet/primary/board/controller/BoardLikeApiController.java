package com.gonet.primary.board.controller;

import com.gonet.primary.board.service.BoardLikeService;
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
 * 좋아요 토글 REST — 레이아웃 7종의 "좋아요/신고 밴드" 가 부르는 경로.
 *
 * <p>순수 JSON 만 돌려준다(화면 조각은 Usr/Adm 의 몫). 버튼 상태와 숫자를 함께 주는 이유는
 * 클라이언트가 숫자를 직접 ±1 하지 않게 하려는 것이다 — 다른 사람이 누른 사이에 어긋난다.
 */
@RestController
@RequestMapping("/api/v1/board/like")
@RequiredArgsConstructor
public class BoardLikeApiController {

    private final BoardLikeService boardLikeService;

    @PostMapping
    public Map<String, Object> toggle(@RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam(required = false) String sourceUrl) {
        BoardLikeService.LikeResult result =
                boardLikeService.toggle(targetType, targetId, sourceUrl);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("liked", result.liked());
        body.put("count", result.count());
        return body;
    }

    /** 잘못된 대상 유형·식별자는 400 — 서버 오류가 아니라 요청 오류다. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }
}
