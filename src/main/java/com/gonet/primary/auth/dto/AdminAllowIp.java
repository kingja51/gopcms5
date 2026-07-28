package com.gonet.primary.auth.dto;

import com.gonet.common.util.IpMatch;
import lombok.Getter;
import lombok.Setter;

/** tb_admin_allow_ip 1행 — 매칭은 SQL 완전일치가 아니라 Java(IpMatch) 에서 한다(CIDR/RANGE). */
@Getter
@Setter
public class AdminAllowIp {

    private String ipId;
    private String adminId;

    /** SINGLE 은 주소, CIDR 은 {@code 10.0.0.0/8} 표기 (RANGE 는 start~end 사용) */
    private String ipAddress;

    /** SINGLE | CIDR | RANGE */
    private String ipType;

    private String ipStart;
    private String ipEnd;
    private String description;

    public boolean matches(String clientIp) {
        return IpMatch.matches(ipType, ipAddress, ipStart, ipEnd, clientIp);
    }
}
