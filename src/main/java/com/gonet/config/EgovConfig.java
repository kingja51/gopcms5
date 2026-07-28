package com.gonet.config;

import org.egovframe.rte.fdl.cmmn.trace.LeaveaTrace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * eGov 실행환경 기반 빈 — 클래식 eGov 의 context-common.xml 대응 (Java Config,
 * 호환성 가이드 p.4 "Java Config/Boot Configuration 방식 적용 가능").
 *
 * <p>{@code EgovAbstractServiceImpl}(전 서비스의 간접 부모)이
 * {@code @Resource(name = "leaveaTrace")} 로 요구하는 필수 빈 — 미등록 시
 * 모든 서비스 빈 생성이 실패한다(P2 기동에서 실측 확인).
 */
@Configuration
public class EgovConfig {

    @Bean
    public LeaveaTrace leaveaTrace() {
        return new LeaveaTrace();
    }
}
