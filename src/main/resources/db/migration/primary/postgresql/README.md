# PostgreSQL 마이그레이션 (예약)

PostgreSQL 전환·병행 운영 시 이 폴더에 **mariadb 폴더와 동일한 버전 번호·설명**으로
방언 번역판을 작성한다 (`V1__core_site_template_layout.sql` …).

- `{vendor}` 플레이스홀더가 런타임 DB 에 맞는 폴더 하나만 선택하므로 버전 충돌 없음.
- 번역 요점: `varchar` 그대로 · `CHARACTER SET ascii COLLATE ascii_bin` 제거(PG 불필요) ·
  `ENGINE/CHARSET` 절 제거 · `timestamp NULL DEFAULT current_timestamp()` →
  `timestamptz DEFAULT now()` · CHECK 는 동일 · FULLTEXT → `tsvector` + GIN 별도 설계.
- 현재는 MariaDB 우선 개발 — 이 폴더가 비어 있는 동안 PG 프로파일로 기동하지 말 것
  (기동 시 마이그레이션 0건으로 빈 스키마가 된다).
