package com.gonet.primary.board.dto;

import com.gonet.common.web.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 게시글 검색 조건.
 *
 * <p>검색은 LIKE 다(P9-7 확정 — 색인 테이블·FULLTEXT 미도입). 대신 게시판과 상태로 먼저
 * 좁힌 뒤 LIKE 를 걸어 선행 인덱스({@code idx_article_bbs_status})가 먹게 한다.
 */
@Getter
@Setter
public class BbsArticleSearch extends PageRequest {

    /** 필수 — 게시글은 항상 한 게시판 안에서 찾는다. */
    private String bbsMasterId;
    private String categoryId;
    private String status;
    /** TITLE | CONTENT | WRITER — 비우면 제목+본문. */
    private String searchType;
    private String keyword;
    private String noticeYn;
}
