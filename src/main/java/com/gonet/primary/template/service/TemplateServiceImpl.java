package com.gonet.primary.template.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.config.CacheConfig;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.template.dto.TemplateAdmDto;
import com.gonet.primary.template.mapper.TemplateMapper;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 템플릿 관리.
 *
 * <p>{@code template_code} 는 {@code /tmpl/css/{code}.css} 파일 1장과 1:1 이다 —
 * 파일이 없으면 사이트가 스타일 없이 뜨고 다음 기동에서 LayoutSmokeRunner 가 이를 잡는다.
 * 기본 레이아웃(default_layout_id)은 NOT NULL 이라 등록 시 반드시 고르게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class TemplateServiceImpl extends AbstractCmsService implements TemplateService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9-]{1,50}$");

    private final TemplateMapper templateMapper;

    @Override
    public PageResult<TemplateAdmDto> getAdmPage(PageRequest cond) {
        return new PageResult<>(templateMapper.findPage(cond), templateMapper.countPage(cond),
                cond.getPage(), cond.getSize());
    }

    @Override
    public TemplateAdmDto getAdm(String templateId) {
        return templateMapper.findById(templateId);
    }

    @Override
    public List<TemplateAdmDto> getAllForSelect() {
        return templateMapper.findAllForSelect();
    }

    /** 쓰기 — writable override. 시각 언어가 바뀌면 렌더 컨텍스트 캐시를 비운다. */
    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public String saveAdm(TemplateAdmDto template) {
        String code = template.getTemplateCode();
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "템플릿 코드는 소문자·숫자·하이픈 1~50자여야 합니다 (CSS 파일명으로 사용).");
        }
        if (template.getTemplateName() == null || template.getTemplateName().isBlank()) {
            throw new IllegalArgumentException("템플릿 이름은 필수입니다.");
        }
        if (template.getDefaultLayoutId() == null || template.getDefaultLayoutId().isBlank()) {
            throw new IllegalArgumentException(
                    "기본 레이아웃은 필수입니다 (사이트가 레이아웃을 고르지 않을 때 쓰입니다).");
        }
        if (templateMapper.countByCode(code, template.getTemplateId()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 템플릿 코드입니다: " + code);
        }

        if (template.getTemplateId() == null || template.getTemplateId().isBlank()) {
            template.setTemplateId(Uid.next(UidPrefix.TPL));
            templateMapper.insert(template);
        } else {
            templateMapper.update(template);
        }
        return template.getTemplateId();
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public void deleteAdm(String templateId) {
        int references = templateMapper.countReferences(templateId);
        if (references > 0) {
            throw new IllegalArgumentException(
                    "사이트·테마 " + references + "건이 이 템플릿을 참조 중이라 삭제할 수 없습니다.");
        }
        templateMapper.softDelete(templateId);
    }
}
