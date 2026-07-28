package com.gonet.primary.layout.service;

import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.primary.layout.dto.LayoutAdmDto;
import java.util.List;

/** 레이아웃(구조 축) 관리 — 3축 중 "뷰 폴더" 축. */
public interface LayoutService {

    PageResult<LayoutAdmDto> getAdmPage(PageRequest cond);

    LayoutAdmDto getAdm(String layoutId);

    List<LayoutAdmDto> getAllForSelect();

    String saveAdm(LayoutAdmDto layout);

    void deleteAdm(String layoutId);
}
