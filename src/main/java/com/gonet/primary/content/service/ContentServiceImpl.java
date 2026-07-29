package com.gonet.primary.content.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.config.CacheConfig;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.content.controller.ContentUsrController;
import com.gonet.primary.content.dto.ContentAdmDto;
import com.gonet.primary.content.dto.ContentDto;
import com.gonet.primary.content.mapper.ContentMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class ContentServiceImpl extends AbstractCmsService implements ContentService {

    /** conventions §5 — ContentUsrController 의 캐치올 매핑 정규식과 같은 값이어야 한다. */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]{1,200}$");

    private final ContentMapper contentMapper;

    /** 조회수 증가 포함 — 쓰기 메서드는 반드시 writable override (CLAUDE.md 트랜잭션 함정). */
    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public ContentDto viewBySlug(String siteId, String slug) {
        ContentDto content = contentMapper.findPublishedBySlug(siteId, slug);
        if (content != null) {
            contentMapper.increaseViewCount(content.getContentId());
        }
        return content;
    }

    @Override
    public List<ContentDto> getRecent(String siteId, int limit) {
        return contentMapper.findRecentPublished(siteId, limit);
    }

    /* ── 관리 CRUD (P7) ─────────────────────────────────────────────────── */

    @Override
    public PageResult<ContentAdmDto> getAdmPage(String siteId, PageRequest cond) {
        return new PageResult<>(contentMapper.findPage(siteId, cond),
                contentMapper.countPage(siteId, cond), cond.getPage(), cond.getSize());
    }

    @Override
    public ContentAdmDto getAdm(String contentId) {
        return contentMapper.findAdmById(contentId);
    }

    @Override
    public List<ContentAdmDto> getAllForSelect(String siteId) {
        return contentMapper.findAllForSelect(siteId);
    }

    /** 쓰기 — writable override. 컨텐츠는 메뉴 href 해석 재료라 캐시를 함께 비운다. */
    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public String saveAdm(ContentAdmDto content) {
        validate(content);
        // 게시 상태로 바꾸는 순간 게시일시가 비어 있으면 지금으로 — 목록 정렬·최신글이 어긋나지 않게
        if ("PUBLISHED".equals(content.getStatus()) && content.getPublishedAt() == null) {
            content.setPublishedAt(LocalDateTime.now());
        }
        if (content.getContentId() == null || content.getContentId().isBlank()) {
            content.setContentId(Uid.next(UidPrefix.CNT));
            contentMapper.insert(content);
        } else {
            contentMapper.update(content);
        }
        return content.getContentId();
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public void deleteAdm(String contentId) {
        contentMapper.softDelete(contentId,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
    }

    /**
     * slug 는 URL 그 자체다 (conventions §5) — 패턴·예약어·사이트 내 유일성을 여기서 막는다.
     * 컨트롤러의 캐치올 매핑이 같은 정규식을 쓰므로, 어긋나면 등록은 되고 열리지는 않는 페이지가 생긴다.
     */
    private void validate(ContentAdmDto content) {
        if (content.getSiteId() == null || content.getSiteId().isBlank()) {
            throw new IllegalArgumentException("사이트는 필수입니다.");
        }
        if (content.getTitle() == null || content.getTitle().isBlank()) {
            throw new IllegalArgumentException("제목은 필수입니다.");
        }
        String slug = content.getSlug();
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "slug 는 소문자·숫자·하이픈 1~200자여야 합니다 (URL 로 그대로 쓰입니다).");
        }
        if (ContentUsrController.RESERVED_SLUGS.contains(slug)) {
            throw new IllegalArgumentException(
                    "'" + slug + "' 는 예약된 주소라 slug 로 쓸 수 없습니다.");
        }
        if (contentMapper.countBySlug(content.getSiteId(), slug, content.getContentId()) > 0) {
            throw new IllegalArgumentException("이 사이트에 같은 slug 가 이미 있습니다: " + slug);
        }
    }
}
