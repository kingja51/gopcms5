package com.gonet.primary.content.mapper;

import com.gonet.primary.content.dto.ContentDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

@EgovMapper
public interface ContentMapper {

    /** 게시 중(PUBLISHED + 게시기간 내) 컨텐츠 slug 조회 — 미존재 시 null. */
    ContentDto findPublishedBySlug(@Param("siteId") String siteId, @Param("slug") String slug);

    /** 최신 게시 컨텐츠 (홈 새소식) */
    List<ContentDto> findRecentPublished(@Param("siteId") String siteId,
            @Param("limit") int limit);

    int increaseViewCount(@Param("contentId") String contentId);
}
