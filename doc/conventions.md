# gopcms5 — 식별자 · 네이밍 규약

| 항목 | 값 |
|---|---|
| 작성일 | 2026-07-28 |
| 적용 범위 | 전 테이블 PK · 전 컨트롤러 클래스 · URL/뷰 계약 |
| 관련 문서 | [template-resolver-design.md](template-resolver-design.md) · [README §5 eGov 규칙](../README.md) |

---

## 1. PK 규칙 — `접두어(3, 대문자) + "_" + UUID v7` = varchar(40)

```
SIT_01890a5d-ac96-774b-bcce-b302099a8057
└┬┘└──────────────────┬─────────────────┘
접두어(테이블 식별)      UUID v7 (36자, 시간순 정렬)
```

- **자릿수**: 3 + 1 + 36 = **정확히 40자** → `VARCHAR(40)` 고정.
- **UUID v7**(RFC 9562): 상위 48bit 가 Unix ms 타임스탬프 — **시간순 단조 증가**라
  B-tree 인덱스 지역성이 좋고(v4 의 랜덤 삽입 분산 문제 없음) 생성 시각이 ID 에 내장된다.
- **접두어 3자리**: **영문 대문자 `[A-Z]{3}`**, 전 시스템 유일. ID 만 보고 어느 테이블
  소속인지 즉시 구별하는 것이 목적(로그·FK·디버깅·API 응답에서 UUID 동형성 문제 해소).
  대문자 접두어 + 소문자 UUID 조합이라 경계 가독성도 확보된다.
- 검증 정규식(공통 Validator·테스트 고정):
  `^[A-Z]{3}_[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`

### 1.1 생성 원칙

- **앱 사이드 단일 유틸**에서만 채번한다(멀티 DB 벤더 원칙상 DB 함수 채번 금지 —
  MariaDB/PostgreSQL 함수 편차 회피). 예: `Uid.next(UidPrefix.SIT)`.
- Java 21 표준에는 v7 이 없으므로 RFC 9562 대로 자체 유틸 구현(타임스탬프 48bit +
  rand_a/rand_b `SecureRandom`) 또는 검증된 경량 라이브러리 1개 채택 — 구현 시 결정.
  클래스명은 eGov 확장 규칙에 따라 `Egov` 접두 금지(`Uid`, `UidPrefix` 권장).
- 접두어는 코드에 **enum(UidPrefix)** 으로 등록 — §2 레지스트리와 1:1, 오타·중복을
  컴파일 타임에 차단. 새 테이블 = ①§2 표 등록 → ②enum 추가 → ③DDL 순서 강제.
- eGov `fdl-idgnr` 는 의존성으로 유지(선택 라이브러리)하되 PK 채번에는 사용하지 않는다
  — 호환성 가이드라인은 idgnr **사용**을 강제하지 않음.

### 1.2 저장 원칙

- 컬럼 정의: `VARCHAR(40) NOT NULL` + PK. 형식은 항상 접두어 대문자 + UUID 소문자
  (생성 유틸이 보장 — 수기 입력 금지).
- MariaDB 는 PK/FK 컬럼에 `CHARACTER SET ascii COLLATE ascii_bin` 지정 권장
  (utf8mb4 대비 인덱스 키 최대 4배 절약 + 대소문자 오염 차단 — 현행 DDL 의
  `utf8mb4_uca1400_ai_ci` 는 대소문자 무시 콜레이션이라 `SIT_x` 와 `sit_x` 가 같은
  키로 취급됨 → PK/FK 컬럼만이라도 `ascii_bin` 권장). PostgreSQL 은 기본 그대로.
- FK 컬럼명은 `{참조테이블 단수}_id` (예: `site_id`, `menu_id`) — 값에 접두어가 있어
  조인 방향이 값만으로도 읽힌다.

## 2. 접두어 레지스트리 (테이블별)

> **유일 원본(single source of truth)** — 새 테이블은 반드시 여기 등록 후 사용.
> 접두어는 재사용·변경 금지(폐기 테이블의 접두어는 `(예약-폐기)` 로 남긴다).

### 2.1 코어 (사이트·템플릿·테마·레이아웃·메뉴·컨텐츠)

| 접두어 | 테이블 | 모듈 | 비고 |
|---|---|---|---|
| `SIT` | tb_site | 사이트관리 | site_code(ai·med…)는 별도 UNIQUE 자연키 |
| `TPL` | tb_template | 템플릿관리 | 시각 언어(CSS) 축 — template_code 는 자연키 |
| `THM` | tb_theme | 템플릿관리(부속) | 템플릿별 색 변형(blue·teal·indigo·green…) |
| `LAY` | tb_layout | 템플릿관리(부속) | 구조 프레임(layout-001~007 = 와이어프레임 A~G) |
| `MNU` | tb_menu | 메뉴관리 | depth 1~4 트리 |
| `CNT` | tb_content | 컨텐츠관리 | slug 페이지 |
| `CNH` | tb_content_history | 컨텐츠관리(부속) | 버전 이력 — 불변 스냅샷(insert-only, updated_* 없음) |
| `BNR` | tb_banner | 사이트관리(부속) | MAIN_HERO / SUB_HERO / 팝업 |
| `POP` | tb_popup | 사이트관리(부속) | 오늘 하루 안 보기 |
| `TRM` | tb_terms | 사이트관리(부속) | 약관 버전 관리 |

### 2.2 게시판 · 파일 (V9)

| 접두어 | 테이블 | 비고 |
|---|---|---|
| `BBM` | tb_bbs_master | 게시판 정의(스킨 NOTICE·GALLERY…) |
| `BBA` | tb_bbs_article | 게시글 |
| `BBC` | tb_bbs_comment | 댓글 |
| `BCT` | tb_bbs_category | 게시판 카테고리 |
| `LIK` | tb_bbs_like | 좋아요 (게시글·댓글·컨텐츠 통합) |
| `RPT` | tb_bbs_report | 신고 (게시글·댓글·컨텐츠 통합) |
| `FGR` | tb_file_group | 파일 그룹 — 업로드 묶음의 소유·권한 단위 |
| `FIL` | tb_file | 업로드 파일 |

※ V9 에서 실제 테이블명이 확정되며 세 항목의 대상이 바뀌었다(설계 초안 → 구현):
`FIL` tb_attach_file→**tb_file** · `LIK` tb_like→**tb_bbs_like** · `RPT` tb_report→**tb_bbs_report**.
접두어 자체는 그대로다(재사용·변경 금지 원칙 유지).

### 2.3 회원·조직·인증 (V6 확장)

| 접두어 | 테이블 | 모듈 |
|---|---|---|
| `ADM` | tb_admin | 관리자 회원 |
| `AGR` | tb_admin_group | 관리자 그룹(접속 정책) |
| `ARL` | tb_admin_role | 관리자-역할 매핑 |
| `AIP` | tb_admin_allow_ip | 관리자 허용 IP(로그인 게이트) |
| `APH` | tb_admin_password_history | 관리자 비밀번호 이력 |
| `MBR` | tb_member | 사용자 회원 |
| `MBC` | tb_member_consent | 회원 동의 이력 |
| `MDN` | tb_member_dormant_notice | 휴면 안내 발송 이력 |
| `MBO` | tb_member_oauth | 회원 OAuth 매핑 |
| `MPH` | tb_member_password_history | 회원 비밀번호 이력 |
| `DPT` | tb_department | 부서 관리 (명칭 확정 — 구 tb_dept 표기 대체) |
| `STF` | tb_staff | 직원관리 |
| `ROL` | tb_role | 역할(계층형) |
| `AUT` | tb_auth | 권한(세부 기능) |
| `RLA` | tb_role_auth | 역할-권한 매핑 |
| `RLH` | tb_role_hierarchy | 역할 계층 closure |
| `RUA` | tb_role_url_access | URL 접근제어 규칙 |
| `LGH` | tb_login_history | 로그인 이력(관리자·사용자 공용) |

※ 휴면/탈퇴 보관 테이블(tb_member_dormant·tb_*_withdraw)은 원 PK 를 승계 — 신규 접두어 없음.

### 2.4 공통 프로그램·운영

| 접두어 | 테이블 | 비고 |
|---|---|---|
| `SCM` | tb_schedule_master | 일정 마스터 (사이트·메뉴 소유) |
| `SCH` | tb_schedule | 개별 일정 |
| `SVM` | tb_survey_master | 설문 마스터 |
| `SVY` | tb_survey | 설문 |
| `SVQ` | tb_survey_question | 설문 문항 |
| `SVO` | tb_survey_option | 설문 선택지 |
| `SVR` | tb_survey_response | 설문 응답 헤더 |
| `SVA` | tb_survey_answer | 설문 응답 상세 |
| `BNR` | tb_banner | 배너 |
| `POP` | tb_popup | 팝업 |
| `HOL` | tb_holiday | 공휴일 |
| `MTP` | tb_mail_template | 메일 템플릿 |
| `NTF` | tb_notification | 알림함 |
| `MWN` | tb_minwon | 민원 |
| `COD` | tb_code | 공통코드 |
| `CGR` | tb_code_group | 공통코드 그룹 |
| `AUD` | tb_audit_log | 관리자 감사 로그 |

### 2.5 logging_db

로그 테이블은 대량 append 라 시퀀스(bigint AUTO_INCREMENT) PK 를 쓴다 — §1 의 예외다.
UUID 채번이 필요한 것은 아래 하나뿐이다.

| 접두어 | 테이블 | 비고 |
|---|---|---|
| `PPG` | log_pii_purge | 개인정보 파기 이력 (배치가 개별 채번) |

## 3. DB · 테이블 네이밍 — 3-DB 분리

| DB | 용도 | 테이블 접두어 | TxManager 빈 |
|---|---|---|---|
| **primary_db** | GOPCMS 기본 프로그램 (9개 모듈) | `tb_*` | `primaryTransactionManager` |
| **secondary_db** | 클라이언트 프로그램 (개별 사업) | `tn_*` | `secondaryTransactionManager` |
| **logging_db** | 각종 로그 · 통계 | `log_*`(이벤트) / `stat_*`(집계) | `loggingTransactionManager` |

- **VIEW 는 전 DB 공통 `vw_*`** (예: `vw_user_login`).
- 각 DB 는 자체 DataSource · TransactionManager · SqlSessionFactory · MapperConfigurer 를
  가진다. Mapper 패키지도 DB 별 분리(`…primary.**.mapper` / `…secondary.**.mapper` /
  `…logging.**.mapper`)해 각 SqlSessionFactory 가 자기 패키지만 스캔.
- **크로스 DB JOIN·FK 금지** — 다른 DB 의 행은 varchar(40) ID 값으로만 보관하고 서비스
  계층에서 조합한다. PK 접두어(§1) 덕에 값만으로 출처 테이블이 판별된다.
- **로그 기록은 주 트랜잭션과 격리** — logging_db 쓰기는 `REQUIRES_NEW`(별도 TM) 또는
  비동기로, 로그 실패가 본 트랜잭션을 롤백시키지 않게 한다.
- **PK 예외(확정 2026-07-28)**: logging_db 의 대량 로그(log_*/stat_*)는
  `bigint AUTO_INCREMENT` PK 를 사용한다 — 단순 로그는 숫자 채번이 적합(삽입 처리량·
  저장 효율). 복합 PK `(id, logged_at)` 은 logged_at 파티셔닝 대비. 따라서 §2 접두어
  레지스트리는 **tb_/tn_ 테이블에만 적용**된다.
- Flyway 는 DB 별 독립 이력(`db/migration/{primary|secondary|logging}/{vendor}`) —
  [flyway-migration.md](flyway-migration.md) §3.

## 4. 컨트롤러 네이밍 — `*ApiController` / `*UsrController` / `*AdmController`

클래스명 = **`{도메인}{구분}Controller`** (예: `BoardUsrController`). 구분 접미어가
패키지·URL·뷰 처리·보안 경계와 1:1 로 대응한다.

패키지는 DB 축 우선 수직 슬라이스(§3) — `com.gonet.primary.{domain}.controller` 에
Usr/Adm/Api 3종이 공존하고, 접미어가 URL·뷰 해석·보안 경계를 결정한다.

| 접미어 | 대상 | URL 네임스페이스 | 반환 | 뷰 해석 |
|---|---|---|---|---|
| `*UsrController` | 사용자 화면 | `/{sc}/**` `/bbs/**` `/member/**` `/search` `/prg/**` | 논리 뷰명 `front/**` | **SiteTemplateViewResolver 재작성 대상** (템플릿 적용) |
| `*AdmController` | 관리자 화면 | `/adm/**` | 뷰명 `adm/**` | 재작성 제외(템플릿 무관) · 관리자 인증+2FA |
| `*ApiController` | JSON API | `/api/v1/**` | `@RestController` JSON | 뷰 없음 · springdoc 노출 · Bucket4j 레이트리밋 |

### 4.1 예시 (게시판 도메인 1벌)

```
com.gonet.primary.bbs.controller.BoardUsrController   GET /bbs/{sc}/{bbsCode}       → "front/board/list"
com.gonet.primary.bbs.controller.BoardAdmController   GET /adm/bbs                  → "adm/bbs/list"
com.gonet.primary.bbs.controller.BoardApiController   GET /api/v1/bbs/{id}/articles → JSON
                                          (id = "BBM_0189…" — 접두어로 대상 테이블 자명)
```

### 4.2 계층 네이밍 (eGov 규칙과 결합)

컨트롤러 3종은 **같은 서비스 인터페이스를 공유**한다 — 구분 접미어는 진입점(웹 계층)
에만 존재하고 비즈니스 계층은 도메인당 1벌:

```
BoardUsrController ┐
BoardAdmController ┼─▶ BoardService(인터페이스)
BoardApiController ┘      └ BoardServiceImpl extends AbstractCmsService(→EgovAbstractServiceImpl)
                              └ BoardMapper (@EgovMapper, eGov MapperConfigurer 스캔)
```

- htmx 부분 응답(조각 반환)도 화면 소속이므로 `*UsrController`/`*AdmController` 가 담당
  — `*ApiController` 는 순수 데이터(JSON) 전용. (배너 조각처럼 JS 렌더용 JSON 은 Api.)
- 보안 경계는 URL 네임스페이스 기준으로 Spring Security 매처 3장(`/adm/**`,
  `/api/v1/**`, 나머지)과 일치 — 클래스 접미어·패키지·URL·보안이 항상 같이 움직인다.

## 5. URL 계약 — 사용자 사이트 컨텐츠

| URL | 화면 | 비고 |
|---|---|---|
| `/{siteCode}/index` | 랜딩 페이지(홈) | 와이어프레임의 `/{sc}/home` 표기는 `index` 로 확정 |
| `/{siteCode}/sitemap` | 사이트맵 | 동일 menuTree 를 1~4뎁스 전체 전개 |
| `/{siteCode}/{slug}` | 컨텐츠 페이지 | `tb_content.slug` (site_id+slug UNIQUE) |

- **매칭 순서**: 고정 라우트(`index`·`sitemap`)를 먼저 선언하고 `{slug}` 캐치올을
  마지막에 둔다 — 컨트롤러 매핑 순서로 보장.
- **예약 slug**: `index` `sitemap` `search` `member` `bbs` `prg` `adm` `api` 는
  slug 로 등록 금지(고정 라우트·상위 네임스페이스와 충돌) — 컨텐츠 등록 시 Validator 로 차단.
- **slug 패턴**: `^[a-z0-9-]{1,200}$` (소문자·숫자·하이픈) — URL 인코딩·대소문자 이슈 차단.
- **siteCode 는 항상 경로에 유지** — 커스텀 도메인 접속 시에도 도메인은 siteCode 판별
  보조일 뿐, canonical URL 은 `/{siteCode}/…` 형태를 유지한다(SiteResolveFilter 정규화).
- 처리 주체: `ContentUsrController` (논리 뷰 `front/index` · `front/sitemap` ·
  `front/content`) — 템플릿 Resolver 재작성 대상.

## 6. 개인정보(PII) 암호화 — `@Encrypt`

| 항목 | 값 |
|---|---|
| 알고리즘 | **AES-256-GCM** (IV 12바이트 랜덤/건, 인증태그 128bit) |
| 마스터 키 | 환경변수 `GOPCMS_PII_MASTER_KEY` — **base64 인코딩 32바이트**. 미주입·길이 오류 시 fail-fast 부팅 실패(의도된 동작) |
| 선언 방식 | DTO/도메인 필드에 **`@Encrypt`** 어노테이션 — MyBatis TypeHandler 가 저장 시 암호화, 조회 시 복호화(서비스 코드는 평문만 다룸) |
| 저장 형식 | **`{AG}` + base64(IV ‖ 암호문 ‖ 태그)** — `{AG}` 프리픽스가 암호문 식별자 |

- **`{AG}` 프리픽스의 역할**: 값만 보고 암호화 여부 판별 → 평문 데이터 이행기 감지,
  이중 암호화 방지, 복호화 시 `{AG}` 없으면 평문으로 간주(이행 완료 후 강제 모드 전환).
  키 회전이 필요해지면 프리픽스 버전 증설(`{AG2}`)로 신·구 키 병행 복호화.
- **암호화 컬럼은 검색 불가**(랜덤 IV — 같은 평문도 매번 다른 암호문). 조회가 필요한
  컬럼은 별도 해시 컬럼(`{컬럼}_hash`, SHA-256) 병행 설계.
- **컬럼 길이 여유**: 평문 n바이트 → `{AG}` 4자 + base64(n+28) ≈ `4 + ⌈(n+28)/3⌉×4`.
  예: 평문 100B → 약 176자. DDL 설계 시 평문 기준의 약 2배 + 40자 여유 확보.
- 마스킹 출력(`MaskUtils`)·접근 기록(logging_db `log_privacy`)은 별도 계층 — 암호화와 병행.

## 7. URL 접근 규칙 등록 — 새 URL 을 만들 때 반드시 함께 하는 일

인가는 `tb_role_url_access` 단일 원천이다(`DynamicAuthorizationManager`).
**규칙이 없는 URL 은 열리지 않는다** — `SecurityConfig` 에 예외를 추가하는 방식은 쓰지 않는다.

| 열 | 의미 |
|---|---|
| `url_pattern` | Ant 패턴. `*`=한 세그먼트, `**`=하위 전체 (예: `/*/member/**`) |
| `http_method` | `ALL` 또는 단일 메서드 — 조회는 공개·쓰기는 인증 같은 분리에 사용 |
| `access_type` | `PERMIT_ALL` · `AUTHENTICATED` · `ANONYMOUS` · `ROLE` · `AUTH` · `IP_ONLY` · `DENY` |
| `required_roles` | `ROLE` 타입의 **role_id CSV** — 사용자 `role_ids`(계층 전개 스냅샷)와 교집합 판정 |
| `allowed_user_types` | `MEMBER`/`ADMIN` 경계. 비우면 유형 무관 |
| `site_id` | NULL=전역. 값이 있으면 **해당 사이트 요청에만** 적용되며 전역 규칙보다 우선 |
| `priority` | 작을수록 먼저. 동순위는 사이트 규칙 → 긴 패턴 순 |

**평가**: 정렬 순서대로 훑어 **첫 매칭 규칙이 최종 결정**이다(이후 규칙은 보지 않음).
어떤 규칙에도 걸리지 않으면 **DENY** 이며 `접근 규칙 없음 → DENY` WARN 로그가 남는다.
화면이 403/로그인 리다이렉트로 튕기면 이 로그부터 확인한다.

**절차**

1. 컨트롤러 매핑을 추가한다 (`{도메인}{Usr|Adm|Api}Controller` — §4).
2. 같은 커밋의 마이그레이션에 규칙 INSERT 를 넣는다. 콘솔 수기 INSERT 금지(CLAUDE.md).
   기존 규칙보다 **구체적인 패턴은 더 작은 priority** 를 줘야 상위 `/**` 에 먹히지 않는다.
   운영 중 조정은 **관리 화면(`/adm/url-access`)** 에서 한다 — 저장 시 캐시가 비워져
   다음 요청부터 반영된다(마이그레이션은 배포 기준선, 화면은 운영 조정).
3. DB 를 직접 고쳤다면 `/adm/url-access` 의 "캐시 비우기"(= `UrlAccessService.evictCache()`).
4. 검증: 익명·회원·관리자 3주체로 각각 호출해 기대 코드(200 / 302 로그인 / 403)를 확인한다.
   `http/01-front-smoke.http` 의 "인가 계약" 절에 케이스를 추가한다.

**주의**: 정적 자원(`/css/**`·`/js/**`)·`/error`·`/actuator/health` 규칙을 지우면
화면이 통째로 무너진다. `/**` PERMIT_ALL(최후 규칙)을 지우면 전 사이트가 닫힌다 —
비공개 전환은 최후 규칙 삭제가 아니라 **사이트 스코프 규칙 추가**로 한다.
역할 계층을 바꿨다면 `RoleService.rebuildHierarchy()` 로 closure·CSV 스냅샷을 재계산한다
(안 하면 인가가 조용히 어긋난다).

## 8. 결정 요약

1. PK = `VARCHAR(40)` 고정 형식 `PRE_uuid-v7`(접두어 대문자 3자리) — 시간순 정렬 + ID 자체로 테이블 식별.
2. 채번은 앱 유틸 단일 경로(`Uid.next(UidPrefix.X)`) — DB 벤더 중립, enum 으로 오타 차단.
3. 접두어 레지스트리(§2)가 유일 원본 — 등록 없는 접두어 사용 금지, 재사용 금지.
4. DB 3분리 = primary(`tb_`) · secondary(`tn_`) · logging(`log_`/`stat_`) + 공통 VIEW `vw_` —
   크로스 DB JOIN/FK 금지, 로그 쓰기는 주 트랜잭션과 격리.
5. 컨트롤러 = `{도메인}{Usr|Adm|Api}Controller` — 접미어가 패키지·URL·뷰 해석·보안과 1:1.
6. 서비스/매퍼는 도메인당 1벌 공유 — eGov 아키텍처 규칙(인터페이스 주입) 그대로.
7. 컨텐츠 URL = `/{siteCode}/{index|sitemap|{slug}}` — 예약 slug 차단, siteCode 는 경로 유지.
8. PII = `@Encrypt`(AES-256-GCM, `{AG}` 프리픽스, 마스터키 base64 32B fail-fast) — 검색은 해시 컬럼.
9. 인가 = `tb_role_url_access` 단일 원천(priority ASC, 사이트 규칙 우선, **무매칭 DENY**) —
   새 URL 은 규칙 등록이 동반돼야 열린다(§7). 역할은 전개된 `role_ids` 교집합으로 판정.
