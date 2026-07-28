/**
 * 배치/스케줄러 — ShedLock 분산 락 기반 (락 테이블은 logging_db.shedlock).
 *
 * <p>소프트 삭제 정리, 로그 보존, 예약 발행(tb_content.publish_scheduled_at) 등.
 * 긴 외부호출은 트랜잭션 격리(NOT_SUPPORTED/REQUIRES_NEW) — CLAUDE.md 트랜잭션 함정.
 */
package com.gonet.scheduler;
