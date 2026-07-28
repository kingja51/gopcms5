package com.gonet.common.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * IP 매칭 — tb_admin_allow_ip 의 {@code ip_type} 3종(SINGLE·CIDR·RANGE)과 신뢰 프록시 판정 공용.
 *
 * <p>SINGLE/CIDR 은 Spring Security {@code IpAddressMatcher}(IPv4·IPv6 공통)에 위임하고,
 * RANGE 만 주소 바이트 비교로 처리한다. <b>형식이 깨진 값은 매칭 실패(false)</b> —
 * 파싱 예외로 로그인 자체를 깨지 않되, 잘못된 규칙이 통과되지도 않게 한다.
 */
@Slf4j
public final class IpMatch {

    private IpMatch() {
        // 유틸리티 클래스 인스턴스화 방지
    }

    /** 허용 IP 1행이 클라이언트 IP 를 포함하는가. */
    public static boolean matches(String ipType, String ipAddress, String ipStart, String ipEnd,
            String clientIp) {
        if (clientIp == null) {
            return false;
        }
        return switch (ipType == null ? "SINGLE" : ipType) {
            case "SINGLE", "CIDR" -> matchesPattern(ipAddress, clientIp);
            case "RANGE" -> inRange(ipStart, ipEnd, clientIp);
            default -> {
                log.warn("알 수 없는 ip_type '{}' — 매칭 실패로 처리", ipType);
                yield false;
            }
        };
    }

    /** 단일 주소 또는 CIDR 표기({@code 10.0.0.0/8}) 매칭. */
    public static boolean matchesPattern(String pattern, String clientIp) {
        if (pattern == null || pattern.isBlank() || clientIp == null) {
            return false;
        }
        try {
            return new IpAddressMatcher(pattern.trim()).matches(clientIp);
        } catch (IllegalArgumentException e) {
            log.warn("IP 패턴 해석 실패 '{}' — 매칭 실패로 처리: {}", pattern, e.getMessage());
            return false;
        }
    }

    /** 시작·종료 주소 사이(양끝 포함). 주소 계열(IPv4/IPv6)이 다르면 불일치. */
    public static boolean inRange(String start, String end, String clientIp) {
        if (start == null || end == null) {
            return false;
        }
        try {
            byte[] from = InetAddress.getByName(start.trim()).getAddress();
            byte[] to = InetAddress.getByName(end.trim()).getAddress();
            byte[] target = InetAddress.getByName(clientIp).getAddress();
            if (from.length != target.length || to.length != target.length) {
                return false;
            }
            return compare(target, from) >= 0 && compare(target, to) <= 0;
        } catch (UnknownHostException e) {
            log.warn("IP 범위 해석 실패 [{} ~ {}] — 매칭 실패로 처리: {}", start, end, e.getMessage());
            return false;
        }
    }

    /** 부호 없는 바이트 사전식 비교 — 주소 크기 비교의 정석. */
    private static int compare(byte[] left, byte[] right) {
        for (int i = 0; i < left.length; i++) {
            int diff = Byte.toUnsignedInt(left[i]) - Byte.toUnsignedInt(right[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }
}
