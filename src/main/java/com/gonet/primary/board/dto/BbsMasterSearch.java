package com.gonet.primary.board.dto;

import com.gonet.common.web.PageRequest;
import lombok.Getter;
import lombok.Setter;

/** 게시판 목록 검색 조건. */
@Getter
@Setter
public class BbsMasterSearch extends PageRequest {

    /** 사이트별 보기 — 멀티사이트라 전체 목록은 금방 길어진다. */
    private String siteId;
    private String bbsType;
}
