package com.gonet.primary.auth.mapper;

import com.gonet.primary.auth.dto.AdminAllowIp;
import com.gonet.primary.auth.dto.LoginUser;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

@EgovMapper
public interface AuthMapper {

    /** vw_user_login 조회 — user_type 필수, MEMBER 는 siteId 스코프 필수. */
    LoginUser findLoginUser(@Param("userType") String userType,
            @Param("siteId") String siteId, @Param("loginId") String loginId);

    /** user_id 기준 조회 — 이미 인증된 주체의 현재 상태 확인용(사이트 스코프 불필요). */
    LoginUser findLoginUserById(@Param("userType") String userType,
            @Param("userId") String userId);

    /**
     * 활성 허용 IP 전량 — CIDR/RANGE 는 SQL 로 판정할 수 없어 Java(IpMatch)에서 매칭한다.
     * 운영 규모상 수십 행이라 전량 조회가 타당하다(관리자 계정별 화이트리스트).
     */
    List<AdminAllowIp> findActiveAllowIps();

    /** 특정 관리자의 활성 허용 IP — (admin_id, ip) 쌍 매칭용. */
    List<AdminAllowIp> findActiveAllowIpsByAdmin(@Param("adminId") String adminId);

    /** 허용 IP 접근 성공 카운트 갱신 — 매칭된 행(ip_id) 기준. */
    int touchAllowIp(@Param("ipId") String ipId);

    /**
     * 관리자 그룹의 2FA 강제 여부 — 계정이 아직 등록하지 않았을 때만 조회한다.
     * (그룹 정책이라 vw_user_login 에 넣지 않았다 — 뷰는 계정 자격 조회용)
     */
    String findTwoFactorRequired(@Param("groupId") String groupId);

    /** 2FA 등록 확정 — 시크릿은 {@code {AG}} 암호문으로 들어온다. */
    int enableTwoFactor(@Param("adminId") String adminId, @Param("secret") String secret);

    int memberLoginFail(@Param("userId") String userId);

    int adminLoginFail(@Param("userId") String userId);

    int memberLoginSuccess(@Param("userId") String userId, @Param("clientIp") String clientIp);

    int adminLoginSuccess(@Param("userId") String userId, @Param("clientIp") String clientIp);
}
