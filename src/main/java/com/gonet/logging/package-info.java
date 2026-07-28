/**
 * logging_db 도메인 — 로그·통계 (테이블 log_* / stat_*).
 *
 * <p>다른 DB 의 행은 varchar(40) ID 값으로만 참조(크로스 DB FK 금지).
 * 로그 쓰기는 주 트랜잭션과 격리(REQUIRES_NEW/비동기) — conventions.md §3.
 */
package com.gonet.logging;
