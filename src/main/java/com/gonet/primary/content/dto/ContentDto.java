package com.gonet.primary.content.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 컨텐츠 조회 DTO — 뷰 계약(P3 템플릿): title·body·summary·publishedAt·viewCount·slug. */
@Getter
@Setter
public class ContentDto {

    private String contentId;
    private String menuId;
    private String title;
    private String slug;
    private String body;      // 저장측 sanitize 완료 HTML — 뷰에서 th:utext
    private String summary;
    private LocalDateTime publishedAt;
    private Long viewCount;
}
