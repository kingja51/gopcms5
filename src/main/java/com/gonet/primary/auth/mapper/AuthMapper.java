package com.gonet.primary.auth.mapper;

import com.gonet.primary.auth.dto.LoginUser;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

@EgovMapper
public interface AuthMapper {

    /** vw_user_login 조회 — user_type 필수, MEMBER 는 siteId 스코프 필수. */
    LoginUser findLoginUser(@Param("userType") String userType,
            @Param("siteId") String siteId, @Param("loginId") String loginId);

    /** 요청 IP 가 활성 허용 IP 목록에 존재하는가 — /adm/login 폼 노출 게이트. */
    int countAllowIp(@Param("clientIp") String clientIp);

    /** (admin_id, ip) 쌍 매칭 — 관리자 인증 필수 조건. */
    int countAdminAllowIp(@Param("adminId") String adminId, @Param("clientIp") String clientIp);

    /** 허용 IP 접근 성공 카운트 갱신 */
    int touchAllowIp(@Param("adminId") String adminId, @Param("clientIp") String clientIp);

    int memberLoginFail(@Param("userId") String userId);

    int adminLoginFail(@Param("userId") String userId);

    int memberLoginSuccess(@Param("userId") String userId, @Param("clientIp") String clientIp);

    int adminLoginSuccess(@Param("userId") String userId, @Param("clientIp") String clientIp);
}
