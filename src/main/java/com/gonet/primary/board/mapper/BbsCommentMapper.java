package com.gonet.primary.board.mapper;

import com.gonet.primary.board.dto.BbsCommentDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** tb_bbs_comment CRUD. */
@EgovMapper
public interface BbsCommentMapper {

    /** 한 글의 댓글 전부 — 트리 정렬(부모 바로 아래 자식). 페이징하지 않는다. */
    List<BbsCommentDto> findByArticle(@Param("articleId") String articleId);

    BbsCommentDto findById(@Param("commentId") String commentId);

    int insert(BbsCommentDto comment);

    int update(BbsCommentDto comment);

    /** 모더레이션 — 상태만 바꾼다(본문은 남겨 근거를 보존). */
    int updateStatus(@Param("commentId") String commentId,
                     @Param("status") String status,
                     @Param("updatedBy") String updatedBy,
                     @Param("updatedIp") String updatedIp);

    int softDelete(@Param("commentId") String commentId,
                   @Param("updatedBy") String updatedBy,
                   @Param("updatedIp") String updatedIp);

    /** 자식 댓글까지 함께 지운다 — 부모가 사라진 대댓글이 떠 있으면 맥락을 잃는다. */
    int softDeleteChildren(@Param("parentCommentId") String parentCommentId,
                           @Param("updatedBy") String updatedBy,
                           @Param("updatedIp") String updatedIp);
}
