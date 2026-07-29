package com.gonet.primary.member.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 인증번호 발송 제한 — <b>IP 축</b>.
 *
 * <p>계정별 쿨다운(60초)만으로는 부족하다. 쿨다운은 "한 계정에 연달아 보내기" 를 막지만,
 * 공격자는 아이디를 바꿔 가며 계정마다 한 번씩 요청하면 된다. 그러면 쿨다운에 한 번도
 * 걸리지 않으면서 다수에게 메일을 보낼 수 있다 — 우리 서버가 스팸 발신자가 되고,
 * 메일 평판이 떨어지면 정상 메일까지 막힌다.
 *
 * <p>그래서 <b>보내는 쪽</b>을 센다. 계정별 쿨다운과 함께 두 축이 되며, 로그인
 * 레이트리밋({@code LoginRateLimiter})과는 목적이 달라 별도 한도를 쓴다 — 이쪽은
 * 인증 시도가 아니라 <b>메일 발송량</b>을 억제한다.
 *
 * <p>저장소는 인메모리다. 다중화하면 인스턴스마다 따로 세므로 실질 한도가 인스턴스
 * 수만큼 늘어난다 — 세션 저장소와 같은 시기에 공유 저장소로 옮겨야 한다.
 */
@Component
public class OtpRateLimiter {

    /**
     * IP 당 허용 발송 횟수.
     *
     * <p>정상 사용자는 한 시간에 몇 번이면 충분하다(코드를 못 받아 재발송하는 경우까지
     * 고려). 사무실·학교 공용 IP 를 감안해 여유를 두되, 대량 발송은 확실히 막히는 선.
     */
    @Value("${gopcms.member.otp.rate.per-ip:10}")
    private int perIp;

    /** 시간창(분). */
    @Value("${gopcms.member.otp.rate.window-minutes:60}")
    private int windowMinutes;

    private volatile Cache<String, Bucket> buckets;

    /**
     * 발송 1회를 소비한다.
     *
     * @return 허용되면 true, 한도를 넘었으면 false
     */
    public boolean tryConsume(String clientIp) {
        return bucket(nullSafe(clientIp)).tryConsume(1);
    }

    private Bucket bucket(String key) {
        if (buckets == null) {
            synchronized (this) {
                if (buckets == null) {
                    // 시간창의 2배 동안 두고 버린다 — 영구 보관하면 IP 마다 객체가 쌓여
                    // 메모리가 새고, 시간창보다 짧게 버리면 카운터가 초기화돼 제한이 무력해진다
                    buckets = Caffeine.newBuilder()
                            .expireAfterAccess(Duration.ofMinutes(windowMinutes * 2L))
                            .maximumSize(100_000)
                            .build();
                }
            }
        }
        return buckets.get(key, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(perIp,
                        Refill.greedy(perIp, Duration.ofMinutes(windowMinutes))))
                .build());
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
