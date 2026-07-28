/**
 * 요청 파이프라인 — 필터 순서가 계약이다:
 * AccessLogFilter(HIGHEST+10, 최외곽 — 응답 상태·소요시간 확보) →
 * SiteResolveFilter(HIGHEST+20 — 경로/파라미터/기본값 3단으로 SiteContext 확정) →
 * Security 체인. SiteContextModelAdvice 는 확정된 SiteContext 를 모델
 * (site/siteLayout/menuTree/themeClass/currentUri)로 노출한다.
 */
package com.gonet.config.web;
