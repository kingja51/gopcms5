/**
 * 스프링 구성 루트 — 공통 인프라(CacheConfig(Caffeine), EgovConfig(leaveaTrace))만 두고
 * 나머지는 관심사별 하위 패키지로 분리한다.
 *
 * <ul>
 *   <li>{@link com.gonet.config.datasource} — 3-DB DataSource/TxManager/SqlSessionFactory/
 *       MapperConfigurer, Flyway 3빈 (conventions.md §3)</li>
 *   <li>{@link com.gonet.config.view} — 3축(layout/template/theme) 뷰 해석:
 *       SiteTemplateViewResolver · ViewTemplateLookup · Thymeleaf 등록 · 부팅 스모크</li>
 *   <li>{@link com.gonet.config.web} — 요청 파이프라인 필터(AccessLog·SiteResolve)와
 *       ModelAdvice (필터 순서 규약은 AccessLogFilter 주석 참조)</li>
 *   <li>{@link com.gonet.config.security} — Security 다중 체인 + 인증 Provider 2종</li>
 * </ul>
 */
package com.gonet.config;
