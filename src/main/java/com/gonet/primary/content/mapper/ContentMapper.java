package com.gonet.primary.content.mapper;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.content.dto.ContentAdmDto;
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

    /* ── 관리 CRUD (P7) ─────────────────────────────────────────────────── */

    List<ContentAdmDto> findPage(@Param("siteId") String siteId,
            @Param("cond") PageRequest cond);

    int countPage(@Param("siteId") String siteId, @Param("cond") PageRequest cond);

    ContentAdmDto findAdmById(@Param("contentId") String contentId);

    /** slug 는 사이트 안에서 유일 — URL 계약(/{siteCode}/{slug})의 근거. */
    int countBySlug(@Param("siteId") String siteId, @Param("slug") String slug,
            @Param("excludeId") String excludeId);

    /** 메뉴 연결 선택 상자용 — 사이트의 컨텐츠 (id, title, slug). */
    List<ContentAdmDto> findAllForSelect(@Param("siteId") String siteId);

    int insert(ContentAdmDto content);

    int update(ContentAdmDto content);

    int softDelete(@Param("contentId") String contentId,
                   @Param("updatedBy") String updatedBy,
                   @Param("updatedIp") String updatedIp);
}
