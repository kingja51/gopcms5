package com.gonet.primary.board.mapper;

import com.gonet.primary.board.dto.BbsLikeDto;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** tb_bbs_like — 좋아요 토글. */
@EgovMapper
public interface BbsLikeMapper {

    /**
     * 토글 upsert — 없으면 켜고, 있으면 뒤집는다.
     *
     * <p>조회 후 분기하지 않고 <b>한 문장</b>으로 처리한다. 같은 사용자가 빠르게 두 번
     * 누르면 조회-분기 방식은 UNIQUE 충돌로 500 이 난다(경합). ON DUPLICATE KEY 로
     * DB 가 직렬화하게 두는 것이 안전하다.
     */
    int toggle(BbsLikeDto like);

    /** 토글 후 현재 상태 — 응답으로 돌려줄 on/off. */
    String findState(@Param("targetType") String targetType,
                     @Param("targetId") String targetId,
                     @Param("userId") String userId);

    /** 대상의 활성 좋아요 수 — 비정규화 컬럼 재계산의 원천. */
    long countActive(@Param("targetType") String targetType,
                     @Param("targetId") String targetId);

    /** 게시글 like_count 재계산 (감사컬럼·updated_at 미갱신). */
    int refreshArticleLikeCount(@Param("articleId") String articleId);

    /** 댓글 like_count 재계산. */
    int refreshCommentLikeCount(@Param("commentId") String commentId);
}
