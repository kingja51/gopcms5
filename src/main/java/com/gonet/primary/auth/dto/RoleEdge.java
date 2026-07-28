package com.gonet.primary.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * tb_role_hierarchy 1행 — closure(조상-후손 전수). depth 0 = self.
 *
 * <p>{@code roleHierarchyId} 는 재전개 직전 서비스가 {@code Uid.next(UidPrefix.RLH)} 로
 * 채운다 — 순수 전개 로직(RoleClosure)은 채번을 모른다(테스트 가능성).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleEdge {

    private String roleHierarchyId;
    private String ancestorRoleId;
    private String descendantRoleId;
    private int depth;

    public RoleEdge(String ancestorRoleId, String descendantRoleId, int depth) {
        this(null, ancestorRoleId, descendantRoleId, depth);
    }
}
