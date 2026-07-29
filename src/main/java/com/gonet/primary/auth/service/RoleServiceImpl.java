package com.gonet.primary.auth.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Csv;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.common.web.PageRequest;
import com.gonet.common.web.PageResult;
import com.gonet.primary.auth.dto.RoleAdmDto;
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
import java.util.regex.Pattern;
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

    /** Spring Security 권한 문자열 규약 — hasRole 이 ROLE_ 접두어를 전제한다. */
    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^ROLE_[A-Z0-9_]{1,44}$");

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

    /* ── 관리 CRUD (P7-3) ───────────────────────────────────────────────── */

    @Override
    public PageResult<RoleAdmDto> getAdmPage(PageRequest cond) {
        return new PageResult<>(roleMapper.findPage(cond), roleMapper.countPage(cond),
                cond.getPage(), cond.getSize());
    }

    @Override
    public RoleAdmDto getAdm(String roleId) {
        return roleMapper.findAdmById(roleId);
    }

    @Override
    public List<RoleAdmDto> getAllForSelect() {
        return roleMapper.findAllForSelect();
    }

    /** 쓰기 — writable override. 저장 후 재전개까지 한 트랜잭션에서 끝낸다. */
    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public String saveAdm(RoleAdmDto role) {
        String code = role.getRoleCode();
        if (code == null || !ROLE_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "역할 코드는 ROLE_ 로 시작하는 영문 대문자·숫자·밑줄이어야 합니다 (예: ROLE_EDITOR).");
        }
        if (role.getRoleName() == null || role.getRoleName().isBlank()) {
            throw new IllegalArgumentException("역할 이름은 필수입니다.");
        }
        if (roleMapper.countByCode(code, role.getRoleId()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 역할 코드입니다: " + code);
        }
        if (role.getRoleId() != null && role.getRoleId().equals(role.getParentRoleId())) {
            throw new IllegalArgumentException("자기 자신을 상위 역할로 지정할 수 없습니다.");
        }

        if (role.getRoleId() == null || role.getRoleId().isBlank()) {
            role.setRoleId(Uid.next(UidPrefix.ROL));
            roleMapper.insert(role);
        } else {
            roleMapper.update(role);
        }
        // 계층이 바뀌었을 수 있다 — closure 와 계정 CSV 를 지금 맞춘다.
        // RoleClosure 가 순환을 예외로 드러내므로 잘못된 계층은 여기서 롤백된다.
        rebuildHierarchy();
        return role.getRoleId();
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void deleteAdm(String roleId) {
        int references = roleMapper.countReferences(roleId);
        if (references > 0) {
            throw new IllegalArgumentException(
                    "계정·하위 역할·URL 규칙 " + references + "건이 이 역할을 참조 중이라 삭제할 수 없습니다.");
        }
        roleMapper.softDelete(roleId,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
        rebuildHierarchy();
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
            roleMapper.updateAdminRoleCsv(entry.getKey(), roleIds, roleCodes,
                    AuditorContext.currentUserId(), AuditorContext.currentIp());
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
                roleMapper.updateMemberRoleIds(row.getUserId(), roleIds,
                        AuditorContext.currentUserId(), AuditorContext.currentIp());
                changed++;
            }
        }
        return changed;
    }
}
