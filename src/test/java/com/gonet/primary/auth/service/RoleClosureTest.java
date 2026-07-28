package com.gonet.primary.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gonet.primary.auth.dto.RoleEdge;
import com.gonet.primary.auth.dto.RoleNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 역할 계층 전개 계약 — V906 시드(5단 체인 + 독립 루트)를 그대로 재현한다. */
class RoleClosureTest {

    private static final String ADMIN = "ROL_admin";
    private static final String MANAGER = "ROL_manager";
    private static final String STAFF = "ROL_staff";
    private static final String MEMBER = "ROL_member";
    private static final String REAL = "ROL_real";
    private static final String PRIVACY = "ROL_privacy";

    /** ADMIN > MANAGER > STAFF > MEMBER > REAL + 계층 밖 독립 역할 PRIVACY (V907). */
    private static List<RoleNode> seedRoles() {
        return List.of(
                new RoleNode(ADMIN, null, "ROLE_ADMIN"),
                new RoleNode(MANAGER, ADMIN, "ROLE_MANAGER"),
                new RoleNode(STAFF, MANAGER, "ROLE_STAFF"),
                new RoleNode(MEMBER, STAFF, "ROLE_MEMBER"),
                new RoleNode(REAL, MEMBER, "ROLE_REAL"),
                new RoleNode(PRIVACY, null, "ROLE_PRIVACY"));
    }

    @Test
    @DisplayName("5단 체인 + 독립 루트 = self 6 + 전개 10 = 16행 (V906·V907 시드와 일치)")
    void expandsSeedHierarchy() {
        List<RoleEdge> edges = RoleClosure.expand(seedRoles());

        assertThat(edges).hasSize(16);
        assertThat(edges).filteredOn(edge -> edge.getDepth() == 0).hasSize(6);
        assertThat(edges).filteredOn(edge -> edge.getAncestorRoleId().equals(ADMIN)).hasSize(5);
        assertThat(edges).filteredOn(edge -> edge.getAncestorRoleId().equals(PRIVACY)).hasSize(1);
    }

    @Test
    @DisplayName("depth 는 조상까지의 거리 — ADMIN→REAL 은 4")
    void depthIsDistanceToAncestor() {
        List<RoleEdge> edges = RoleClosure.expand(seedRoles());

        assertThat(edges).anySatisfy(edge -> {
            assertThat(edge.getAncestorRoleId()).isEqualTo(ADMIN);
            assertThat(edge.getDescendantRoleId()).isEqualTo(REAL);
            assertThat(edge.getDepth()).isEqualTo(4);
        });
    }

    @Test
    @DisplayName("상위 역할 하나로 하위 전체가 전개된다 — 인가 판정의 교집합 원천")
    void expandAllCoversDescendants() {
        Map<String, Set<String>> descendants =
                RoleClosure.descendantsByAncestor(RoleClosure.expand(seedRoles()));

        assertThat(RoleClosure.expandAll(Set.of(ADMIN), descendants))
                .containsExactly(ADMIN, MANAGER, STAFF, MEMBER, REAL);
        assertThat(RoleClosure.expandAll(Set.of(MEMBER), descendants))
                .containsExactly(MEMBER, REAL);
        // 독립 역할은 ADMIN 을 가져도 따라오지 않는다 (V907 최소 권한 원칙)
        assertThat(RoleClosure.expandAll(Set.of(ADMIN), descendants)).doesNotContain(PRIVACY);
    }

    @Test
    @DisplayName("비활성 부모는 루트로 간주 — 전개가 그 지점에서 멈춘다")
    void stopsAtUnknownParent() {
        List<RoleEdge> edges = RoleClosure.expand(List.of(
                new RoleNode(STAFF, "ROL_deleted", "ROLE_STAFF"),
                new RoleNode(MEMBER, STAFF, "ROLE_MEMBER")));

        assertThat(edges).hasSize(3); // self 2 + STAFF→MEMBER
        assertThat(edges).noneMatch(edge -> edge.getAncestorRoleId().equals("ROL_deleted"));
    }

    @Test
    @DisplayName("순환 참조는 무한루프 대신 예외로 드러난다")
    void detectsCycle() {
        List<RoleNode> cyclic = List.of(
                new RoleNode(ADMIN, MANAGER, "ROLE_ADMIN"),
                new RoleNode(MANAGER, ADMIN, "ROLE_MANAGER"));

        assertThatThrownBy(() -> RoleClosure.expand(cyclic))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("순환 참조");
    }
}
