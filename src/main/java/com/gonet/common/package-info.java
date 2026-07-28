/**
 * 공통 자원 — 전 모듈 재사용 (도메인 중복 구현 금지).
 *
 * <p>Uid/UidPrefix(UUIDv7 채번 · conventions.md §1), PageRequest, ApiResponse,
 * 감사컬럼 처리, 유틸리티. 특정 DB·도메인에 속하지 않는 코드만 둔다.
 */
package com.gonet.common;
