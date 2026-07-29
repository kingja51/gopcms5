package com.gonet.primary.menu.mapper;

import com.gonet.primary.menu.dto.MenuAdmDto;
import com.gonet.primary.menu.dto.MenuNode;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 메뉴 조회 매퍼 — 활성 메뉴 평면 목록(depth·sort 정렬), 트리 조립은 서비스 몫. */
@EgovMapper
public interface MenuMapper {

    List<MenuNode> findActiveBySiteId(@Param("siteId") String siteId);

    /* ── 관리 CRUD (P7) ─────────────────────────────────────────────────── */

    /**
     * 사이트의 메뉴 전량 — 페이징하지 않는다. 메뉴는 <b>트리</b>라 페이지로 자르면
     * 부모 없는 자식이 보이는 화면이 된다(사이트당 수십 건 규모).
     */
    List<MenuAdmDto> findAdmBySite(@Param("siteId") String siteId,
            @Param("keyword") String keyword);

    MenuAdmDto findAdmById(@Param("menuId") String menuId);

    /** 하위 메뉴 수 — 0 이어야 삭제 가능(고아 노드 방지). */
    int countChildren(@Param("menuId") String menuId);

    int insert(MenuAdmDto menu);

    int update(MenuAdmDto menu);

    int softDelete(@Param("menuId") String menuId,
                   @Param("updatedBy") String updatedBy,
                   @Param("updatedIp") String updatedIp);
}
