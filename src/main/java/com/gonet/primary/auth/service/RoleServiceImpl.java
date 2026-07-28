package com.gonet.primary.auth.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Csv;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.auth.dto.RoleEdge;
import com.gonet.primary.auth.dto.RoleNode;
import com.gonet.primary.auth.dto.RoleRebuildResult;
import com.gonet.primary.auth.dto.UserRoleRow;
import com.gonet.primary.auth.mapper.RoleMapper;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할 계층 재전개 배치 (P6-2).
 *
 * <p><b>왜 필요한가</b>: 인가 판정은 사용자의 {@code role_ids}(전개 스냅샷)와 규칙의
 * {@code required_roles} 교집합이다. 계층을 바꿔도 스냅샷을 다시 계산하지 않으면
 * 권한이 조용히 어긋난다 — 그 재계산이 이 서비스다.
 *
 * <p><b>기준 집합</b>: 관리자는 tb_admin_role(정본 매핑)을 전개한다. 회원은 매핑 테이블이
 * 없어(선행 프로젝트도 동일 — 회원 인가는 AUTHENTICATED + user_type 기준) 현재 CSV 를
 * 기준으로 전개하므로 <b>가산만 되고 축소는 반영되지 않는다</b>. 회원 도메인 페이즈에서
 * 기준 매핑이 생기면 관리자와 같은 방식으로 통일한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class RoleServiceImpl extends AbstractCmsService implements RoleService {

    /** 배치 INSERT 분할 크기 — 단일 문장 길이 폭주 방지. */
    private static final int CHUNK = 500;

    private final RoleMapper roleMapper;

    /** 쓰기 — writable override (트랜잭션 함정 규약). 재전개는 delete+insert 원자 처리. */
    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public RoleRebuildResult rebuildHierarchy() {
        List<RoleNode> roles = roleMapper.findAllRoles();
        List<RoleEdge> edges = RoleClosure.expand(roles);
        edges.forEach(edge -> edge.setRoleHierarchyId(Uid.next(UidPrefix.RLH)));

        roleMapper.deleteAllHierarchy();
        for (int from = 0; from < edges.size(); from += CHUNK) {
            roleMapper.insertHierarchy(edges.subList(from, Math.min(from + CHUNK, edges.size())));
        }

        Map<String, Set<String>> descendants = RoleClosure.descendantsByAncestor(edges);
        Map<String, String> codeById = new LinkedHashMap<>();
        roles.forEach(role -> codeById.put(role.getRoleId(), role.getRoleCode()));

        RoleRebuildResult result = new RoleRebuildResult(roles.size(), edges.size(),
                recalculateAdmins(descendants, codeById), recalculateMembers(descendants));
        log.info("역할 계층 재전개 완료 — 역할 {} · closure {} · 관리자 갱신 {} · 회원 갱신 {}",
                result.roles(), result.edges(), result.adminsFixed(), result.membersFixed());
        return result;
    }

    /**
     * 관리자: tb_admin_role 기준 역할을 후손까지 전개해 role_ids·role_codes 를 다시 쓴다.
     *
     * <p>드라이버가 UPDATE 의 "matched" 행 수를 돌려주므로 갱신 건수는 SQL 결과가 아니라
     * 현재 값과의 비교로 센다 — 그래야 숫자가 곧 계층 이탈(drift) 규모가 된다.
     */
    private int recalculateAdmins(Map<String, Set<String>> descendants,
            Map<String, String> codeById) {
        Map<String, Set<String>> baseByAdmin = new LinkedHashMap<>();
        for (UserRoleRow row : roleMapper.findAdminRolePairs()) {
            baseByAdmin.computeIfAbsent(row.getUserId(), key -> new LinkedHashSet<>())
                    .add(row.getRoleId());
        }
        Map<String, UserRoleRow> currentById = new LinkedHashMap<>();
        roleMapper.findAdminRoleCsv().forEach(row -> currentById.put(row.getUserId(), row));

        int changed = 0;
        for (Map.Entry<String, Set<String>> entry : baseByAdmin.entrySet()) {
            Set<String> expanded = RoleClosure.expandAll(entry.getValue(), descendants);
            String roleIds = String.join(",", expanded);
            String roleCodes = expanded.stream()
                    .map(codeById::get).filter(Objects::nonNull)
                    .collect(Collectors.joining(","));

            UserRoleRow current = currentById.get(entry.getKey());
            if (current != null && roleIds.equals(current.getRoleIds())
                    && roleCodes.equals(current.getRoleCodes())) {
                continue;
            }
            roleMapper.updateAdminRoleCsv(entry.getKey(), roleIds, roleCodes);
            changed++;
        }
        return changed;
    }

    /** 회원: 현재 CSV 를 기준 집합으로 전개 — 값이 실제로 바뀐 계정만 UPDATE. */
    private int recalculateMembers(Map<String, Set<String>> descendants) {
        int changed = 0;
        for (UserRoleRow row : roleMapper.findMemberRoleCsv()) {
            Set<String> base = Csv.toSet(row.getRoleIds());
            String roleIds = String.join(",", RoleClosure.expandAll(base, descendants));
            if (!roleIds.equals(row.getRoleIds())) {
                roleMapper.updateMemberRoleIds(row.getUserId(), roleIds);
                changed++;
            }
        }
        return changed;
    }
}
