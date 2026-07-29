package com.gonet.primary.auth.dto;

import com.gonet.common.audit.Auditable;
import com.gonet.common.util.Csv;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_role_url_access 1행 — DB RBAC 규칙 (conventions.md §7).
 *
 * <p>CSV 컬럼(required_roles·required_auths·allowed_user_types·allowed_ips)은 규칙 로드 시
 * 1회만 Set 으로 파싱해 캐시에 실린다 — 요청마다 split 하지 않는다.
 */
@Getter
@Setter
public class UrlAccessRule extends Auditable {

    private String urlAccessId;

    /** NULL = 전역 규칙. 값이 있으면 해당 사이트 요청에만 적용(전역보다 우선). */
    private String siteId;

    private String siteCode;

    /** Ant 스타일 패턴 — {@code /adm/**} · {@code /*&#47;member/**} */
    private String urlPattern;

    /** ALL | GET | POST | … */
    private String httpMethod;

    /** PERMIT_ALL | AUTHENTICATED | ANONYMOUS | ROLE | AUTH | IP_ONLY | DENY */
    private String accessType;

    private String requiredRoles;
    private String requiredAuths;
    private String allowedUserTypes;
    private String allowedIps;
    private String requireCsrfYn;
    private String require2faYn;
    private int priority;
    private String description;

    /** 관리 화면 편집 대상 — 인가 판정은 활성 규칙만 읽으므로 여기선 표시·저장용이다. */
    private String useYn;

    private Set<String> requiredRoleSet;
    private Set<String> requiredAuthSet;
    private Set<String> allowedUserTypeSet;
    private Set<String> allowedIpSet;

    /** 로드 직후 1회 호출 — CSV → Set 전개 (UrlAccessServiceImpl). */
    public UrlAccessRule parseCsv() {
        requiredRoleSet = Csv.toSet(requiredRoles);
        requiredAuthSet = Csv.toSet(requiredAuths);
        allowedUserTypeSet = Csv.toSet(allowedUserTypes);
        allowedIpSet = Csv.toSet(allowedIps);
        return this;
    }

    /** 규칙 위반 추적용 라벨 — 접근 거부 로그에 그대로 실린다. */
    public String label() {
        return "%s %s [%s] pri=%d%s".formatted(httpMethod, urlPattern, accessType, priority,
                siteCode == null ? "" : " site=" + siteCode);
    }

}
