/**
 * primary_db 도메인 — GOPCMS 기본 프로그램 (테이블 tb_*).
 *
 * <p>도메인별 수직 슬라이스: {@code primary.<domain>.{controller,service,mapper,dto}}.
 * 컨트롤러는 {도메인}{Usr|Adm|Api}Controller (conventions.md §4),
 * 서비스는 인터페이스 + AbstractCmsService 상속 구현, 매퍼는 @EgovMapper.
 * 예: primary.site, primary.menu, primary.content, primary.template.
 */
package com.gonet.primary;
