package com.gonet.config.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로그인 시도 제한 — <b>IP 와 아이디 두 축</b>.
 *
 * <p>한 축만으로는 부족하다:
 * <ul>
 *   <li><b>IP 만</b> 걸면 한 IP 에서 아이디를 바꿔 가며 시도하는 것은 막지만,
 *       봇넷이 IP 를 바꿔 가며 한 계정을 두드리는 것은 통과한다</li>
 *   <li><b>아이디만</b> 걸면 반대가 된다. 게다가 공격자가 남의 아이디를 일부러 잠가
 *       서비스를 막는 데(계정 잠금 공격) 쓸 수 있다</li>
 * </ul>
 * 그래서 둘 다 센다. 계정 잠금(5회/30분)은 <b>비밀번호가 틀렸을 때만</b> 오르지만
 * 이쪽은 <b>시도 자체</b>를 센다 — 인증에 도달하기 전에 끊는 것이 목적이다.
 *
 * <p>저장소는 인메모리다. 다중화하면 인스턴스마다 따로 세므로 실질 한도가 인스턴스 수만큼
 * 늘어난다 — Redis 공유가 필요해지는 지점이며 세션 저장소와 같은 시기에 함께 옮긴다.
 */
@Component
public class LoginRateLimiter {

    /** IP 당 허용 시도. 사무실·학교처럼 공용 IP 가 있어 아이디보다 넉넉해야 한다. */
    @Value("${gopcms.security.login-rate.per-ip:30}")
    private int perIp;

    /** 아이디당 허용 시도 — 사람이 오타를 내는 횟수를 넘지 않는 선. */
    @Value("${gopcms.security.login-rate.per-id:10}")
    private int perId;

    /** 시간창(분). */
    @Value("${gopcms.security.login-rate.window-minutes:5}")
    private int windowMinutes;

    /**
     * 버킷 캐시 — 시간창의 2배 동안 두고 버린다.
     *
     * <p>영구 보관하면 IP·아이디마다 객체가 쌓여 메모리가 샌다. 반대로 시간창보다 짧게
     * 버리면 카운터가 초기화돼 제한이 무력해진다.
     */
    private Cache<String, Bucket> buckets;

    /**
     * 시도 1회를 소비한다.
     *
     * @return 허용되면 true, 한도를 넘었으면 false
     */
    public boolean tryConsume(String clientIp, String loginId) {
        boolean ipOk = bucket("ip:" + nullSafe(clientIp), perIp).tryConsume(1);
        // 두 축을 모두 소비한다 — 한쪽이 막혔다고 다른 쪽 카운터를 건너뛰면
        // 그 축으로는 사실상 무제한이 된다
        boolean idOk = bucket("id:" + nullSafe(loginId).toLowerCase(), perId).tryConsume(1);
        return ipOk && idOk;
    }

    private Bucket bucket(String key, int capacity) {
        if (buckets == null) {
            synchronized (this) {
                if (buckets == null) {
                    buckets = Caffeine.newBuilder()
                            .expireAfterAccess(Duration.ofMinutes(windowMinutes * 2L))
                            .maximumSize(100_000)
                            .build();
                }
            }
        }
        return buckets.get(key, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(capacity,
                        Refill.greedy(capacity, Duration.ofMinutes(windowMinutes))))
                .build());
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
