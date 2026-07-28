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
 */
public record LoginPrincipal(String userType, String userId, String loginId,
        String displayName, String siteId, String siteCode)
        implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return loginId;
    }
}
