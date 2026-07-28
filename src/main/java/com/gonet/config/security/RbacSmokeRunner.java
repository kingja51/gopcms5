package com.gonet.config.security;

import com.gonet.primary.auth.dto.UrlAccessRule;
import com.gonet.primary.auth.service.UrlAccessService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 스모크 — 인가 규칙 공백 상태로 뜨는 것을 막는다 (LayoutSmokeRunner 와 같은 취지).
 *
 * <p>규칙이 0건이면 {@link DynamicAuthorizationManager} 의 "무매칭 DENY" 계약상 로그인
 * 화면을 포함한 <b>모든 요청이 차단</b>되고, 앱을 통해 규칙을 넣을 방법도 사라진다.
 * 이 상태로 기동하는 것은 조용한 전면 장애이므로 기동을 중단한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacSmokeRunner implements ApplicationRunner {

    private final UrlAccessService urlAccessService;

    @Override
    public void run(ApplicationArguments args) {
        List<UrlAccessRule> rules = urlAccessService.getActiveRules();
        if (rules.isEmpty()) {
            throw new IllegalStateException("""
                    URL 접근 규칙(tb_role_url_access)이 0건 — 기동 중단.
                    무매칭 DENY 계약상 /adm/login 을 포함한 전 요청이 차단된다.
                    dev 는 gopcms.flyway.devdata=true (V909 시드), 운영은 규칙 이관이 선행돼야 한다.""");
        }
        log.info("RbacSmoke OK — URL 접근 규칙 {}건 로드 (최후 규칙: {})",
                rules.size(), rules.get(rules.size() - 1).label());
    }
}
