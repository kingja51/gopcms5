package com.gonet.primary.board.service;

import com.gonet.primary.board.dto.BbsCategoryAdmDto;
import java.util.List;

/** 게시판 카테고리 — 게시판 안의 분류. */
public interface BoardCategoryService {

    List<BbsCategoryAdmDto> getByBoard(String bbsMasterId);

    BbsCategoryAdmDto getAdm(String categoryId);

    void saveAdm(BbsCategoryAdmDto category);

    void deleteAdm(String categoryId);
}
