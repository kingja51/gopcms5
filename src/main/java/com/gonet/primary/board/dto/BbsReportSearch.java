package com.gonet.primary.board.dto;

import com.gonet.common.web.PageRequest;
import lombok.Getter;
import lombok.Setter;

/** 신고 검토 큐 조회 조건. 기본은 처리 대기(PENDING) — 할 일이 먼저 보여야 한다. */
@Getter
@Setter
public class BbsReportSearch extends PageRequest {

    private String status = "PENDING";
    private String targetType;
    private String reasonCode;
}
