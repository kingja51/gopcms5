package com.gonet.config.view;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

/**
 * ThymeleafViewResolver 교체 등록 — 빈 이름 {@code thymeleafViewResolver} 가
 * Boot 자동구성 조건(@ConditionalOnMissingBean)과 일치해 자동구성이 물러난다.
 * 엔진·템플릿 리졸버(prefix classpath:/templates/, suffix .html, 캐시)는 자동구성 그대로.
 */
@Configuration
public class ThymeleafViewConfig {

    @Bean
    public ThymeleafViewResolver thymeleafViewResolver(
            SpringTemplateEngine templateEngine, ViewTemplateLookup lookup) {
        SiteTemplateViewResolver resolver = new SiteTemplateViewResolver(lookup);
        resolver.setTemplateEngine(templateEngine);
        resolver.setCharacterEncoding("UTF-8");
        // 뷰 캐시 금지 — 사이트별 재작성 결과가 원본 뷰명 키로 캐시되면 레이아웃 오염
        // (Thymeleaf 템플릿 캐시가 물리명 기준으로 성능 담당). SiteTemplateViewResolver 주석 참조.
        resolver.setCache(false);
        // 전체 버퍼링 후 응답 — 부분 출력 중이면 폼의 지연 CSRF 토큰이 세션을 만들 때
        // "response committed" 로 실패 (P6-1 실측). ※ 커스텀 리졸버라 yml 의
        // spring.thymeleaf.servlet.* 속성이 자동 적용되지 않음 — 여기서 직접 설정.
        resolver.setProducePartialOutputWhileProcessing(false);
        return resolver;
    }
}
