package com.gonet.primary.auth.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 역할 관리 DTO — tb_role.
 *
 * <p>{@code parentRoleId} 로 계층을 만들고, 실제 인가 판정은 그 계층을 전개한
 * closure(tb_role_hierarchy)와 계정의 role_ids 스냅샷이 한다. 그래서 계층을 바꾸면
 * <b>재전개가 반드시 뒤따라야</b> 한다 — 화면에 실행 버튼을 둔 이유다.
 */
@Getter
@Setter
public class RoleAdmDto {

    private String roleId;

    /** NULL = 전역 역할. 사이트 전용 역할도 만들 수 있다(uk: site_id + role_code) */
    private String siteId;
    private String siteCode;

    /** 직계 상위 역할 — NULL 이면 독립 루트(다른 역할에 상속되지 않는다) */
    private String parentRoleId;

    /** ROLE_ 로 시작하는 코드 — Spring Security 권한 문자열과 1:1 */
    private String roleCode;

    private String roleName;
    private String description;
    private int sortOrder;
    private String useYn;

    /** 목록 표시용 */
    private String parentRoleName;
    private int descendantCount;
    private int adminCount;
}
