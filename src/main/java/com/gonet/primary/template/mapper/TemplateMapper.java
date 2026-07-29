package com.gonet.primary.template.mapper;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.template.dto.TemplateAdmDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 템플릿(시각 언어 축) 관리 매퍼 — tb_template. */
@EgovMapper
public interface TemplateMapper {

    List<TemplateAdmDto> findPage(PageRequest cond);

    int countPage(PageRequest cond);

    TemplateAdmDto findById(@Param("templateId") String templateId);

    int countByCode(@Param("templateCode") String templateCode,
            @Param("excludeId") String excludeId);

    /** 참조 중인 사이트·테마 수 — 0 이어야 삭제 가능. */
    int countReferences(@Param("templateId") String templateId);

    int insert(TemplateAdmDto template);

    int update(TemplateAdmDto template);

    int softDelete(@Param("templateId") String templateId,
                   @Param("updatedBy") String updatedBy,
                   @Param("updatedIp") String updatedIp);

    List<TemplateAdmDto> findAllForSelect();
}
