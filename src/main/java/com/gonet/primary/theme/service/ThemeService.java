package com.gonet.primary.theme.service;

import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.primary.theme.dto.ThemeAdmDto;
import java.util.List;

/** 테마(색 축) 관리 — 템플릿 종속(복합 FK). */
public interface ThemeService {

    PageResult<ThemeAdmDto> getAdmPage(PageRequest cond);

    ThemeAdmDto getAdm(String themeId);

    List<ThemeAdmDto> getAllForSelect();

    /**
     * (테마, 템플릿) 짝이 성립하는가 — 사이트 저장 시 사전 검증용.
     * DB 도 복합 FK 로 막지만, 그건 500 이 되므로 여기서 먼저 걸러 안내 문구를 준다.
     */
    boolean belongsToTemplate(String themeId, String templateId);

    String saveAdm(ThemeAdmDto theme);

    void deleteAdm(String themeId);
}
