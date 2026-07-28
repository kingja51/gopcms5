package com.gonet.primary.content.service;

import com.gonet.primary.content.dto.ContentDto;
import java.util.List;

public interface ContentService {

    /** 게시 중 컨텐츠 조회 + 조회수 증가(쓰기) — 미존재 시 null. */
    ContentDto viewBySlug(String siteId, String slug);

    /** 최신 게시 컨텐츠 (홈 새소식) */
    List<ContentDto> getRecent(String siteId, int limit);
}
