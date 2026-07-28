package com.gonet.primary.auth.service;

import com.gonet.primary.auth.dto.RoleRebuildResult;

/** 역할 계층 정합성 유지 — closure 재전개 + 사용자 역할 CSV 재계산. */
public interface RoleService {

    /**
     * tb_role 의 adjacency 를 기준으로 closure(tb_role_hierarchy)를 전량 재생성하고,
     * 그 결과로 관리자·회원의 역할 CSV 스냅샷을 다시 계산한다.
     *
     * <p>역할 계층을 바꾼 직후(관리자 화면 저장 훅)에 호출해야 인가 판정이 어긋나지 않는다.
     */
    RoleRebuildResult rebuildHierarchy();
}
