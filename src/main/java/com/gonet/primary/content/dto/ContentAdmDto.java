package com.gonet.primary.content.dto;

import com.gonet.common.audit.Auditable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 컨텐츠 관리 DTO — tb_content 편집 컬럼.
 *
 * <p>렌더용 {@link ContentDto} 와 분리한 이유: 저쪽은 "게시 중인 것만" 보는 뷰 계약이라
 * 초안·예약·게시종료 상태를 표현하지 못한다. 관리 화면은 그 상태들을 다뤄야 한다.
 */
@Getter
@Setter
public class ContentAdmDto extends Auditable {

    private String contentId;
    private String siteId;
    private String siteCode;

    /** 연결 메뉴 — breadcrumb 경로 해석의 근거 */
    private String menuId;

    private String title;
    private String slug;
    private String body;
    private String summary;
    private String metaKeywords;
    private String metaDescription;

    /** DRAFT | PUBLISHED | ARCHIVED */
    private String status;

    private LocalDateTime publishedAt;
    private LocalDateTime publishScheduledAt;
    private LocalDateTime unpublishAt;
    private Long viewCount;
    private Integer versionNo;
    private LocalDateTime updatedAt;

    /** 목록 표시용 */
    private String menuName;
}
