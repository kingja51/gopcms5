package com.gonet.primary.auth.service;

import com.gonet.primary.auth.dto.LoginUser;

/** 인증 조회·상태 갱신 서비스 — Provider·컨트롤러는 이 인터페이스만 사용. */
public interface AuthService {

    LoginUser findLoginUser(String userType, String siteId, String loginId);

    /** /adm/login 폼 노출 게이트 — 요청 IP 가 허용 IP 목록에 존재하는가. */
    boolean isIpAllowedForLoginForm(String clientIp);

    /** 관리자 인증 조건 — (admin_id, ip) 쌍 매칭. */
    boolean isIpAllowedForAdmin(String adminId, String clientIp);

    void loginFailed(String userType, String userId);

    void loginSucceeded(String userType, String userId, String clientIp);
}
