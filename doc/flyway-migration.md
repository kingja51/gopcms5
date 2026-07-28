# gopcms5 — Flyway SQL 마이그레이션 규약

| 항목 | 값 |
|---|---|
| 작성일 | 2026-07-28 |
| 의존성 | `flyway-core` + `flyway-mysql`(MariaDB 담당) + `flyway-database-postgresql` — Boot BOM 버전 |
| 위치 | `src/main/resources/db/migration/{db}/{vendor}/` — db = primary·secondary·logging (+ 개발 시드 `db/devdata/{db}/`) |
| 관련 | [conventions.md](conventions.md)(PK 규칙) · [template-resolver-design.md](template-resolver-design.md) |

## 1. 대원칙

1. **스키마·기준 데이터 변경은 오직 마이그레이션 파일로만** — 콘솔 수기 DDL 금지.
   DB 의 현재 상태 = 마이그레이션 파일의 합, 이 등식이 깨지면 Flyway 를 쓰는 의미가 없다.
2. **적용된 파일은 수정 금지** (checksum 검증으로 기동 실패). 잘못된 마이그레이션의
   수정은 항상 **다음 버전 파일**로 전진시킨다. (예외: 아직 어떤 환경에도 적용 전인
   로컬 작업 파일은 자유 — push 이후는 불변)
3. 마이그레이션은 **벤더 폴더별 동일 버전 세트** — `{vendor}` 는 런타임 DB 에 맞춰
   mariadb / postgresql 폴더를 선택한다. **기본 개발·운영 DB = MariaDB 11.8**
   (utf8mb4_uca1400 콜레이션·CHECK 제약 등 11.x 문법 전제), PostgreSQL 은 번역판 준비 후
   ([primary/postgresql/README.md](../src/main/resources/db/migration/primary/postgresql/README.md) 참조).
4. **3-DB 분리** (conventions.md §3): primary_db(`tb_*`) · secondary_db(`tn_*`) ·
   logging_db(`log_*`/`stat_*`) 는 **각자 Flyway 인스턴스·각자 flyway_schema_history** 를
   가진다. 버전 번호는 DB 폴더별 독립 채번(primary V3 과 logging V1 은 무관).

## 2. 파일 네이밍·버전 정책

```
V{버전}__{설명}.sql        예) V3__bbs_tables.sql   (버전은 정수 증가, 설명은 소문자 스네이크)
R__{설명}.sql              반복 실행(뷰·프로시저 재정의 등) — 내용 변경 시마다 재적용
```

| 버전 대역 | 용도 |
|---|---|
| `V1` ~ `V899` | 스키마 + 기준(불변) 시드 — 운영 포함 전 환경 공통 |
| `V900` ~ | **개발 시드 예약 대역** — `db/devdata/` 폴더, dev/local 프로파일만 포함 |

- 기준 시드(레이아웃 7종·기본 템플릿·테마처럼 시스템이 전제하는 행)는 V 마이그레이션으로
  포함한다(V2). 데모·테스트 데이터는 devdata(V900+)로 격리.
- 시드 ID 는 고정 리터럴(전 환경 동일). UUIDv7 형식 규격을 지키되 수기 예약값
  (`…-7000-8000-…` + 뒤 12자리 일련)으로 앱 채번과 구분한다.

## 3. Spring 설정 설계 — DB 별 Flyway 3개 빈

Boot 자동구성은 **단일 DataSource + 단일 Flyway** 전제라 3-DB 에는 쓸 수 없다.
`FlywayConfig` 에서 DB 별 Flyway 빈 3개를 직접 구성하고, 자동구성은 끈다
(`spring.flyway.enabled: false`).

```java
/* 설계 스케치 — 각 DataSource 에 1:1, 기동 시 primary → secondary → logging 순 실행 */
Flyway primaryFlyway  = Flyway.configure().dataSource(primaryDs)
        .locations("classpath:db/migration/primary/"  + vendor /*, devdata(local/dev) */)
        .validateOnMigrate(true).cleanDisabled(true).load();
Flyway secondaryFlyway = …("classpath:db/migration/secondary/" + vendor)…
Flyway loggingFlyway   = …("classpath:db/migration/logging/"   + vendor)…
// vendor 는 각 DataSource URL 에서 판별(mariadb/postgresql) — {vendor} 플레이스홀더의 수동 대응
```

- 각 DB 에 자체 `flyway_schema_history` 가 생긴다 — 이력·버전이 완전 독립.
- dev/local 프로파일에서만 primary 위치에 `classpath:db/devdata/primary` 를 추가.
- MyBatis 초기화 전에 Flyway 3개가 모두 실행되도록 빈 의존 순서를 명시(`@DependsOn` 또는
  FlywayMigrationInitializer 순서) — 구현 시 확정.
- **기존 DB 에 도입할 때**(이미 테이블이 있는 환경): 해당 DB 의 Flyway 만 최초 1회
  `baselineOnMigrate(true)` + `baselineVersion("0")` 으로 기동 후 제거. 신규 DB 는 불필요.

## 4. 작업 절차 (1인 개발 기준)

```
① 대상 DB 결정(테이블 접두어가 곧 답: tb_→primary · tn_→secondary · log_/stat_→logging)
② db/migration/{db}/mariadb/V{그 폴더의 다음번호}__{설명}.sql 작성
③ 로컬 기동 → 해당 DB 의 flyway_schema_history 반영 확인
④ MyBatis 매퍼/도메인 코드 수정 → 커밋(마이그레이션 + 코드 한 커밋)
⑤ 로컬 DB 리셋이 필요하면: DROP DATABASE → CREATE → 재기동 (clean 은 비활성)
```

- 버전 번호는 git 이력이 곧 채번 장부다(1인 개발이라 경합 없음). 커밋 전
  **해당 DB 폴더**의 마지막 번호 확인 습관만 유지.
- `out-of-order`: **운영 false**(과거 번호 끼워넣기 금지). 단 **local/dev(devdata 포함)는
  true 필수** — devdata V900 대역이 이력에 먼저 남아, 이후 추가되는 스키마 버전(V4~)이
  out-of-order 로 거부되기 때문(P5 실측, FlywayConfig 에서 devdata 토글과 연동).

## 5. 스타일 규칙

- 파일 헤더에 목적·특이사항 주석 블록(V1 형식 참조). 테이블·컬럼 COMMENT 필수.
- PK/FK·감사 ID 컬럼은 `VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin`
  ([conventions.md](conventions.md) §1.2) — 신규 테이블마다 접두어를 §2 레지스트리에
  먼저 등록.
- 예약어 대문자, 식별자 소문자(백틱). 하나의 마이그레이션은 하나의 관심사
  (모듈 단위 — 예: V3 게시판, V4 회원). DDL 과 대량 DML 은 파일 분리.
- MariaDB DDL 은 암묵 커밋이라 실패 시 부분 적용될 수 있다 — 파일을 작게 유지하고,
  실패 복구는 수정 후 `flyway repair`(checksum/실패 기록 정리)로.
- **CHECK 제약은 테이블 레벨 명명 제약으로 쓴다** —
  `CONSTRAINT chk_{table}_{col} CHECK (...)`. 컬럼 뒤에 인라인으로 붙이면
  information_schema 에는 컬럼명으로 보이지만 `DROP CONSTRAINT` 로는 지워지지 않아
  (MariaDB 11.8 실측: `Can't DROP CONSTRAINT`), 값 목록 하나 늘리는 데도
  `MODIFY COLUMN` 으로 컬럼을 통째로 재정의해야 한다(V8 사례).

## 6. 현재 마이그레이션 목록

| 파일 | 내용 |
|---|---|
| `primary/mariadb/V1__core_site_template_layout.sql` | 코어 6테이블: tb_layout · tb_template · tb_theme · tb_site · tb_menu · tb_content (3축 모델 반영) |
| `primary/mariadb/V2__seed_layout_template_theme.sql` | 레이아웃 7종(A~G) · KRDS 기본 템플릿 · 테마 4종 기준 시드 |
| `primary/mariadb/V3__seed_design_templates.sql` | 시각 언어 템플릿 7종(blueprint-001~midnight-007, design-md 추천 리네이밍) + 테마 19종 |
| `primary/mariadb/V4__content_history.sql` | tb_content_history — 불변 버전 스냅샷(CNH, PAGE_COMPRESSED) |
| `primary/mariadb/V6__auth_tables.sql` | 인증·인가 21테이블(ADM·MBR·ROL·RUA 등) + vw_user_login |
| `primary/mariadb/V7__login_history.sql` | tb_login_history(LGH, insert-only) + 기존 계정 비밀번호 만료일 소급 |
| `primary/mariadb/V8__login_captcha_expired.sql` | 이력 결과코드 FAIL_EXPIRED 추가 + vw_user_login 에 captcha_required_yn 노출 |
| `primary/mariadb/V9__cms_extension_tables.sql` | CMS 확장 21테이블 — 파일(FGR·FIL) · 게시판(BBM·BCT·BBA·BBC·LIK·RPT) · 배너/팝업 · 일정(SCM·SCH) · 설문(SVM·SVY·SVQ·SVO·SVR·SVA) · 직원(EMP) · 공휴일·메일템플릿 |
| `logging/mariadb/V2__log_stat_extension.sql` | 로그 5종(error·file_download·privacy_access·pii_purge·security) + 통계 5종(stat_*) |
| `logging/mariadb/V1__log_access_audit.sql` | log_access · log_audit(bigint AUTO_INCREMENT 예외, (id, logged_at) 복합 PK) + shedlock |
| `secondary/mariadb/` | README 만 (테이블 확정 시 V1 부터) |
| `devdata/primary/V900__demo_site.sql` | (dev 전용) 데모 사이트 main |
| `devdata/primary/V901__demo_site_ai.sql` | (dev 전용) 데모 사이트 ai(인공지능학과) — 실측 IA: 폴더 6 + 컨텐츠 메뉴·페이지 10 + 게시판 자리 5 |
| `devdata/primary/V902__demo_site_nursing.sql` | (dev 전용) 데모 사이트 nursingcollege(간호대학) — 실측 IA: 폴더 4 + 컨텐츠 10, trust-002 템플릿 + teal 테마(복합 FK 실증) |
| `devdata/primary/V906__seed_auth.sql` | (dev 전용) 역할 계층 5종 + closure + admin/user1 계정 |
| `devdata/primary/V909__seed_url_access_rules.sql` | (dev 전용) URL 접근 규칙 20건 — 무매칭 DENY 의 기준 데이터(conventions §7) |
| `devdata/primary/V910__seed_mail_template.sql` | (dev 전용) 메일 템플릿 10건 — 발송 기능 도입 시 운영 기준 데이터로 승격 |

이후 예정 — primary: V5 게시판(BBM·BBA·BBC·FIL·LIK·RPT) → V6 회원·조직(ADM·MBR·DPT·STF·ROL·LGH)
→ V7 공통 프로그램(SCH·NTF·SVY·SVA·MWN·COD·AUD) → V8 배너·팝업·약관(BNR·POP·TRM) /
logging: V2 stat_* 집계 세트(접근 로그 적재 필터 구현과 동행)
