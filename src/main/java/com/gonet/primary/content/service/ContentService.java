package com.gonet.primary.content.service;

import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.primary.content.dto.ContentAdmDto;
import com.gonet.primary.content.dto.ContentDto;
import java.util.List;

public interface ContentService {

    /** 게시 중 컨텐츠 조회 + 조회수 증가(쓰기) — 미존재 시 null. */
    ContentDto viewBySlug(String siteId, String slug);

    /** 최신 게시 컨텐츠 (홈 새소식) */
    List<ContentDto> getRecent(String siteId, int limit);

    /* ── 관리 CRUD (P7) ─────────────────────────────────────────────────── */

    PageResult<ContentAdmDto> getAdmPage(String siteId, PageRequest cond);

    ContentAdmDto getAdm(String contentId);

    /** 메뉴 연결 선택 상자용 — 사이트의 컨텐츠 전체. */
    List<ContentAdmDto> getAllForSelect(String siteId);

    /**
     * 등록·수정. slug 는 URL 계약(conventions §5)을 그대로 강제한다 —
     * 소문자·숫자·하이픈, 예약어 금지, 사이트 내 유일.
     *
     * @throws IllegalArgumentException 제목·slug 규칙 위반, 중복
     */
    String saveAdm(ContentAdmDto content);

    void deleteAdm(String contentId);
}
