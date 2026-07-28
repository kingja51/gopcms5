package com.gonet.primary.menu.mapper;

import com.gonet.primary.menu.dto.MenuNode;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 메뉴 조회 매퍼 — 활성 메뉴 평면 목록(depth·sort 정렬), 트리 조립은 서비스 몫. */
@EgovMapper
public interface MenuMapper {

    List<MenuNode> findActiveBySiteId(@Param("siteId") String siteId);
}
