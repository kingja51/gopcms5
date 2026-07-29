package com.gonet.primary.auth.service;

import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.primary.auth.dto.RoleAdmDto;
import com.gonet.primary.auth.dto.RoleRebuildResult;
import java.util.List;

/** 역할 계층 정합성 유지 — closure 재전개 + 사용자 역할 CSV 재계산. */
public interface RoleService {

    /**
     * tb_role 의 adjacency 를 기준으로 closure(tb_role_hierarchy)를 전량 재생성하고,
     * 그 결과로 관리자·회원의 역할 CSV 스냅샷을 다시 계산한다.
     *
     * <p>역할 계층을 바꾼 직후(관리자 화면 저장 훅)에 호출해야 인가 판정이 어긋나지 않는다.
     */
    RoleRebuildResult rebuildHierarchy();

    /* ── 관리 CRUD (P7-3) ───────────────────────────────────────────────── */

    PageResult<RoleAdmDto> getAdmPage(PageRequest cond);

    RoleAdmDto getAdm(String roleId);

    List<RoleAdmDto> getAllForSelect();

    /**
     * 등록·수정. 계층(parent)이 바뀌면 closure 와 계정 CSV 가 낡으므로
     * 저장 직후 {@link #rebuildHierarchy()} 를 함께 돌린다 — 화면이 어긋난 권한을
     * 남기지 않게 하려는 것이다.
     *
     * @throws IllegalArgumentException 코드 형식·중복·순환 지정
     */
    String saveAdm(RoleAdmDto role);

    /** 참조(계정·하위 역할·URL 규칙)가 있으면 거부. */
    void deleteAdm(String roleId);
}
