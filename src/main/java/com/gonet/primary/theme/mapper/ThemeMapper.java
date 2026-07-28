package com.gonet.primary.theme.mapper;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.theme.dto.ThemeAdmDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 테마(색 축) 관리 매퍼 — tb_theme. */
@EgovMapper
public interface ThemeMapper {

    List<ThemeAdmDto> findPage(PageRequest cond);

    int countPage(PageRequest cond);

    ThemeAdmDto findById(@Param("themeId") String themeId);

    /** 코드는 템플릿 안에서만 유일하면 된다 (uk: template_id + theme_code). */
    int countByCode(@Param("templateId") String templateId, @Param("themeCode") String themeCode,
            @Param("excludeId") String excludeId);

    /** (theme, template) 짝이 유효한가 — 사이트 저장 전 복합 FK 사전 검증. */
    int countByIdAndTemplate(@Param("themeId") String themeId,
            @Param("templateId") String templateId);

    int countReferences(@Param("themeId") String themeId);

    int insert(ThemeAdmDto theme);

    int update(ThemeAdmDto theme);

    int softDelete(@Param("themeId") String themeId);

    List<ThemeAdmDto> findAllForSelect();
}
