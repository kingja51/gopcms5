package com.gonet.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * IP 매칭 — 관리자 IP 게이트와 URL 규칙의 {@code IP_ONLY} 가 함께 쓴다.
 *
 * <p>여기가 느슨하면 증상이 없다. 허용해야 할 것을 막으면 바로 신고가 들어오지만,
 * <b>막아야 할 것을 허용하면 아무 일도 일어나지 않는다</b> — 그래서 거절 케이스를 함께 못 박는다.
 */
class IpMatchTest {

    @Nested
    @DisplayName("CSV 1토큰 — 표기로 SINGLE·CIDR·RANGE 를 가른다")
    class Token {

        @Test
        void 단일_주소는_완전일치() {
            assertThat(IpMatch.matchesToken("10.0.0.1", "10.0.0.1")).isTrue();
            assertThat(IpMatch.matchesToken("10.0.0.1", "10.0.0.2")).isFalse();
        }

        @Test
        void CIDR_는_대역_전체를_연다() {
            assertThat(IpMatch.matchesToken("10.0.0.0/8", "10.9.9.9")).isTrue();
            assertThat(IpMatch.matchesToken("10.0.0.0/8", "11.0.0.1")).isFalse();
            assertThat(IpMatch.matchesToken("192.168.1.0/24", "192.168.1.255")).isTrue();
            assertThat(IpMatch.matchesToken("192.168.1.0/24", "192.168.2.1")).isFalse();
        }

        @Test
        void 하이픈은_범위다_양끝_포함() {
            assertThat(IpMatch.matchesToken("10.0.0.5-10.0.0.9", "10.0.0.5")).isTrue();
            assertThat(IpMatch.matchesToken("10.0.0.5-10.0.0.9", "10.0.0.9")).isTrue();
            assertThat(IpMatch.matchesToken("10.0.0.5-10.0.0.9", "10.0.0.4")).isFalse();
            assertThat(IpMatch.matchesToken("10.0.0.5-10.0.0.9", "10.0.0.10")).isFalse();
        }

        @Test
        void IPv6_는_하이픈을_쓰지_않으므로_잘리지_않는다() {
            assertThat(IpMatch.matchesToken("0:0:0:0:0:0:0:1", "::1")).isTrue();
            assertThat(IpMatch.matchesToken("::1", "::2")).isFalse();
        }

        @Test
        void 공백은_다듬고_형식이_깨진_값은_거절한다() {
            assertThat(IpMatch.matchesToken("  10.0.0.1  ", "10.0.0.1")).isTrue();
            // 파싱 예외로 로그인을 깨지 않되, 잘못된 규칙이 통과되지도 않게 한다
            assertThat(IpMatch.matchesToken("not-an-ip", "10.0.0.1")).isFalse();
            assertThat(IpMatch.matchesToken("10.0.0.0/999", "10.0.0.1")).isFalse();
            assertThat(IpMatch.matchesToken("", "10.0.0.1")).isFalse();
            assertThat(IpMatch.matchesToken(null, "10.0.0.1")).isFalse();
            assertThat(IpMatch.matchesToken("10.0.0.1", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("ip_type 컬럼이 있는 곳 — tb_admin_allow_ip")
    class TypedRow {

        @Test
        void 타입별로_다른_컬럼을_본다() {
            assertThat(IpMatch.matches("SINGLE", "10.0.0.1", null, null, "10.0.0.1")).isTrue();
            assertThat(IpMatch.matches("CIDR", "10.0.0.0/8", null, null, "10.1.2.3")).isTrue();
            assertThat(IpMatch.matches("RANGE", null, "10.0.0.1", "10.0.0.5", "10.0.0.3")).isTrue();
            assertThat(IpMatch.matches("RANGE", null, "10.0.0.1", "10.0.0.5", "10.0.0.6")).isFalse();
        }

        @Test
        void 알_수_없는_타입은_통과시키지_않는다() {
            assertThat(IpMatch.matches("WHATEVER", "10.0.0.1", null, null, "10.0.0.1")).isFalse();
        }

        @Test
        void 주소_계열이_다르면_불일치() {
            // IPv4 범위에 IPv6 주소를 넣어도 바이트 길이가 달라 통과하면 안 된다
            assertThat(IpMatch.matches("RANGE", null, "10.0.0.1", "10.0.0.5", "::1")).isFalse();
        }
    }
}
