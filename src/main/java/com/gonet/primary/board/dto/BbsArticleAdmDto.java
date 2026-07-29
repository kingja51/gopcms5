package com.gonet.primary.board.dto;

import com.gonet.common.audit.Auditable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 게시글 — 목록·상세·폼 공용. */
@Getter
@Setter
public class BbsArticleAdmDto extends Auditable {

    private String articleId;
    private String bbsMasterId;
    private String categoryId;
    /** 첨부 묶음 — 첨부가 하나도 없으면 NULL 로 둔다(빈 그룹을 남기지 않는다). */
    private String fileGroupId;

    private String writerUserId;
    private String writerUserType;
    private String writerName;
    /** 비로그인 글의 수정·삭제 비밀번호(BCrypt). 관리자 화면에서는 쓰지 않는다. */
    private String writerPassword;

    private String title;
    private String content;

    /* 타입별 부가 필드 — BODO(보도자료)·YOUTUBE 에서만 채운다 */
    private String pressName;
    private String linkUrl;
    private LocalDate publishedAt;

    private String noticeYn;
    private String secretYn;
    private Long viewCount;
    private Long likeCount;
    private Integer reportCount;
    private Integer commentCount;
    private String clientIp;
    private String status;

    /* 목록 표시용 조인 값 */
    private String categoryName;
    private String bbsCode;
    private String bbsName;
    private String siteCode;
    private LocalDateTime createdAtView;
    private int fileCount;
    /** 갤러리·영상 목록의 썸네일 — 첨부 중 첫 이미지. 없으면 null(자리표시자로 대체). */
    private String thumbFileId;

    /** 폼에서 넘어오는 첨부 파일 ID CSV — picker 가 hidden 으로 싣는다. */
    private String attachments;

    public boolean isNotice() {
        return "Y".equals(noticeYn);
    }

    public boolean isSecret() {
        return "Y".equals(secretYn);
    }
}
