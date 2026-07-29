package com.gonet.primary.board.mapper;

import com.gonet.primary.board.dto.BbsArticleAdmDto;
import com.gonet.primary.board.dto.BbsArticleSearch;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** tb_bbs_article CRUD. */
@EgovMapper
public interface BbsArticleMapper {

    List<BbsArticleAdmDto> findPage(BbsArticleSearch cond);

    int countPage(BbsArticleSearch cond);

    BbsArticleAdmDto findById(@Param("articleId") String articleId);

    int insert(BbsArticleAdmDto article);

    int update(BbsArticleAdmDto article);

    int softDelete(@Param("articleId") String articleId,
                   @Param("updatedBy") String updatedBy,
                   @Param("updatedIp") String updatedIp);

    /**
     * 조회수 증가.
     *
     * <p><b>감사컬럼을 건드리지 않는다</b> — 읽기는 수정이 아니다. 여기서 updated_by 를
     * 덮으면 "마지막으로 글을 고친 사람" 이 조회자로 바뀌어 이력이 못 쓰게 된다.
     * (그래서 AuditInterceptor 가 잡지 않도록 DTO 가 아닌 파라미터로 호출한다)
     */
    int increaseViewCount(@Param("articleId") String articleId);

    /** 댓글 수 재계산 — 증분이 아니라 재계산이다(모더레이션·삭제로 어긋나는 것 방지). */
    int refreshCommentCount(@Param("articleId") String articleId);
}
