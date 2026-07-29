package com.gonet.primary.board.mapper;

import com.gonet.primary.board.dto.BbsCategoryAdmDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 게시판 카테고리 CRUD — tb_bbs_category. */
@EgovMapper
public interface BbsCategoryMapper {

    /** 게시판 안의 분류 목록 — 수가 적어 페이징하지 않는다. */
    List<BbsCategoryAdmDto> findByBoard(@Param("bbsMasterId") String bbsMasterId);

    BbsCategoryAdmDto findById(@Param("categoryId") String categoryId);

    int countByCode(@Param("bbsMasterId") String bbsMasterId,
                    @Param("categoryCode") String categoryCode,
                    @Param("excludeId") String excludeId);

    /** 삭제 차단 판단 — 이 분류를 쓰는 글이 있으면 지우지 않는다. */
    int countArticles(@Param("categoryId") String categoryId);

    int insert(BbsCategoryAdmDto category);

    int update(BbsCategoryAdmDto category);

    int softDelete(@Param("categoryId") String categoryId,
                   @Param("updatedBy") String updatedBy,
                   @Param("updatedIp") String updatedIp);
}
