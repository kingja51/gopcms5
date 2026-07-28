package com.gonet.primary.auth.service;

import com.gonet.primary.auth.dto.RoleEdge;
import com.gonet.primary.auth.dto.RoleNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 역할 계층 전개 — adjacency(parent_role_id) → closure(조상·후손 전수).
 *
 * <p>DB·트랜잭션을 모르는 순수 함수라 단위 테스트로 계약을 고정한다
 * (호출자는 {@link RoleService} 재전개 배치).
 */
public final class RoleClosure {

    private RoleClosure() {
        // 유틸리티 클래스 인스턴스화 방지
    }

    /**
     * 모든 (조상, 후손, depth) 쌍 — 자기 자신(depth 0) 포함.
     *
     * <p>부모가 목록에 없으면(비활성·삭제) 그 지점에서 루트로 간주하고 멈춘다.
     * 순환 참조는 계층 자체가 깨진 것이므로 예외로 드러낸다(조용한 무한루프 금지).
     */
    public static List<RoleEdge> expand(List<RoleNode> roles) {
        Map<String, RoleNode> byId = new LinkedHashMap<>();
        for (RoleNode role : roles) {
            byId.put(role.getRoleId(), role);
        }

        List<RoleEdge> edges = new ArrayList<>();
        for (RoleNode role : roles) {
            edges.add(new RoleEdge(role.getRoleId(), role.getRoleId(), 0));

            Set<String> walked = new LinkedHashSet<>();
            walked.add(role.getRoleId());
            String ancestorId = role.getParentRoleId();
            int depth = 1;
            while (ancestorId != null && byId.containsKey(ancestorId)) {
                if (!walked.add(ancestorId)) {
                    throw new IllegalStateException(
                            "역할 계층 순환 참조 — role_id=" + role.getRoleId() + " 경로 " + walked);
                }
                edges.add(new RoleEdge(ancestorId, role.getRoleId(), depth++));
                ancestorId = byId.get(ancestorId).getParentRoleId();
            }
        }
        return edges;
    }

    /**
     * 조상 → 후손 집합(자기 자신 포함) 색인. 사용자 role_ids 전개에 쓴다 —
     * 상위 역할을 가진 계정은 하위 역할 규칙을 자동 통과한다.
     */
    public static Map<String, Set<String>> descendantsByAncestor(List<RoleEdge> edges) {
        Map<String, Set<String>> index = new LinkedHashMap<>();
        for (RoleEdge edge : edges) {
            index.computeIfAbsent(edge.getAncestorRoleId(), key -> new LinkedHashSet<>())
                    .add(edge.getDescendantRoleId());
        }
        return index;
    }

    /** 기준 역할 집합을 후손까지 전개 — 입력 순서를 유지한 CSV 스냅샷의 원천. */
    public static Set<String> expandAll(Set<String> baseRoleIds,
            Map<String, Set<String>> descendants) {
        Set<String> expanded = new LinkedHashSet<>();
        for (String roleId : baseRoleIds) {
            expanded.addAll(descendants.getOrDefault(roleId, Set.of(roleId)));
        }
        return expanded;
    }
}
