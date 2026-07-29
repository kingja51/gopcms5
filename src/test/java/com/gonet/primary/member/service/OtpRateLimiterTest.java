package com.gonet.primary.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 인증번호·임시비밀번호 <b>발송량</b> 제한.
 *
 * <p>계정별 쿨다운(60초)이 막지 못하는 축을 이 클래스가 막는다: 아이디를 바꿔 가며
 * 계정마다 한 번씩 요청하면 쿨다운에는 한 번도 걸리지 않으면서 대량 메일을 보낼 수 있다.
 * 그 경로가 열리면 우리 서버가 스팸 발신자가 되고, 메일 평판이 떨어지면 정상 메일까지 막힌다.
 *
 * <p>HTTP 로는 확인하기 번거로운 부분(한도 경계·IP 분리)을 여기서 고정한다.
 */
class OtpRateLimiterTest {

    private OtpRateLimiter limiter(int perIp) {
        OtpRateLimiter limiter = new OtpRateLimiter();
        ReflectionTestUtils.setField(limiter, "perIp", perIp);
        ReflectionTestUtils.setField(limiter, "windowMinutes", 60);
        return limiter;
    }

    @Test
    @DisplayName("한도까지는 통과하고 그 다음부터 막는다")
    void blocksAfterLimit() {
        OtpRateLimiter limiter = limiter(3);

        assertThat(limiter.tryConsume("10.0.0.1")).isTrue();
        assertThat(limiter.tryConsume("10.0.0.1")).isTrue();
        assertThat(limiter.tryConsume("10.0.0.1")).isTrue();
        // 4번째 — 한도 초과
        assertThat(limiter.tryConsume("10.0.0.1")).isFalse();
        assertThat(limiter.tryConsume("10.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("IP 마다 따로 센다 — 한 IP 가 막혀도 다른 사용자는 영향이 없다")
    void countsPerIp() {
        OtpRateLimiter limiter = limiter(1);

        assertThat(limiter.tryConsume("10.0.0.1")).isTrue();
        assertThat(limiter.tryConsume("10.0.0.1")).isFalse();
        // 다른 IP 는 자기 몫을 그대로 갖는다
        assertThat(limiter.tryConsume("10.0.0.2")).isTrue();
    }

    @Test
    @DisplayName("계정을 바꿔도 같은 IP 면 같은 버킷 — 이것이 이 제한의 존재 이유다")
    void sameIpSharesBucketAcrossAccounts() {
        OtpRateLimiter limiter = limiter(2);

        // 호출부가 계정을 넘기지 않는다는 사실 자체가 설계다.
        // 계정별로 셌다면 아이디만 바꿔 가며 무제한 발송이 가능하다.
        assertThat(limiter.tryConsume("10.0.0.9")).isTrue();
        assertThat(limiter.tryConsume("10.0.0.9")).isTrue();
        assertThat(limiter.tryConsume("10.0.0.9")).isFalse();
    }

    @Test
    @DisplayName("IP 를 모를 때도 세다 — null 이라고 통과시키면 그게 우회로가 된다")
    void nullIpStillCounted() {
        OtpRateLimiter limiter = limiter(1);

        assertThat(limiter.tryConsume(null)).isTrue();
        assertThat(limiter.tryConsume(null)).isFalse();
        assertThat(limiter.tryConsume("   ")).isFalse();
    }
}
