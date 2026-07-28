package com.gonet.primary.auth.service;

import com.gonet.primary.auth.dto.AdminAllowIp;
import com.gonet.primary.auth.dto.LoginUser;

/** 인증 조회·상태 갱신 서비스 — Provider·컨트롤러는 이 인터페이스만 사용. */
public interface AuthService {

    LoginUser findLoginUser(String userType, String siteId, String loginId);

    /** user_id 로 조회 — 이미 인증된 주체의 현재 상태(비밀번호 해시·만료)가 필요할 때. */
    LoginUser findLoginUserById(String userType, String userId);

    /** /adm/login 폼 노출 게이트 — 요청 IP 가 허용 IP 목록(SINGLE·CIDR·RANGE)에 걸리는가. */
    boolean isIpAllowedForLoginForm(String clientIp);

    /** 관리자 인증 조건 — (admin_id, ip) 매칭 행. 없으면 null(로그인 거부). */
    AdminAllowIp matchAllowIp(String adminId, String clientIp);

    /** 그룹 정책상 2FA 가 강제인가 — 계정이 아직 등록하지 않았을 때의 판단 근거. */
    boolean isTwoFactorRequired(String groupId);

    /** 2FA 등록 확정 (시크릿은 암호화해 저장). */
    void enableTwoFactor(String adminId, String encryptedSecret);

    void loginFailed(String userType, String userId);

    /**
     * 로그인 성공 처리 — 실패 카운트·잠금 해제 + 최종 접속 기록.
     *
     * @param allowIpId 매칭된 허용 IP 행(관리자만, 회원은 null) — 접근 횟수 갱신 대상
     */
    void loginSucceeded(String userType, String userId, String clientIp, String allowIpId);
}
