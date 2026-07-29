package com.gonet.primary.board.service;

import com.gonet.primary.board.dto.BbsCommentDto;
import java.util.List;

/** 댓글 — 작성·삭제·모더레이션. */
public interface BoardCommentService {

    /** 한 글의 댓글 전부(트리 정렬). 비밀댓글 열람 판정은 {@link #canRead} 로 따로 묻는다. */
    List<BbsCommentDto> getByArticle(String articleId);

    /** 작성. depth 는 상위 댓글에서 계산하며 2를 넘기지 않는다. */
    void write(BbsCommentDto comment);

    /** 삭제(soft) — 자식 대댓글도 함께 정리하고 댓글 수를 재계산한다. */
    void delete(String commentId);

    /** 모더레이션 — 숨김/복원. 본문은 지우지 않는다(근거 보존). */
    void moderate(String commentId, String status);

    /** 비밀댓글 열람 가능 여부 — 작성자·글쓴이·담당자. */
    boolean canRead(BbsCommentDto comment, String articleWriterUserId);
}
