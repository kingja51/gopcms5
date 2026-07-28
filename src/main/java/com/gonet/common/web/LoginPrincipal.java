package com.gonet.common.web;

import java.io.Serializable;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * 인증 주체 — 세션 저장 대상(Serializable). 비밀번호 등 민감값 미보유.
 *
 * @param userType    MEMBER | ADMIN
 * @param userId      ADM_… / MBR_… (감사·로그의 actor_user_id)
 * @param loginId     로그인 ID
 * @param displayName 표시 이름
 * @param siteId      MEMBER 의 소속 사이트 (ADMIN null)
 * @param siteCode    MEMBER 로그인 시 사이트 코드 — 성공 리다이렉트(/{sc}/index)에 사용
 * @param roleIds     역할 ID CSV — 계층 closure 전개 스냅샷. DB RBAC 규칙의
 *                    {@code required_roles} 와 교집합으로 인가를 판정한다(P6-2).
 *                    로그인 시점 스냅샷이므로 역할 변경은 재로그인 후 반영된다.
 * @param twoFactorPending 그룹 정책상 2FA 가 강제인데 아직 등록하지 않은 상태(P6-3).
 *                    {@code TwoFactorEnrollmentFilter} 가 등록 화면으로 강제 유도한다.
 */
public record LoginPrincipal(String userType, String userId, String loginId,
        String displayName, String siteId, String siteCode, String roleIds,
        boolean twoFactorPending)
        implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return loginId;
    }
}
