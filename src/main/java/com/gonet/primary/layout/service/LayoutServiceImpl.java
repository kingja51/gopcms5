package com.gonet.primary.layout.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.config.CacheConfig;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.layout.dto.LayoutAdmDto;
import com.gonet.primary.layout.mapper.LayoutMapper;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 레이아웃 관리.
 *
 * <p>{@code layout_code} 는 뷰 폴더명이라 파일시스템과 짝이 맞아야 한다 —
 * 짝이 깨지면 사이트가 렌더되지 않고 다음 기동에서 LayoutSmokeRunner 가 부팅을 세운다.
 * 그래서 <b>참조 중인 레이아웃은 삭제를 막고</b>, 코드 변경은 폼에서 경고한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class LayoutServiceImpl extends AbstractCmsService implements LayoutService {

    /** 뷰 폴더명으로 쓰이므로 경로 문자를 배제한다. */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9-]{1,50}$");

    private final LayoutMapper layoutMapper;

    @Override
    public PageResult<LayoutAdmDto> getAdmPage(PageRequest cond) {
        return new PageResult<>(layoutMapper.findPage(cond), layoutMapper.countPage(cond),
                cond.getPage(), cond.getSize());
    }

    @Override
    public LayoutAdmDto getAdm(String layoutId) {
        return layoutMapper.findById(layoutId);
    }

    @Override
    public List<LayoutAdmDto> getAllForSelect() {
        return layoutMapper.findAllForSelect();
    }

    /** 쓰기 — writable override. 3축이 바뀌면 렌더 컨텍스트 캐시도 함께 비운다. */
    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public String saveAdm(LayoutAdmDto layout) {
        String code = layout.getLayoutCode();
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "레이아웃 코드는 소문자·숫자·하이픈 1~50자여야 합니다 (뷰 폴더명으로 사용).");
        }
        if (layout.getLayoutName() == null || layout.getLayoutName().isBlank()) {
            throw new IllegalArgumentException("레이아웃 이름은 필수입니다.");
        }
        if (layoutMapper.countByCode(code, layout.getLayoutId()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 레이아웃 코드입니다: " + code);
        }

        if (layout.getLayoutId() == null || layout.getLayoutId().isBlank()) {
            layout.setLayoutId(Uid.next(UidPrefix.LAY));
            layoutMapper.insert(layout);
        } else {
            layoutMapper.update(layout);
        }
        return layout.getLayoutId();
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public void deleteAdm(String layoutId) {
        int references = layoutMapper.countReferences(layoutId);
        if (references > 0) {
            throw new IllegalArgumentException(
                    "사이트·템플릿 " + references + "건이 이 레이아웃을 참조 중이라 삭제할 수 없습니다.");
        }
        layoutMapper.softDelete(layoutId,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
    }
}
