package com.gonet.common.web;

import com.gonet.common.util.Csv;
import com.gonet.common.util.IpMatch;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 클라이언트 IP 해석 단일 경로 — 접근 로그·감사컬럼·관리자 IP 게이트가 모두 이 값을 쓴다.
 *
 * <p><b>X-Forwarded-For 는 위조 가능한 헤더</b>다. 그래서 신뢰 프록시 목록
 * ({@code gopcms.security.trusted-proxies}, CIDR CSV)이 <b>비어 있으면 헤더를 아예 무시</b>하고
 * TCP 원격 주소만 쓴다 — 리버스 프록시 뒤에 두기 전까지가 안전한 기본값이다.
 *
 * <p>목록이 설정돼 있고 직접 접속 주소가 신뢰 프록시일 때만 XFF 를 해석하며,
 * <b>오른쪽에서 왼쪽으로</b> 훑어 신뢰 프록시가 아닌 첫 주소를 실제 클라이언트로 본다
 * (공격자가 왼쪽에 임의 주소를 덧붙여도 신뢰 구간 밖 값은 채택되지 않는다).
 */
@Slf4j
@Component
public class ClientIpResolver {

    private static final String XFF = "X-Forwarded-For";

    private final Set<String> trustedProxies;

    public ClientIpResolver(
            @Value("${gopcms.security.trusted-proxies:}") String trustedProxiesCsv) {
        this.trustedProxies = Csv.toSet(trustedProxiesCsv);
        if (!trustedProxies.isEmpty()) {
            log.info("신뢰 프록시 {}개 설정 — X-Forwarded-For 해석 활성", trustedProxies.size());
        }
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies.isEmpty() || !isTrusted(remoteAddr)) {
            return remoteAddr;
        }

        // 순서·중복이 의미를 갖는 홉 목록이라 Set 이 아닌 List 로 다룬다
        String header = request.getHeader(XFF);
        List<String> forwarded = header == null || header.isBlank() ? List.of()
                : Arrays.stream(header.split(",")).map(String::trim)
                        .filter(hop -> !hop.isEmpty()).toList();
        for (int i = forwarded.size() - 1; i >= 0; i--) {
            if (!isTrusted(forwarded.get(i))) {
                return forwarded.get(i);
            }
        }
        // 전부 신뢰 프록시거나 헤더가 없다 — 직접 접속 주소가 최선의 값
        return forwarded.isEmpty() ? remoteAddr : forwarded.get(0);
    }

    /** 요청 객체를 직접 들고 있지 않은 지점(인증 Provider)용 — 요청 스코프 밖이면 null. */
    public String resolveCurrent() {
        if (RequestContextHolder
                .getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return resolve(attributes.getRequest());
        }
        return null;
    }

    private boolean isTrusted(String ip) {
        return trustedProxies.stream().anyMatch(pattern -> IpMatch.matchesPattern(pattern, ip));
    }
}
