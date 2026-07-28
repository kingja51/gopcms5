package com.gonet.primary.auth.mapper;

import com.gonet.primary.auth.dto.RoleEdge;
import com.gonet.primary.auth.dto.RoleNode;
import com.gonet.primary.auth.dto.UserRoleRow;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 역할 계층 재전개·사용자 역할 CSV 재계산 (RoleService 배치 전용). */
@EgovMapper
public interface RoleMapper {

    /** 활성 역할 전량 — sort_order 순(전개 결과 CSV 의 순서를 결정). */
    List<RoleNode> findAllRoles();

    /** closure 전량 삭제 — 재전개는 delete + insert 한 트랜잭션. */
    int deleteAllHierarchy();

    int insertHierarchy(@Param("edges") Collection<RoleEdge> edges);

    /** 관리자 기준 역할(tb_admin_role) — 계정당 N행. CSV 는 이 값을 전개한 스냅샷. */
    List<UserRoleRow> findAdminRolePairs();

    /** 관리자 현재 CSV — 재계산 결과와 비교해 실제 변경분만 UPDATE 하기 위한 값. */
    List<UserRoleRow> findAdminRoleCsv();

    int updateAdminRoleCsv(@Param("userId") String userId, @Param("roleIds") String roleIds,
            @Param("roleCodes") String roleCodes);

    /** 회원 현재 role_ids CSV — 기준 매핑 테이블이 없어 현재 값을 기준 집합으로 쓴다. */
    List<UserRoleRow> findMemberRoleCsv();

    int updateMemberRoleIds(@Param("userId") String userId, @Param("roleIds") String roleIds);
}
