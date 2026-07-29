package com.gonet.primary.board.service;

import com.gonet.common.web.PageResult;
import com.gonet.primary.board.dto.BbsReportDto;
import com.gonet.primary.board.dto.BbsReportSearch;

/** 신고 접수와 관리자 검토. */
public interface BoardReportService {

    /**
     * 신고 접수. 같은 사람이 같은 대상을 다시 신고할 수 없다.
     *
     * @return 접수 후 그 대상의 유효 신고 수(임계 도달 여부 안내용)
     */
    int report(String targetType, String targetId, String reasonCode,
               String reasonText, String sourceUrl);

    PageResult<BbsReportDto> getPage(BbsReportSearch cond);

    /**
     * 검토 처리.
     *
     * @param status     REVIEWED(조치함) 또는 REJECTED(문제 없음)
     * @param hideTarget 대상을 숨길지 — REVIEWED 일 때만 의미가 있다
     */
    void review(String reportId, String status, String reviewNote, boolean hideTarget);
}
