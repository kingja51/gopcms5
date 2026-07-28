/**
 * 3축(layout/template/theme) 뷰 해석 (doc/template-resolver-design.md) —
 * SiteTemplateViewResolver 의 3단 폴백(sites/{siteCode} → layouts/{layout} → layouts/_default),
 * ViewTemplateLookup(존재 판정 캐시), ThymeleafViewConfig(리졸버 교체 등록),
 * LayoutSmokeRunner(부팅 시 레이아웃 뷰 존재 검증).
 *
 * <p>뷰 캐시 OFF·전체 버퍼링(setProducePartialOutputWhileProcessing(false)) 규약은
 * ThymeleafViewConfig 주석이 정본.
 */
package com.gonet.config.view;
