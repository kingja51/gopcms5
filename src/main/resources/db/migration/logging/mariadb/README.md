# logging_db 마이그레이션 (로그 · 통계)

- 테이블 접두어 **`log_*`**(이벤트 로그) / **`stat_*`**(집계 통계), VIEW 는 공통 `vw_*` — doc/conventions.md §3.
- **PK 정책(확정 2026-07-28)**: 대량 로그는 `bigint AUTO_INCREMENT` — varchar(40) UUID
  규약의 명시적 예외. 복합 PK `(id, logged_at)` 는 파티셔닝 대비.
- 버전은 이 폴더 독립 채번 — V1: log_access · log_audit · shedlock(분산 락).
- logging_db 테이블은 다른 DB 의 ID 를 **FK 제약 없이** varchar(40) 값으로만 보관
  (크로스 DB FK 불가 — 접두어 덕에 값만으로 출처 판별).
- 로그 쓰기는 주 트랜잭션과 격리(REQUIRES_NEW/비동기) — 적재 필터·AuditLogger 는
  logging 도메인 구현 페이즈에서.
