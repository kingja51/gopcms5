# secondary_db 마이그레이션 (클라이언트 프로그램)

- 테이블 접두어 **`tn_*`** (VIEW 는 공통 `vw_*`) — doc/conventions.md §3.
- 버전은 이 폴더 독립 채번(V1~) — DB 마다 flyway_schema_history 가 분리되므로
  primary 의 버전 번호와 무관하다.
- 클라이언트 프로그램(개별 사업) 테이블이 확정되면 V1 부터 작성. 그 전까지 비워 둔다
  (secondary Flyway 빈은 마이그레이션 0건이어도 기동에 지장 없음).
