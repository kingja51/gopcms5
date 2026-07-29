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

    /** 단일 게시판 조회의 기준. 통합 게시판이면 {@link #bbsMasterIds} 가 대신 쓰인다. */
    private String bbsMasterId;
    /**
     * 통합 게시판(B7)의 대상 목록 — 채워져 있으면 이 집합에서 찾는다.
     *
     * <p>합본은 <b>읽기 전용</b>이다. 어느 게시판의 글인지는 목록에 함께 보여준다.
     */
    private java.util.List<String> bbsMasterIds;
    private String categoryId;
    private String status;
    /** TITLE | CONTENT | WRITER — 비우면 제목+본문. */
    private String searchType;
    private String keyword;
    private String noticeYn;
}
