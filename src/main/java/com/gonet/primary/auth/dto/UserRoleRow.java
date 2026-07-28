package com.gonet.primary.auth.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 역할 CSV 재계산 입력 1행 — 조회 종류에 따라 채워지는 필드가 다르다.
 *
 * <ul>
 *   <li>관리자 기준 역할(tb_admin_role): {@code userId} + {@code roleId} (계정당 N행)</li>
 *   <li>현재 CSV 스냅샷(tb_admin·tb_member): {@code userId} + {@code roleIds}(+{@code roleCodes})
 *       — 재계산 결과와 비교해 <b>실제로 바뀐 계정만</b> UPDATE 하기 위한 원본</li>
 * </ul>
 */
@Getter
@Setter
public class UserRoleRow {

    private String userId;
    private String roleId;
    private String roleIds;
    private String roleCodes;
}
