package com.gonet.primary.content.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.content.dto.ContentDto;
import com.gonet.primary.content.mapper.ContentMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class ContentServiceImpl extends AbstractCmsService implements ContentService {

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
}
