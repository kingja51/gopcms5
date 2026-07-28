package com.gonet.primary.auth.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** vw_user_login 1행 — 관리자(/adm/login)·회원(/login?siteCode=) 공용 인증 원천. */
@Getter
@Setter
public class LoginUser {

    private String userType;      // MEMBER | ADMIN (EMPLOYEE 는 직원 도메인 도입 시)
    private String userId;        // ADM_… / MBR_…
    private Long uniqId;
    private String siteId;        // MEMBER 만 (ADMIN NULL)
    private String groupId;
    private String loginId;
    private String password;      // BCrypt
    private String status;        // ACTIVE | LOCKED | …
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime passwordExpireAt;
    private String captchaRequiredYn; // 잠금 이력이 있는 계정 — 다음 성공 시 자동 'N'
    private String twoFactorEnabledYn;
    private String twoFactorSecret;
    private String roleIds;       // 계층 전개 CSV
    private String roleCodes;     // ROLE_* CSV — Security 권한 부여
    private String departmentId;
    private String departmentName;
    private String displayName;
}
