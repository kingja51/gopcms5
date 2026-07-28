package com.gonet.primary.template.service;

import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.primary.template.dto.TemplateAdmDto;
import java.util.List;

/** 템플릿(시각 언어 축) 관리 — CSS 1장에 대응하는 축. */
public interface TemplateService {

    PageResult<TemplateAdmDto> getAdmPage(PageRequest cond);

    TemplateAdmDto getAdm(String templateId);

    List<TemplateAdmDto> getAllForSelect();

    String saveAdm(TemplateAdmDto template);

    void deleteAdm(String templateId);
}
