package com.gonet.primary.board.dto;

import com.gonet.common.audit.Auditable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 게시글 댓글 — 본문은 항상 평문이다(HTML 을 허용하지 않는다). */
@Getter
@Setter
public class BbsCommentDto extends Auditable {

    private String commentId;
    private String articleId;
    /** 대댓글의 상위. 최상위 댓글은 null. */
    private String parentCommentId;

    private String writerUserId;
    private String writerUserType;
    private String writerName;
    private String writerPassword;

    private String content;
    private Long likeCount;
    private Integer reportCount;
    private String secretYn;
    /** 1 = 최상위, 2 = 대댓글. 그 이상은 만들지 않는다(서비스가 평탄화). */
    private Integer depth;
    private String clientIp;
    private String status;
    private String clientIpView;
    private LocalDateTime createdAtView;

    public boolean isSecret() {
        return "Y".equals(secretYn);
    }

    public boolean isHidden() {
        return "HIDDEN".equals(status) || "REPORTED".equals(status);
    }
}
