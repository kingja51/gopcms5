package com.gonet.primary.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** tb_role 의 계층 계산 최소 투영 — adjacency(parent) + 권한 부여용 role_code. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleNode {

    private String roleId;
    private String parentRoleId;
    private String roleCode;
}
