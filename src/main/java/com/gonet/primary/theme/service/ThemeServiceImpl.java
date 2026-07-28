package com.gonet.primary.theme.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.config.CacheConfig;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.theme.dto.ThemeAdmDto;
import com.gonet.primary.theme.mapper.ThemeMapper;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테마 관리.
 *
 * <p>{@code css_class} 는 전역 CSS 에 이미 정의돼 있어야 의미가 생긴다(파일을 만들지 않는 축) —
 * 오타를 내면 조용히 기본 브랜드로 보이므로 형식만이라도 강제한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class ThemeServiceImpl extends AbstractCmsService implements ThemeService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9-]{1,30}$");

    /** {@code <html class>} 에 들어갈 값 — 빈 문자열(기본 브랜드) 허용. */
    private static final Pattern CSS_CLASS_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{0,50}$");

    private final ThemeMapper themeMapper;

    @Override
    public PageResult<ThemeAdmDto> getAdmPage(PageRequest cond) {
        return new PageResult<>(themeMapper.findPage(cond), themeMapper.countPage(cond),
                cond.getPage(), cond.getSize());
    }

    @Override
    public ThemeAdmDto getAdm(String themeId) {
        return themeMapper.findById(themeId);
    }

    @Override
    public List<ThemeAdmDto> getAllForSelect() {
        return themeMapper.findAllForSelect();
    }

    @Override
    public boolean belongsToTemplate(String themeId, String templateId) {
        return themeId == null || templateId == null
                || themeMapper.countByIdAndTemplate(themeId, templateId) > 0;
    }

    /** 쓰기 — writable override. 색이 바뀌면 렌더 컨텍스트 캐시를 비운다. */
    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public String saveAdm(ThemeAdmDto theme) {
        if (theme.getTemplateId() == null || theme.getTemplateId().isBlank()) {
            throw new IllegalArgumentException("소속 템플릿은 필수입니다 (테마는 템플릿에 종속됩니다).");
        }
        String code = theme.getThemeCode();
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("테마 코드는 소문자·숫자·하이픈 1~30자여야 합니다.");
        }
        if (theme.getThemeName() == null || theme.getThemeName().isBlank()) {
            throw new IllegalArgumentException("테마 이름은 필수입니다.");
        }
        if (theme.getCssClass() == null) {
            theme.setCssClass("");
        }
        if (!CSS_CLASS_PATTERN.matcher(theme.getCssClass()).matches()) {
            throw new IllegalArgumentException(
                    "CSS 클래스는 영문·숫자·하이픈·밑줄 50자 이내여야 합니다 (빈 값 = 템플릿 기본 브랜드).");
        }
        if (themeMapper.countByCode(theme.getTemplateId(), code, theme.getThemeId()) > 0) {
            throw new IllegalArgumentException("이 템플릿에 같은 테마 코드가 이미 있습니다: " + code);
        }

        if (theme.getThemeId() == null || theme.getThemeId().isBlank()) {
            theme.setThemeId(Uid.next(UidPrefix.THM));
            themeMapper.insert(theme);
        } else {
            themeMapper.update(theme);
        }
        return theme.getThemeId();
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public void deleteAdm(String themeId) {
        int references = themeMapper.countReferences(themeId);
        if (references > 0) {
            throw new IllegalArgumentException(
                    "사이트 " + references + "곳이 이 테마를 쓰고 있어 삭제할 수 없습니다.");
        }
        themeMapper.softDelete(themeId);
    }
}
