package com.gonet.primary.board.dto;

import com.gonet.common.audit.Auditable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 신고 — 게시글·댓글·컨텐츠 공용(다형 참조). */
@Getter
@Setter
public class BbsReportDto extends Auditable {

    private String reportId;
    private String targetType;
    private String targetId;
    private String reporterUserId;
    private String reporterUserType;
    private String sourceUrl;
    private String menuId;
    private String reasonCode;
    private String reasonText;
    private String status;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;

    /* 검토 큐 표시용 — 대상 본문을 함께 보여줘야 판단할 수 있다 */
    private String targetTitle;
    private String targetWriter;
    private String targetStatus;
    private String bbsMasterId;
    private String bbsName;
    private String articleId;
    private LocalDateTime createdAtView;
    private int reportCount;

    public boolean isPending() {
        return "PENDING".equals(status);
    }
}
