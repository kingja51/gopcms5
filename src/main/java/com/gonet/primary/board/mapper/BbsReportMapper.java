package com.gonet.primary.board.mapper;

import com.gonet.primary.board.dto.BbsReportDto;
import com.gonet.primary.board.dto.BbsReportSearch;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** tb_bbs_report — 신고 접수와 검토 큐. */
@EgovMapper
public interface BbsReportMapper {

    int insert(BbsReportDto report);

    /** 같은 사람이 같은 대상을 다시 신고했는지 — UNIQUE 충돌을 예외 대신 안내로 바꾼다. */
    int countByReporter(@Param("targetType") String targetType,
                        @Param("targetId") String targetId,
                        @Param("reporterUserId") String reporterUserId);

    /** 대상의 유효 신고 수 — 기각(REJECTED)은 세지 않는다. */
    int countActive(@Param("targetType") String targetType,
                    @Param("targetId") String targetId);

    /* ── 관리자 검토 큐 ─────────────────────────────────────────────────── */

    List<BbsReportDto> findPage(BbsReportSearch cond);

    int countPage(BbsReportSearch cond);

    BbsReportDto findById(@Param("reportId") String reportId);

    int review(@Param("reportId") String reportId,
               @Param("status") String status,
               @Param("reviewNote") String reviewNote,
               @Param("reviewedBy") String reviewedBy,
               @Param("updatedIp") String updatedIp);

    /* ── 대상 상태 전환 (임계 도달·검토 결과) ──────────────────────────── */

    int updateArticleStatus(@Param("articleId") String articleId,
                            @Param("status") String status,
                            @Param("updatedBy") String updatedBy,
                            @Param("updatedIp") String updatedIp);

    int updateCommentStatus(@Param("commentId") String commentId,
                            @Param("status") String status,
                            @Param("updatedBy") String updatedBy,
                            @Param("updatedIp") String updatedIp);

    int refreshArticleReportCount(@Param("articleId") String articleId);

    int refreshCommentReportCount(@Param("commentId") String commentId);
}
