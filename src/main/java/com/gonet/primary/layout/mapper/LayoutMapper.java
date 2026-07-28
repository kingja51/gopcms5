package com.gonet.primary.layout.mapper;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.layout.dto.LayoutAdmDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 레이아웃(구조 축) 관리 매퍼 — tb_layout. */
@EgovMapper
public interface LayoutMapper {

    List<LayoutAdmDto> findPage(PageRequest cond);

    int countPage(PageRequest cond);

    LayoutAdmDto findById(@Param("layoutId") String layoutId);

    int countByCode(@Param("layoutCode") String layoutCode, @Param("excludeId") String excludeId);

    /** 참조 중인 사이트·템플릿 수 — 0 이어야 삭제 가능. */
    int countReferences(@Param("layoutId") String layoutId);

    int insert(LayoutAdmDto layout);

    int update(LayoutAdmDto layout);

    int softDelete(@Param("layoutId") String layoutId);

    List<LayoutAdmDto> findAllForSelect();
}
