# 회원 관리 개발 매뉴얼

gopcms5 회원 도메인(P10)의 기능별 개발 매뉴얼이다. **코드를 고치기 전에 이 문서에서
해당 기능 절을 먼저 읽는다.** 각 절은 "무엇을 하는가 → 어디에 있는가 → 왜 그렇게
만들었는가 → 고칠 때 함께 봐야 할 것" 순서다.

- 기준일: 2026-07-30 (P10 전 항목 완료 시점)
- 관련 정본: [PLAN.md](../PLAN.md) §P10 · [conventions.md](conventions.md) §2·§6·§7 ·
  [flyway-migration.md](flyway-migration.md)

> 이 문서는 **구현된 것만** 적는다. 계획은 PLAN.md 에 있다. 둘이 어긋나면 코드가 정답이고
> 이 문서가 틀린 것이다 — 발견하면 고쳐 주기 바란다.

---

## 0. 한눈에 보기

### 0.1 계정 생애

```
                    ┌──────────────── 복원(2수단) ─────────────┐
                    ▼                                          │
 [가입] ──▶ ACTIVE ──[미접속 1년]──▶ 휴면 ──[+1년]──▶ 탈퇴 ──[+1년]──▶ 완전 삭제
              │      tb_member      tb_member_dormant  PII 파기    행 자체 삭제
              │                                        + 원장      + 파기 이력
              └──[본인 요청 / 관리자 강제]──▶ 탈퇴
```

- **휴면까지는 되돌릴 수 있다.** 탈퇴부터는 되돌릴 수 없다 — 그래서 탈퇴 이후의 모든
  처리는 dry-run 이 기본이고 1회 건수 상한이 붙는다.
- 탈퇴 경로는 셋(본인 요청 · 관리자 강제 · 휴면 만료 배치)이지만 **처리 코드는 하나**다
  ([§7.1](#71-탈퇴-단일-경로)).

### 0.2 URL 지도

| URL | 담당 | 인증 |
|---|---|---|
| `/login?siteCode=` | `LoginUsrController` | 공개 |
| `/{siteCode}/member/join` ~ `/join/complete` | `MemberJoinUsrController` | 공개 |
| `/{siteCode}/member/find-id` | `MemberFindUsrController` | 공개 |
| `/{siteCode}/member/find-password` | `MemberPasswordResetUsrController` | 공개 |
| `/{siteCode}/member/dormant` | `DormantRestoreUsrController` | 공개 |
| `/{siteCode}/member/mypage` ~ `/mypage/withdraw` | `MemberMyPageUsrController` | 회원 |
| **`/member/identity/nice`** | `NiceCheckUsrController` | 공개 |
| **`/member/oauth2/{provider}`** | `MemberOAuth2UsrController` | 공개 |
| `/adm/member` ~ | `MemberAdmController` | 관리자 |

**굵은 두 경로에는 `{siteCode}` 가 없다.** 콜백 URL 을 외부 사업자 콘솔에 문자열로
등록하기 때문이다 — 사이트마다 경로가 갈리면 사이트 수만큼 계약을 맺어야 한다.
사이트는 쿼리 파라미터와 세션으로 잇는다([§4.2](#42-경로에-sitecode-가-없는-이유)).

### 0.3 패키지

```
primary/member/
  ├ controller/   Usr 6종 + Adm 1종
  ├ dto/          MemberDto · 폼 · 세션 · 관리자 조회행
  ├ mapper/       MemberMapper · MemberAdmMapper · MemberLifecycleMapper · MemberOtpMapper
  ├ service/      가입·찾기·재설정·프로필·생명주기·휴면복원·재인증·발송제한
  └ oauth2/       소셜 로그인 일체 (controller/dto/mapper/service)
primary/identity/  NICE 본인인증 (config/controller/dto/service)
logging/privacy/   개인정보 접근 이력
logging/purge/     개인정보 파기 이력
logging/retention/ 로그 보존기간 파기
scheduler/         MemberLifecycleJob · LogRetentionJob
```

---

## 1. 데이터 모델

### 1.1 테이블

| 테이블 | 용도 | 비고 |
|---|---|---|
| `tb_member` | 활성 회원 | PII 다수 — [§2](#2-개인정보-취급-pii) |
| `tb_member_consent` | 동의 이력 | UPDATE 아닌 **INSERT 누적** |
| `tb_member_dormant` | 휴면 분리보관 | `tb_member` 와 컬럼 구조 동일 |
| `tb_member_withdraw` | 탈퇴 원장 | **해시 + 마스킹 이름**(V11) — 사람 식별 불가 |
| `tb_member_dormant_notice` | 휴면 사전 안내 이력 | `(member_id, stage)` UNIQUE |
| `tb_member_oauth` | 소셜 계정 연결 | `(provider, provider_user_id, delete_yn)` UNIQUE |
| `tb_member_otp` | 인증번호 | **평문 미보관**(HMAC), 시도 횟수를 행에 둔다 |
| `tb_member_password_history` | 비밀번호 재사용 방지 | |
| `tb_login_history` | 로그인 이력 | 성격은 로그인데 **primary_db** 에 있다 |

마이그레이션: `V6__auth_tables.sql`(본체) · `V7__login_history.sql` ·
`V8__login_captcha_expired.sql` · `V10__member_otp.sql` ·
`V11__member_withdraw_masked_name.sql` · `V12__member_name_not_null.sql`

### 1.2 상태값 — DDL CHECK 와 1:1

코드에 문자열을 새로 만들지 말 것. CHECK 위반은 **저장이 통째로 실패**하는데, 로그에는
제약 이름만 남아 원인을 찾기 어렵다(실제로 여러 번 겪었다).

| 컬럼 | 허용값 |
|---|---|
| `tb_member.status` | `ACTIVE` `LOCKED` `EMAIL_PENDING` `SUSPENDED` |
| `tb_member.join_type` | `EMAIL` `KAKAO` `NAVER` `GOOGLE` `APPLE` `HOMEPAGE` `MOBILE` |
| `tb_member.gender` | `M` `F` `N` |
| `tb_member_withdraw.withdraw_status` | `USER_REQUEST` `ADMIN_FORCE` `DORMANT_EXPIRED` |
| `tb_member_otp.purpose` | `DORMANT_RESTORE` `EMAIL_VERIFY` `PASSWORD_RESET` |
| `tb_member_oauth.provider` | `NAVER` `KAKAO` `GOOGLE` |

> `withdraw_status` 는 이름과 달리 **상태가 아니라 유형**이다. `COMPLETED` 같은 값을
> 넣으면 제약에 걸린다.

### 1.3 알려진 스키마 문제

`uk_member_identity (site_id, member_name, di_hash, parent_di_hash)` 는 **한 번도 작동한
적이 없다.** `member_name` 이 AES-GCM 암호문이라 같은 이름도 매번 다른 값이 되고, 따라서
튜플이 항상 유일하다. 중복가입 차단은 이 키가 아니라 서비스의
`memberMapper.countByDiHash()` 가 한다. DDL 주석의 `member_name ... (평문)` 표기도 실제와
다르다. **이 키를 근거로 삼는 코드를 새로 쓰지 말 것.**

---

## 2. 개인정보 취급 (PII)

회원 도메인에서 가장 자주 실수가 나는 영역이다. 세 겹으로 되어 있고 **셋 다 갖춰야
동작한다.**

### 2.1 세 겹의 역할

| 요소 | 파일 | 역할 |
|---|---|---|
| `@Encrypt` | `common/crypto/Encrypt.java` | **문서용 표시** — "이 필드는 PII 다" |
| `PiiTypeHandler` | `common/crypto/PiiTypeHandler.java` | **실제 암복호화** (AES-256-GCM) |
| `PiiHash` | `common/crypto/PiiHash.java` | 검색용 HMAC-SHA256 (**AES 와 다른 키**) |

**MyBatis 는 `@Encrypt` 를 보지 않는다.** 매퍼 XML 이 컬럼마다 `typeHandler` 를 명시해야
비로소 암복호화가 걸린다.

```xml
<result property="email" column="email"
        typeHandler="com.gonet.common.crypto.PiiTypeHandler"/>
```

**빠뜨리면 평문으로 저장되거나 화면에 `{AG}…` 암호문이 그대로 뜬다.** 실제로
`vw_user_login.display_name` 이 `resultType` 자동 매핑이라 암호문을 화면에 노출한 적이
있다(P10-1). 새 조회를 만들 때 `resultType` 대신 `resultMap` 을 쓰는 이유가 이것이다.

### 2.2 암호화 대상 컬럼 (`tb_member`)

**암호화**: `email` `phone` `birth_date` `di` `parent_di` `address` `address_detail`
병행 해시 컬럼: `email_hash` `phone_hash` `di_hash` `parent_di_hash`

**평문**: `login_id` `nickname` `member_name` `parent_name` `birth_year` `gender`
`address_zipcode`

개인정보인 것과 암호화 대상인 것은 다르다 — 판단 근거는
[conventions.md §6.1](conventions.md#61-무엇을-암호화하고-무엇을-하지-않는가-2026-07-30-확정).

#### 이름은 평문이다 (2026-07-30 사용자 확정)

`member_name` · `parent_name` 은 **평문으로 저장한다.** 이름은 개인정보지만 저장 암호화
의무 대상이 아니고(고유식별정보·비밀번호·생체정보만 의무), **이름 검색이 실무에 필수**다.

전에는 `@Encrypt` + `PiiTypeHandler` 가 붙어 있었는데, V6 DDL 의 컬럼 주석은 처음부터
`'회원 이름 (평문)'` 이었다 — 코드 쪽이 어긋나 있었다. 그리고 그 어긋남은 **기능 하나를
조용히 죽이고 있었다**:

```
UNIQUE KEY uk_member_identity (site_id, member_name, di_hash, parent_di_hash)
```

AES-GCM 은 건당 난수 IV 를 쓴다 — 같은 이름도 매번 다른 암호문이 되므로 이 UNIQUE 는
**중복을 하나도 잡지 못했다.** 중복가입 차단 장치가 있는 척만 하고 있었다.
평문으로 되돌린 뒤에는 작동한다(실측: 2건째 INSERT 가 `ERROR 1062` 로 거부되고
중복키 값에 평문 이름이 들어간다).

> **아직 성인 회원은 막지 못한다.** MariaDB UNIQUE 는 NULL 을 서로 다른 값으로 보므로
> 인덱스 컬럼 하나라도 NULL 이면 충돌하지 않는다. `parent_di_hash` 는 14세 미만에만
> 채워지므로 **성인은 항상 NULL** 이고, 같은 `di_hash` 로 몇 번이든 가입된다(실측).
> 암호화 해제로 제약이 살아난 범위는 **아동 회원뿐**이다. 성인까지 막으려면
> `(site_id, di_hash)` 별도 UNIQUE 가 필요하다 — 스키마 결정 사항이라 손대지 않았다.

암호화를 뺐다고 보호를 놓는 것은 아니다. 마스킹([§2.4](#24-마스킹))·접근 이력
([§2.5](#25-접근-이력))·탈퇴 시 파기([§7.2](#72-탈퇴))는 그대로 적용된다.
탈퇴 원장에 남는 이름은 **마스킹된 값**이다([§7.2a](#72a-원장에-남는-이름은-마스킹된-값이다-v11)).

> `{AG}` 암호문은 SQL 로 복호화할 수 없다. 평문 전환 전에 쌓인 값이 있는지는
> `SELECT COUNT(*) FROM tb_member WHERE member_name LIKE '{AG}%'` 로 확인한다(0이어야 한다).
> 전환 시점의 dev DB 는 0건이었다.

### 2.3 검색이 제약된다

암호문에는 `=` 도 `LIKE` 도 걸 수 없다. 그래서:

- **부분 검색**은 평문 컬럼(`login_id` · `nickname` · **`member_name`**)에 가능하다
- **이메일·전화**는 해시 정확 일치뿐 — 전체 주소를 알아야 찾을 수 있다
- 해시는 **서비스가 만들어 넣는다**. 정규화 규칙(소문자·trim·숫자만)이 정책이라
  SQL 이 정할 수 없고, 가입 때와 같은 규칙이어야 값이 맞는다

이 비대칭(이름은 부분일치 / 이메일은 정확일치)은 실수가 아니라 암호화 정책의 결과다.
관리자 화면 설계가 여기서 나온다([§8.2](#82-검색)).

### 2.4 마스킹

`common/util/Mask.java` — 관리자 화면·CSV 가 **모두 이 함수를 지난다**.

| 메서드 | 예 |
|---|---|
| `name` | 홍길동 → `홍*동` / 김철 → `김*` / 박 → `박` |
| `email` | hongildong@ex.kr → `ho********@ex.kr` |
| `phone` | 01012345678 → `010-****-5678` |
| `birthDate` | 19900101 → `1990-**-**` |
| `address` | 앞 10글자 + `…` |
| `token`(DI) | `(설정됨)` / `-` |

**뷰에서 마스킹하지 말 것.** CSV·JSON 처럼 뷰를 안 타는 출구가 생길 때마다 빠뜨린다.
한 글자 이름처럼 **가릴 자리가 없어 원본이 그대로 나가는** 경우는 `MaskTest` 가 고정한다.

### 2.5 접근 이력

`logging/privacy/PrivacyAccessLogger` — `log_privacy_access` 에 적재(개인정보보호법 §29).

| 메서드 | action | 언제 |
|---|---|---|
| `search` | `SEARCH` | 목록 조회 (대상 여럿 → `target_id` 없음) |
| `read` | `READ` | 상세 조회 |
| `decrypt` | `DECRYPT` | 마스킹 해제 — **사유 필수** |
| `export` | `EXPORT` | CSV 내려받기 — **사유 필수** |
| `update` / `delete` | `UPDATE` / `DELETE` | 상태변경·초기화 / 강제탈퇴 |
| `denied` | (인자) + `result=DENIED` | 사유 미기재·권한 미달로 막은 시도 |

**거부도 남긴다.** 성공만 기록하면 "사유 없이 반복해서 내려받기를 시도하는" 패턴이
이력에 아예 나타나지 않는다.

`REQUIRES_NEW` 로 격리한다 — 대상 업무가 롤백돼도 "보려고 시도했다" 는 사실은 남아야 한다.
적재 실패는 삼킨다(부가 기록 때문에 관리 업무가 멈추면 손해가 더 크다).

### 2.6 파기 이력

`logging/purge/PiiPurgeLogService` — `log_pii_purge`(insert 전용).

- 회원 ID 는 **해시만**. 평문을 적으면 파기 이력 자체가 "이 사람이 회원이었다" 는
  개인정보가 된다
- **이력을 먼저, 파기를 나중에.** 크로스 DB 라 한 트랜잭션으로 묶을 수 없으므로
  순서가 유일한 안전장치다. 뒤에 남기면 "흔적 없는 삭제" 가 생긴다
- `table_list` 에 실제로 손댄 테이블을 적는다 — 파기 **범위**의 증빙

### 2.7 삼킨 실패는 `log_error` 로 끌어올린다

부가 기록(접근·개인정보취급·파기·로그인·다운로드 이력)의 적재 실패는 **삼킨다.**
로그 하나 때문에 사용자 업무가 멈추면 손해가 더 크기 때문이다 — 실제로 파기 이력 적재
실패가 **회원 탈퇴 자체를 롤백**시키고 있었다(코드 리뷰 2026-07-30).

그런데 삼킨 실패가 서버 파일 로그에만 남으면 아무도 보지 않는다. 그래서 창구를 하나
둔다: `logging/error/ErrorLogger`.

```
부가 기록 insert 실패
  → log.warn/error (파일)
  → errorLogger.logRecordFailure(channel, detail, e)
      → log_error INSERT (REQUIRES_NEW, logging_db)
      → 그마저 실패하면 파일 로그만 (재귀 금지)
  → 본 업무는 그대로 계속
```

| 채널 | 실패한 기록 | 부르는 자리 |
|---|---|---|
| `PII_PURGE_LOG` | `log_pii_purge` | `PiiPurgeLogServiceImpl` |
| `PRIVACY_ACCESS_LOG` | `log_privacy_access` | `PrivacyAccessLogger` |
| `LOGIN_HISTORY` | `tb_login_history` | `LoginHistoryRecorder` |
| `FILE_DOWNLOAD_LOG` | `log_file_download` | `FileDownloadLogger` |
| `ACCESS_LOG` | `log_access` | `AccessLogFilter` — **스로틀 60초**(요청마다 불리는 자리라 장애 한 번에 표가 넘친다). 억제 건수는 다음 행 상세에 함께 적힌다 |
| `AUDIT_LOG` | `log_audit` | `AuditTrailRecorder` — 관리자 CUD 전수 감사([§2.8](#28-감사-로그--관리자-cud-전수)) |

`error_class` 는 `RECORD_FAILURE:{채널}` 형태로 저장돼 분류 필터가 그대로 먹는다.
관리자 확인은 [`/adm/error-log`](#106-에러-로그-확인) — 목록·상세(스택트레이스)·분류별 집계.

> **삼키면 안 되는 것.** 탈퇴 원장(`tb_member_withdraw`)과 동의 이력
> (`tb_member_consent`) 적재 실패는 **반드시 전파**해 트랜잭션을 롤백시킨다.
> 근거 없이 PII 를 파기하거나 동의 없이 가입을 성립시키면 안 된다.
> 삼킬 대상은 "있으면 좋은 기록" 이고, 없으면 안 되는 기록은 실패가 곧 중단이다.

### 2.8 감사 로그 — 관리자 CUD 전수

`log_audit` — **관리자 화면에서 상태를 바꾼 요청 전부**. 2026-07-30 사용자 요청으로
도입했다(그 전까지는 스키마만 있고 적재 코드가 없어 0건이었다).

| | |
|---|---|
| 적재 지점 | `AuditTrailInterceptor`(`HandlerInterceptor`) → `AuditTrailRecorder` |
| 대상 | `/adm/**` 의 **비-GET** 요청. `/adm/login`·`/adm/logout` 제외 |
| 화면 | `/adm/audit-log` 목록·상세 (읽기 전용) |
| 보존 | 36개월 — `LogRetentionWorker` 가 이미 대상에 넣고 있었다 |

**왜 도메인 서비스가 아니라 인터셉터인가.** 테이블 주석이 "전수" 를 요구하는데,
서비스마다 호출을 심으면 새 관리자 화면을 만들 때 한 줄만 빠뜨려도 그 화면이 조용히
감사에서 제외된다. `/adm/**` 패턴 하나로 걸면 **새 화면은 등록 없이 자동으로 걸린다** —
전수를 코드 규율이 아니라 구조로 보장한다.

**왜 필터가 아니라 인터셉터인가.** 감사에는 행위자가 필요하다. `AccessLogFilter` 처럼
시큐리티 체인 **바깥**에서 돌면 그 시점에 `SecurityContextHolder` 가 이미 비어 있다.
인터셉터는 DispatcherServlet 안, 즉 체인 **안쪽**이라 주체를 직접 읽는다.

#### 값 해석 규칙 (URL 에서 기계적으로)

```
/adm/board/save                             → BOARD               CREATE 또는 UPDATE
/adm/url-access/evict                       → URL_ACCESS          EVICT
/adm/board/category/save                    → BOARD_CATEGORY      CREATE
/adm/board/BBM_…/article/save               → BOARD_ARTICLE       SAVE
/adm/board/BBM_…/article/comment/moderate   → BOARD_ARTICLE_COMMENT  MODERATE
/adm/member/MBR_…/status                    → MEMBER              STATUS
/adm/member/batch/notice                    → MEMBER              BATCH_NOTICE
/adm/password                               → PASSWORD            UPDATE
```

- `target_id` 는 **엔티티가 정한 파라미터를 먼저** 본다(`BOARD`→`bbsMasterId`).
  없으면 경로의 ID 마디. 둘 다 없으면 NULL 이고, 그 NULL 이 CREATE/UPDATE 를 가른다
- `siteId`·`fileGroupId` 는 **대상이 아니라 상위 참조**라 후보에서 뺐다 —
  처음에 "알려진 ID 목록" 을 훑었더니 게시판 신규 등록이 `siteId` 를 집어
  *사이트를 고친 것처럼* 기록되고 CREATE 도 UPDATE 가 됐다(실측)
- 게시글은 **PK 선발급** 도메인이라 요청만으로 신규/수정을 가릴 수 없다
  ([§게시글 생명주기 §1](게시글%20생명주기.md)) → 모른다고 적는다: `SAVE`

#### 결과 판정 — 2xx 가 실패 신호다

관리자 쓰기 핸들러는 **성공하면 반드시 리다이렉트**한다(PRG). 값 검증에 걸리면
`model.addAttribute("flashError", …)` 후 **폼 뷰를 다시 그려 200** 을 낸다.
그 오류는 Model 에 있고 플래시 맵에는 없어서 플래시로는 잡히지 않는다 — 그래서
"쓰기 요청 + 2xx" 를 FAIL 로 본다. 이 규칙은 인터셉터가 `/adm/**` 에만 걸려
200 이 곧 성공인 자리가 없어서 성립한다. `/api/**` 로 확장하려면 먼저 고쳐야 한다.

#### 알려진 한계 (전부 의도된 선택)

| 한계 | 이유 |
|---|---|
| `before_json`·`after_json` 이 항상 NULL | 인터셉터는 요청이 끝난 뒤에 돌아 "전" 을 볼 수 없다. 서비스가 넘기게 하면 전수 보장이 깨진다 |
| 인가·CSRF 로 막힌 요청은 미기록 | 시큐리티 필터가 DispatcherServlet 앞에서 끊는다. 그 시도는 `log_access` 의 403 으로 남는다 |
| `flashMessage` 로 실패를 알리는 화면은 SUCCESS | 그 키는 성공·실패에 모두 쓰여 신호로 못 쓴다(파일 관리 화면) |
| 사용자 화면 쓰기(게시글·댓글)는 미기록 | 도메인 데이터가 감사컬럼 6종을 이미 갖고 있어 중복이다 |
| 관리자 로그인·로그아웃 미기록 | `tb_login_history` 가 성공·실패·사유·잠금까지 담당한다 |

---

## 3. 가입

### 3.1 5단계 마법사

| STEP | URL | 화면 |
|---|---|---|
| 1 유형 선택 | `GET /{sc}/member/join` | `member/join/type.html` |
| 2 약관 동의 | `GET·POST /{sc}/member/join/terms` | `member/join/terms.html` |
| 3 본인인증 | `GET /{sc}/member/join/verify` | `member/join/verify.html` |
| 4 정보 입력 | `GET·POST /{sc}/member/join/form` | `member/join/form.html` |
| 5 완료 | `GET /{sc}/member/join/complete` | `member/join/complete.html` |

단계 표시는 `fragments/member/join-steps.html`. 본인인증을 끄면 STEP 3 칸을 **그리지
않는다** — 지나갈 수 없는 단계를 남겨 두면 "왜 안 넘어가지" 를 사용자가 되묻는다.

### 3.2 상태는 전부 세션에 있다

`dto/JoinSession.java` (세션 키 `GOPCMS_JOIN_SESSION`)

유형·동의·본인인증 결과를 hidden 으로 넘기면 **사용자가 값을 바꿔 인증을 건너뛸 수
있다.** 각 단계는 진입할 때 앞 단계 완료를 확인하고, 아니면 되돌린다.

`siteId` 를 함께 들고 다닌다. 마법사 도중 다른 사이트로 이동하면 동의·인증이 엉뚱한
사이트에 적용되므로, 매 단계에서 현재 사이트와 대조해 어긋나면 버린다.

### 3.3 유형 — ADULT / CHILD

| | 인증 주체 | 저장 위치 | 이름·생년월일 |
|---|---|---|---|
| `ADULT` | 본인 | `di` / `di_hash` | 인증기관 값(읽기 전용) |
| `CHILD`(14세 미만) | **법정대리인** | `parent_name` / `parent_di` / `parent_di_hash` | 사용자 입력 |

CHILD 는 DI 중복 검사를 **하지 않는다.** 한 법정대리인이 자녀 여럿을 가입시키므로
`parent_di` 중복이 정상이다.

### 3.4 폼 값보다 인증 값이 이긴다

`MemberJoinServiceImpl.applyVerifiedIdentity()` 가 저장 직전에 **폼 값을 덮어쓴다.**
화면이 읽기 전용으로 보여 주더라도 전송 값은 얼마든지 바뀐다 — `readonly` 는 안내일 뿐
방어가 아니다.

### 3.5 역할 부여

| 상황 | `role_ids` |
|---|---|
| 실명확인 완료 | `ROLE_MEMBER` + `ROLE_REAL` |
| 미인증 | `ROLE_MEMBER` 만 |

설정: `gopcms.member.default-role-id` · `gopcms.member.real-role-id`

**`ROLE_REAL` 은 "실명확인을 거쳤다" 는 사실 표시다**(2026-07-30 사용자 확정).
기능 권한의 기준이 아니다 — 파일 업로드는 `ROLE_MEMBER` 를 본다
([file-domain.md §4.1b](file-domain.md#41b-업로드-권한--role_member)).

CHILD 도 이 표시를 받는다. 법정대리인이 실명확인을 했고, 그것이 14세 미만에게 법이 요구하는
확인이기 때문이다.

> **주의**: `role_ids` 에 ROLE_REAL 을 넣어도 Security 권한 목록에는 나타나지 않는다 —
> `vw_user_login` 이 회원의 `role_codes` 를 `'ROLE_MEMBER'` 리터럴로 고정한다.
> URL 접근 규칙(`role_ids` 기반)에는 반영되고 `hasRole()`(`role_codes` 기반)에는
> 반영되지 않는, 두 축이 갈리는 지점이다.

### 3.6 동의 이력

필수 2종 + 선택 3종을 **모두** 남긴다. 선택 항목을 `N` 으로 남기는 것도 기록이다 —
"동의하지 않았다" 를 나중에 증명하려면 행이 있어야 한다.
버전(`gopcms.member.consent-version`)·IP·UA 동반(UA 는 500자 절단 — 넘치면 저장이 통째로
실패한다).

### 3.7 중복 차단

| 대상 | 방법 |
|---|---|
| 아이디 | `countByLoginId(siteId, loginId)` — 사이트 스코프 |
| 이메일 | `countByEmailHash` — 소문자·trim 정규화 후 해시 |
| 본인 | `countByDiHash` — 아이디·이메일은 새로 만들 수 있어 **DI 로만** 걸린다 |

서비스 검사는 안내 문구용이고 **최종 방어선은 DB UNIQUE 제약**이다(동시 요청은 둘 다
통과할 수 있다).

---

## 4. 본인인증 (NICE CheckPlus)

### 4.1 구성

```
primary/identity/
  config/NiceCheckProperties   gopcms.nice.*
  service/NiceCheckService     generateRequestNo / encode / decode
  controller/NiceCheckUsrController
  dto/NiceCheckResult          DI 포함 — CI 는 아예 읽지 않는다
```

`lib/NiceID_v1.1.jar` 는 JPMS 플래그를 요구한다
(`--add-exports/--add-opens java.base/com.sun.crypto.provider=ALL-UNNAMED`).
surefire·spring-boot:run·운영 Tomcat setenv 세 곳 모두 필요하며 pom.xml 에 반영돼 있다.

### 4.2 경로에 siteCode 가 없는 이유

콜백 URL 은 NICE 콘솔에 **문자열 그대로** 등록되고 그 URL 로만 응답이 돌아온다.
사이트마다 경로가 갈리면 사이트 수만큼 계약이 필요하다. 그래서 `/login` 과 같은
사이트 무관 엔드포인트이고, 사이트는 `siteCode` 파라미터와 세션으로 잇는다.

### 4.3 팝업 흐름

```
부모창 [data-nice-open] 클릭
   └▶ 팝업: GET /member/identity/nice        우리 페이지 → NICE 로 자동 POST
        └▶ NICE 인증
             └▶ 팝업: /nice/success | /nice/fail   (우리 origin)
                  └▶ 부모창에 신호 3중 → 팝업 자동 닫기
```

신호가 3중인 이유: COOP(`same-origin`) 아래서 팝업이 NICE 도메인을 한 번 거치면
`window.opener` 가 끊긴다. `localStorage` storage 이벤트와 `BroadcastChannel` 은
opener 관계와 무관하게 same-origin 두 창을 잇는다. 셋 중 무엇이 먼저 와도 **한 번만**
처리한다. 구현은 `static/js/nice-auth.js` — 인라인 스크립트 금지 규약을 지킨다.

### 4.4 반드시 함께 있어야 하는 것

| 항목 | 위치 | 없으면 |
|---|---|---|
| CSP `form-action` 확장 | `gopcms.security.csp-form-action-extra` | **폼 전송이 조용히 차단**된다(콘솔에만 위반) |
| CSRF 예외 2경로 | `SecurityConfig` | 외부 POST 콜백이 403 |
| URL 접근 규칙 | V919 (priority 52) | 무매칭 DENY |

### 4.5 위조 방어

세션의 요청번호(`REQ_SEQ`)와 콜백 응답의 `REQ_SEQ` 를 대조한다. 콜백 URL 은 공개돼 있어
이 대조가 없으면 아무 `EncodeData` 나 밀어 넣을 수 있다. 실패한 요청번호는 즉시 버린다.

### 4.6 자격이 없을 때

`site-code`/`site-password` 가 비면 **기동은 되고** 인증 화면만 안내를 띄운다 —
계약 전에도 나머지 기능이 떠야 하기 때문이다.

`gopcms.nice.enabled=false` 면 가입 STEP 3 이 통째로 빠진다(계약 전 개발용).
**운영에서 끄면 실명 확인 없이 가입이 열린다.**

---

## 5. 소셜 로그인 (OAuth2)

### 5.1 스타터를 쓰지 않는다

`spring-security-oauth2-client` 대신 `RestClient` 로 authorization_code 흐름을 직접
처리한다. 인가가 DB 단일 원천(`tb_role_url_access`)이라, 자체 필터 체인을 끼워 넣는
스타터보다 authorization_code 교환만 하는 편이 경계가 분명하다.

### 5.2 흐름

```
GET /member/oauth2/{provider}?siteCode=x
   state 발급(세션) + siteCode 세션 보관 → provider 인가 URL 로 302
      └▶ GET /member/oauth2/{provider}/callback
           state 1회 소비 → 토큰 교환 → userinfo → ExternalProfile
              ├ 연결 있음 → 즉시 로그인 → /{siteCode}/index
              └ 연결 없음 → 가입 마법사 STEP 1 로
```

**`state` 를 1회 소비하지 않으면** 공격자가 만든 인가 코드를 피해자 브라우저에 흘려
남의 소셜 계정을 피해자 계정에 붙일 수 있다.

### 5.3 신규 사용자를 바로 만들지 않는다

약관 동의와 본인인증은 소셜로 들어왔다고 건너뛸 수 있는 절차가 아니다. 외부 프로필을
`JoinSession` 에 심고 마법사로 보낸다. 연결(`tb_member_oauth`)은 **가입 트랜잭션 안에서**
만든다 — 부분 성공하면 "소셜로 가입했는데 소셜 로그인이 안 되는" 상태가 되고, 다시
시도하면 아이디 중복으로 막힌다.

### 5.4 provider 추가 절차

1. `OAuth2Provider` enum 에 항목 + 엔드포인트 추가
2. `OAuth2Properties` 에 자격 필드 추가
3. `OAuth2ServiceImpl.parseProfile()` 에 응답 파싱 분기 추가 (**구조가 제각각이다**)
4. `tb_member_oauth.provider` / `tb_member.join_type` CHECK 제약을 마이그레이션으로 확장

enum 이름이 곧 `provider` 값이자 `join_type` 값이다 — 변환표를 두지 않는다(표가 생기면
언젠가 한쪽만 고쳐진다).

### 5.5 provider 별 함정

| provider | 주의 |
|---|---|
| NAVER | 식별자·이름·이메일이 `response` **하위**에 있다 |
| KAKAO | `id` 가 숫자로 온다(문자열 변환 필수) · **실명을 주지 않아** 닉네임이 이름 자리를 대신한다 · secret 없이 쓰는 설정이 있어 자격 판정에서 예외 |
| GOOGLE | 식별자는 `sub`(email 아님) · scope 의 공백은 percent-encoding 필수 |

자격이 없는 provider 는 로그인 화면에 **버튼이 아예 나오지 않는다**
(`OAuth2Service.configuredProviders()`).

---

## 6. 로그인 · 마이페이지

### 6.1 로그인

`/login?siteCode=` — `MemberAuthenticationProvider`. 사이트 스코프 `(site_id, login_id)`
조회는 `uk_member_login` 과 1:1.

| 방어 | 내용 |
|---|---|
| 일반 실패 문구 | 실패 사유는 **이력에만** 남긴다 |
| 타이밍 균일화 | `LoginTiming.burn()` — 응답 속도로 계정 존재가 새지 않게 |
| 계정 잠금 | 5회 실패 → 30분. **비밀번호가 틀렸을 때만** 오른다 |
| CAPTCHA | 잠금 이력이 있는 계정(`captcha_required_yn`)에 강제 |
| 레이트리밋 | `LoginRateLimiter` — IP·아이디 두 축, **시도 자체**를 센다 |
| 세션 고정 방어 | `changeSessionId()` |
| 동시 세션 | `maximumSessions(1)` |

### 6.2 재인증 (Step-up)

`service/StepUpAuth` — 마이페이지 진입 시 비밀번호 재확인. TTL 5분, 실패 5회.
세션 속성 `GOPCMS_STEPUP_AT` / `GOPCMS_STEPUP_FAIL`.

### 6.3 마이페이지

| URL | 기능 |
|---|---|
| `GET /{sc}/member/mypage` | 프로필 조회 (미인증 시 `/verify` 로) |
| `GET·POST /{sc}/member/mypage/verify` | 재인증 |
| `POST /{sc}/member/mypage` | 프로필 수정 |
| `GET·POST /{sc}/member/mypage/password` | 비밀번호 변경 |
| `GET·POST /{sc}/member/mypage/withdraw` | **셀프 탈퇴** |

셀프 탈퇴는 확인 문구 입력을 요구하고, 처리는 `MemberLifecycleService.withdraw(…,
"USER_REQUEST")` 를 탄다([§7.1](#71-탈퇴-단일-경로)).

### 6.4 아이디 · 비밀번호 찾기

| | 동작 |
|---|---|
| 아이디 찾기 | 이름+이메일 일치 시 **마스킹된 아이디** 반환 |
| 비밀번호 찾기 | 임시 비밀번호를 **메일로만** 발송 |

임시 비밀번호는 **만료 시각을 과거로** 둔다. 로그인은 되지만 인증 Provider 가 만료로
막아 변경 화면으로 보낸다 — 임시 비밀번호를 계속 쓰는 상태를 만들지 않는 장치다.

결과 문구는 **성공·실패가 같다.** 다르면 계정 존재 확인 도구가 된다.

---

## 7. 생명주기 (휴면 · 탈퇴 · 파기)

### 7.1 탈퇴 단일 경로

```
셀프 탈퇴 ┐
관리자 강제 ├─▶ MemberLifecycleService.withdraw(memberId, reason, withdrawType)
휴면 만료 ┘
```

경로가 둘이면 원장 적재 순서나 PII 파기 범위가 갈린다. **새 탈퇴 진입점을 만들지 말고
이 메서드를 부를 것.**

처리 순서 — **바꾸지 말 것**:

1. **두 원본을 먼저 읽는다** (`findNameSource` + `findDormantNameSource`) — 이름을 마스킹해
   둘 곳이 여기뿐이다. 파기 뒤에는 만들 수 없다
2. **원장 INSERT** (`tb_member_withdraw`) — PII 삭제는 되돌릴 수 없으므로 근거를 먼저 남긴다
3. **파기 이력** (`log_pii_purge`) — 크로스 DB 라 순서가 유일한 안전장치
4. **PII 파기** — `nullifyPii` **와** `nullifyDormantPii` 를 **둘 다** 부른다(아래)
5. **소셜 연결 삭제** (`tb_member_oauth` — **행째로**, [§7.2b](#72b-소셜-연결은-행째로-지운다))
6. **작성자 익명화** (게시글·댓글)

#### NULL 로 비우지 않는 컬럼 셋

| 컬럼 | 파기 후 값 | 이유 |
|---|---|---|
| `login_id` | 원본 유지 | 탈퇴한 아이디의 재사용을 막는 유일한 근거 |
| `password` | `'-'` | NOT NULL. BCrypt 형식이 아니라 어떤 입력과도 맞지 않는다 |
| `member_name` | **마스킹 값**(`홍*동`) | NOT NULL(V12) + 사용자 확정 — [§7.2c](#72c-탈퇴-시-이름은-마스킹해서-남긴다-v12) |

#### 두 테이블을 모두 파기해야 한다 (2026-07-30 실측 결함)

`nullifyPii`(tb_member)와 `nullifyDormantPii`(tb_member_dormant)를 **둘 다** 부른다.
있는 쪽만 실제로 바뀌고 없으면 0건이다.

전에는 원장이 적재된 쪽만 파기했다. 그런데 `insertWithdrawLedger` 의 조회에는
`delete_yn` 필터가 없고, **휴면 전환은 복사 + soft-delete** 라 휴면 회원도 `tb_member`
행을 그대로 갖고 있다 — 그래서 원장은 **언제나** `tb_member` 에서 적재됐고,
`nullifyDormantPii` 는 사실상 한 번도 실행되지 않았다.

결과: 휴면 만료 자동 탈퇴가 **휴면 스냅샷의 이름·이메일·전화·주소를 통째로 남겨** 두고
있었다. 실측 로그에 `탈퇴 처리(휴면 경유)` 가 아니라 `탈퇴 처리` 만 찍히는 것이 단서였다.

### 7.2a 원장에 남는 이름은 마스킹된 값이다 (V11)

원장이 해시만 들고 있던 동안 관리자 화면의 탈퇴 목록은 **읽을 수 없는 표**였다.
어느 행이 누구인지 가늠할 단서가 없어 문의 응대마다 해시를 다시 계산해야 했다.
그래서 이름 한 칸을 두되 **마스킹해서** 넣는다 — 홍길동 → `홍*동`.

| | |
|---|---|
| 컬럼 | `tb_member_withdraw.member_name varchar(150)` |
| 값 | `Mask.name()` 결과. 빈 이름은 NULL(문자열 `'-'` 를 넣지 않는다) |
| 암호화 | **하지 않는다** — 이미 되돌릴 수 없는 값이다. `PiiTypeHandler` 를 붙이면 평문을 복호화하려 들어 깨진다 |
| 넣는 자리 | `MemberLifecycleServiceImpl.withdraw()` — 원장 INSERT 파라미터 |
| 구 데이터 | NULL. 원본이 이미 파기돼 채울 값이 없다 |

**왜 SQL 이 아니라 앱인가.** `tb_member.member_name` 의 저장값은 암호문(`{AG}…`)이다.
기존 `INSERT … SELECT` 로 컬럼을 그대로 복사하면 원장에 **암호문이 박히고**, 키가 있는
쪽에서는 그것이 곧 평문이다. 그래서 서비스가 복호화된 이름을 읽어(`findNameSource` /
`findDormantNameSource`) 마스킹한 뒤 파라미터로 넘긴다.

**순서 주의.** 반드시 `nullifyPii` **앞에서** 읽는다. 뒤에서 읽으면 이미 NULL 이라
영구히 채울 수 없다 — 원장 INSERT 가 1단계인 이유와 같다.

읽을 때 다시 마스킹하지 말 것. `홍*동` 을 `Mask.name()` 에 또 넣으면 `홍*동` → `홍*동`
으로 별이 늘어나 대조가 어긋난다. 화면(`adm/member/withdraw.html`)은 값을 그대로 쓴다.

### 7.2c 탈퇴 시 이름은 마스킹해서 남긴다 (V12)

`tb_member.member_name` 은 **NOT NULL** 이다 — 회원 테이블에 이름이 없는 행은 성립하지
않는다(사용자 확정 2026-07-30). 가입 경로가 이미 이름을 필수 검증하고 있었으므로
DDL 이 코드를 뒤늦게 따라간 변경이다.

그래서 파기 때 NULL 로 비울 수 없다. **마스킹한 값을 남긴다** — 홍길동 → `홍*동`.

```
tb_member.member_name          홍*동   ← 파기 후 (마스킹)
tb_member_dormant.member_name  홍*동   ← 같은 값
tb_member_withdraw.member_name 홍*동   ← 원장 (V11)
```

세 값이 같은 이유는 **한 번 계산해서 돌려쓰기** 때문이다. 두 번 계산하면 한쪽만 고쳐져
값이 갈린다. 원본 이름을 못 읽은 경우(이미 파기된 행을 다시 탈퇴시키는 등)에는 `'-'` 다 —
이름 하나 때문에 탈퇴가 실패하면 파기 자체가 막히기 때문이다.

**되마스킹하지 말 것.** `Mask.name("홍*동")` 은 `홍*동` 그대로라 다행히 멱등이지만,
설계상 이 값은 이미 최종형이다.

> `tb_member_dormant.member_name` 에는 NOT NULL 을 걸지 않았다. 값은 같은 규칙으로
> 채우지만 스냅샷은 원본이 아니라 사본이라 "이름 없는 행" 의 의미가 다르다 — 맞추려면
> 별도 결정이 필요하다. `parent_name` 도 NULL 허용이다: 법정대리인은 14세 미만에만
> 있으므로 NOT NULL 로 만들면 성인이 가입할 수 없다.

### 7.2b 소셜 연결은 행째로 지운다

탈퇴 사실은 원장이 해시로 보관하므로 `tb_member_oauth` 행이 이력을 대신할 이유가 없다.
그런데 남겨 두면 **재가입이 막힌다.**

```
UNIQUE KEY uk_oauth_provider_user (provider, provider_user_id, delete_yn)
```

표시만 내리는 방식(`use_yn='N'`)으로는 해결되지 않는다 — 조회에서는 빠지지만 UNIQUE 키에는
`delete_yn='N'` 행이 그대로 잡힌다. 같은 소셜 계정으로 다시 가입하면 연결 INSERT 가 중복
키로 실패하고, 그 INSERT 가 **가입 트랜잭션 안에 있어 가입 전체가 롤백**된다. 사용자는
원인 불명의 실패를 겪고 재시도해도 계속 막힌다.

> 코드 리뷰(2026-07-30)에서 잡힌 결함이다. 실측으로 양쪽을 대조했다 —
> `use_yn='N'` 만 내린 뒤 재연결하면 `Duplicate entry 'NAVER-…-N'`,
> 행을 지운 뒤에는 정상 INSERT.

`MemberOAuthService.unlink()` 도 같은 이유로 하드 삭제다. 정상 탈퇴는 그 경로를 타지 않고
(`withdraw()` 가 직접 지운다), `unlink()` 는 회원 행이 사라졌는데 연결만 남은 비정상 상태를
정리하는 방어 경로다.

### 7.2 작성자 익명화

탈퇴 시 `tb_bbs_article` · `tb_bbs_comment` 의 `writer_name` 을
`gopcms.member.anonymous-writer-name`(기본 `탈퇴한 회원`)으로 바꾼다.

- **글은 지우지 않는다.** 대화의 맥락이 통째로 사라지면 남은 사람들의 글이 읽히지 않는다.
  지워야 하는 것은 "누가 썼는지" 이지 "무엇을 썼는지" 가 아니다
- **`writer_user_id` 는 남긴다.** 회원 행이 사라진 뒤에는 그 값으로 사람을 되짚을 수 없어
  식별정보가 아니고, 같은 작성자의 글을 묶는 운영 기능이 거기 걸려 있다
- 탈퇴 트랜잭션 안에서 처리한다. 배치로 미루면 그 사이 실명이 노출된 채 남고, 배치가
  실패하면 영영 남는다

### 7.3 배치 4종 (`MemberLifecycleJob`)

| 순서 | 메서드 | 기본 cron | 하는 일 |
|---|---|---|---|
| ① | `sendDormantNotices` | `0 10 3 * * *` | 휴면 30/7/1일 전 안내 |
| ② | `transferToDormant` | `0 30 3 * * *` | ACTIVE → 휴면 |
| ③ | `transferToWithdraw` | `0 40 3 * * *` | 휴면 → 탈퇴(**PII 파기**) |
| ④ | `purgeWithdrawn` | `0 50 3 * * *` | 보존기한 경과 → 행 삭제 |

**안내(①)가 전환(②)보다 먼저 돌아야 한다.** 같은 날 함께 돌면 안내를 받은 그날 휴면이
되는 계정이 생긴다.

셋을 하나로 묶지 않은 이유: 위험도가 다르다. 휴면은 되돌릴 수 있고, 탈퇴는 PII 가
사라지며, 완전 삭제는 행 자체가 없어진다. 따로 켜고 끌 수 있어야 한다.

**대상이 0건이어도 로그를 남긴다.** 관리자 화면의 수동 실행은 로그가 유일한 확인
수단이라, 아무것도 안 찍히면 "돌았는데 0건" 과 "안 돌았다" 를 구분할 수 없다.

### 7.4 안전장치

- **`dry-run` 기본 켜짐**(`gopcms.member.lifecycle.dry-run`) — 대상만 로그에 남는다.
  배치를 처음 켜는 순간 오래된 계정이 한꺼번에 사라지는 것이 가장 흔한 사고다
- **1회 상한**(`batch-size`, 기본 200) — 잘못 돌아도 피해가 한 배치로 제한된다
- **단건 독립 트랜잭션**(`MemberLifecycleWorker`, `REQUIRES_NEW`) — 한 건이 실패해도
  나머지는 진행한다. **워커가 별도 빈인 이유는 자기호출이면 `@Transactional` 이
  무시되기 때문**이다
- ShedLock — 다중 인스턴스에서 중복 실행 방지

### 7.5 휴면 복원 — 수단 둘, 처리 하나

| 수단 | 확인 방법 | 진입 |
|---|---|---|
| 이메일 인증번호 | 아이디+비밀번호 → 메일 6자리 | `POST /dormant/check` → `/verify` |
| **실명인증(NICE)** | DI 대조 | `data-nice-open` 팝업 → `/dormant` 복귀 |

어느 수단이든 `restore()` 하나를 탄다. 실명인증 경로는 **아이디·비밀번호를 묻지 않는다** —
비밀번호를 잊어 못 들어오는 것이 휴면의 흔한 사정이라, 그걸 요구하면 복원 수단이 하나 더
필요해진다. 실명인증 없이 가입한 계정(`di_hash` 없음)은 OTP 경로를 쓴다.

**인증번호 규칙**

- 코드는 **평문 저장하지 않는다**(HMAC). 대조도 해시끼리
- 시도 횟수는 **행에** 둔다. 세션에 두면 세션을 새로 잡아 무제한 대입할 수 있다
- 이전 코드는 즉시 만료 — 살아 있는 코드가 둘이면 시도 제한이 무의미해진다
- 실패 사유를 구분해 알려 주지 않는다(만료인지 오답인지 알려 주면 대입에 도움이 된다)
- 아이디만으로 "휴면입니다" 를 알려 주지 않는다 — 계정 존재 확인 도구가 된다

**발송 제한 두 축** (`service/OtpRateLimiter`)

| 축 | 설정 | 막는 것 |
|---|---|---|
| 계정 쿨다운 | `otp.resend-cooldown-seconds` (60) | 한 계정에 연달아 |
| **IP** | `otp.rate.per-ip` (10 / 60분) | 아이디를 바꿔 가며 |

쿨다운만으로는 아이디만 바꾸면 **한 번도 걸리지 않으면서 대량 발송**이 가능하다.
휴면 복원과 비밀번호 찾기 두 경로에 함께 건다(관리자 발급은 제외 — 인증을 통과한
운영자의 업무이고 여기서 막으면 정작 필요할 때 못 쓴다).

---

## 8. 관리자 회원 관리 (`/adm/member`)

### 8.1 화면

| URL | 화면 |
|---|---|
| `GET /adm/member` | 목록 (마스킹 기본) |
| `GET /adm/member/{id}` | 상세 |
| `GET /adm/member/{id}?reason=…` | **마스킹 해제** |
| `POST /adm/member/{id}/status` | 상태 변경 |
| `POST /adm/member/{id}/unlock` | 잠금 해제 |
| `POST /adm/member/{id}/password` | 임시 비밀번호 발급 |
| `POST /adm/member/{id}/withdraw` | 강제 탈퇴 |
| `GET /adm/member/dormant` | 휴면 현황 + 배치 수동 실행 |
| `GET /adm/member/withdraw` | 탈퇴 원장 |
| `GET /adm/member/export` | CSV 내려받기 |

**등록 기능이 없다.** 가입은 본인 동의와 본인확인을 거쳐야 성립하는데 관리자가 대신
만들면 그 둘이 없는 계정이 생긴다(정책). `MemberAdmService` 에 `create` 가 없는 것은
빠뜨린 것이 아니다.

> `{memberId}` 경로에 `MBR_` 접두어 제약이 걸려 있다
> (`@PostMapping("/{memberId:MBR_.+}/withdraw")`). 없으면
> `/adm/member/batch/withdraw`(배치)가 `/{memberId}/withdraw`(강제 탈퇴)에도 매칭돼
> 배치가 400 을 뱉는다 — 실측으로 잡은 충돌이다.

### 8.2 검색

암호화 정책이 화면을 규정한다([§2.3](#23-검색이-제약된다)).

| 조건 | 방식 |
|---|---|
| `keyword` | `login_id` · `nickname` · **`member_name`** 부분일치 |
| `email` / `phone` | 해시 **정확 일치** — 전체 값 필요 |
| `siteId` `status` `joinType` | 동등 비교 |
| `lockedOnly` `unverifiedOnly` | 플래그 |

이름이 `keyword` 에 들어간 것은 평문 저장으로 되돌린 결과다([§2.2](#22-암호화-대상-컬럼-tb_member)).
휴면 현황(`/adm/member/dormant`)의 검색도 같이 `login_id` + `member_name` 이 된다.
검색 결과 조회는 `ACTION_SEARCH` 로 접근 이력에 남는다 — 이름으로 훑는 것도 열람이다.

`MemberAdmSearch` 는 **표시값과 해시를 분리**해서 갖는다(`email` vs `emailHash`).
입력 자리에 해시를 덮어쓰면 검색창에 64자 16진수가 되돌아와 관리자가 방금 친 값을 잃는다.

### 8.3 마스킹 해제 · 내려받기

| | 요구 |
|---|---|
| 마스킹 해제 | 사유 5자 이상 + **`ROLE_PRIVACY`** |
| CSV 내려받기 | 사유 5자 이상 + `ROLE_PRIVACY`(V920) + 건수 상한 + **마스킹 유지** |

`ROLE_PRIVACY` 는 `ADMIN>MANAGER>…` 계층 **밖**의 독립 역할이라 ROLE_ADMIN 이어도
자동 상속되지 않는다(V907). "관리자니까 다 볼 수 있다" 를 막는 것이 이 역할의 존재 이유다.

**마스킹 해제는 URL 규칙으로 가를 수 없다** — 상세와 같은 주소(`{id}?reason=`)이기
때문이다. 그래서 `MemberAdmController.hasPrivacyRole()` 이 직접 판정한다.

**DI 는 해제해도 표시하지 않는다.** 전 기관 공통 식별자라 화면에 띄울 업무상 이유가 없다.

CSV 는 세 가지가 함께 있어야 의미가 있다 — 사유만 받고 마스킹을 풀면 사유가 형식이 되고,
마스킹만 하고 상한이 없으면 전 회원 목록이 한 번에 나간다. 상한(`gopcms.member.adm.
export-limit`, 기본 5000)에 걸리면 **파일 끝에 잘렸다고 적는다**(안 적으면 전부라고 믿는다).
CSV 인젝션 방어로 `=` `+` `-` `@` 로 시작하는 값은 따옴표로 고정한다.

### 8.4 임시 비밀번호

**관리자 화면에 값을 표시하지 않는다.** 회원 메일로만 간다 — 관리자에게 값을 보여 주면
관리자가 그 계정으로 로그인할 수 있고, 계정을 되찾아 주는 것과 가져가는 것은 다른 일이다.

발급 자체는 본인 요청과 **같은 경로**(`MemberPasswordResetServiceImpl.issue()`)를 탄다.

### 8.5 배치 수동 실행

`POST /adm/member/batch/{notice|dormant|withdraw|purge}` — 스케줄이 멈췄거나 시각을
놓쳤을 때 쓰는 운영 복구 경로.

**dry-run 설정이 그대로 적용된다.** 손으로 돌릴 때만 진짜로 지워지는 동작은 사고를 부른다.

### 8.6 에러 로그 확인 (`/adm/error-log`)

[§2.7](#27-삼킨-실패는-log_error-로-끌어올린다) 의 짝이 되는 화면. 삼킨 기록 실패와
미처리 예외를 사람이 보는 자리다.

| URL | 화면 |
|---|---|
| `GET /adm/error-log` | 목록 + 최근 7일 분류별 집계. `?errorClass=` 로 분류 필터 |
| `GET /adm/error-log/{id}` | 상세 — 스택트레이스는 **여기서만** 읽는다 |

- **읽기 전용.** 등록·수정·삭제가 없다. 정리는 화면이 아니라 보존기간 배치가 맡는다
  (`log_error` 36개월, [§9.3](#93-파기-등록부))
- 목록 쿼리는 `stack_trace`(mediumtext)를 **싣지 않는다** — 페이지마다 끌어오면 목록이
  통째로 느려진다
- `trace_id` 가 `log_access.trace_id` · `sql.log` 의 `[traceId]` 와 같은 값이다.
  "이 오류를 낸 요청이 어떤 쿼리를 돌렸나" 를 세 곳에서 맞춰 볼 수 있다
- `query_string` 은 **적재 시점에** 민감 파라미터 값이 `***` 로 가려진 상태다
  (`ErrorLogger.MASKED_PARAMS` — `reason` `state` `encodedata` `token` 등).
  화면이 가리는 것이 아니므로 CSV·API 로 내보내도 안전하다
- 세션 ID 는 **마지막 8자만** 남긴다 — 전체를 보관하면 유출 시 세션 탈취 재료가 된다
- URL 규칙을 새로 넣지 않았다. `/adm/**` ROLE_ADMIN(priority 20)이 이미 덮는다
- 상세는 없는 ID 에 404 대신 목록으로 되돌린다. 보존기간 배치가 지운 직후의 북마크
  접근이 대표적인데, 그건 오류가 아니다

미처리 예외는 `UnhandledErrorRecorder`(`HandlerExceptionResolver`, 최우선 order)가 담는다.
**응답은 건드리지 않는다** — `null` 을 돌려주므로 오류 화면은 기존 흐름이 만든다.
4xx 계열(`ErrorResponse`)과 인가 거부(`AccessDeniedException`)는 남기지 않는다 —
봇의 404 탐색만으로 표가 가득 차고, 인가 거부는 접근 로그의 상태코드가 이미 갖고 있다.

---

## 9. 보존기간 · 파기

### 9.1 정책 (2026-07-29 사용자 확정)

| 대상 | 보존 |
|---|---|
| 회원 PII 본체 | **즉시 파기** (탈퇴 시 NULL → 1년 후 행 삭제) |
| 개인정보 접근·파기 이력 | **5년** |
| 탈퇴 원장 | **36개월** |
| 나머지 `log_*` | **36개월** |
| 통계 `stat_*` | **영구** |

원칙: **회원 개인정보는 최소로, 이력은 길게.** 보유한 개인정보가 적을수록 유출 시
피해가 작고, "누가 언제 무엇을 했는가" 는 길게 남겨야 사고 때 추적이 된다.

### 9.2 설정 일원화

`config/retention/RetentionProperties` ← `gopcms.retention.*`

보존기간이 코드 상수로 흩어지면 정책이 바뀔 때 반드시 하나를 빠뜨리고, 빠뜨린 쪽은
조용히 옛 기간으로 계속 돈다. **새 보존기간을 코드에 박지 말 것.**

`stat_*` 에 대응하는 키가 없는 것은 누락이 아니다 — 영구 보존이라 기간이라는 개념이 없고,
값을 두면 언젠가 누군가 파기 기준으로 쓴다.

### 9.3 파기 등록부

`logging/retention/dto/RetentionTarget` — **제외 대상까지 명시적으로 나열한다.**

제외를 "목록에 안 적음" 으로 표현하면 다음 사람이 빠뜨린 것인지 일부러 뺀 것인지 알 수
없다. 그래서 `purgeable=false` + 제외 사유를 함께 적는다.

| 테이블 | 보존 | 파기 |
|---|---|---|
| `log_access` `log_audit` `log_error` `log_security` `log_file_download` | 36개월 | O |
| `log_privacy_access` | **5년** | O |
| `log_pii_purge` | 5년 | **X** — 자기 기록을 같은 배치가 지우지 않는다 |
| `tb_login_history` (**primary_db**) | 36개월 | O |

### 9.4 `${}` 없이 테이블명 분기

테이블명은 `#{}` 로 바인딩할 수 없고 `${}` 는 금지 규약(SQLi)이다. 그래서 매퍼 XML 이
`<choose>` 로 **미리 적어 둔 문장 중 하나를 고른다** — 파라미터는 분기 선택에만 쓰이고
SQL 문자열이 되지 않는다. 서비스가 등록부로 한 번 더 검증한다.

**새 테이블을 추가할 때는 XML 에 분기를 함께 넣어야 한다.** 빠뜨리면 어느 분기에도
걸리지 않아 문법 오류로 터진다(조용히 엉뚱한 테이블을 지우는 것보다 낫다).

### 9.5 크로스 DB

한 배치가 두 DB 를 오간다(`logging_db` 의 `log_*` + `primary_db` 의 `tb_login_history`).
**크로스 DB 트랜잭션은 만들지 않는다** — 테이블마다 독립 트랜잭션이고, 한 테이블이
실패해도 나머지는 진행한다.

---

## 10. 설정 키 일람

### 10.1 회원

| 키 | 기본 | 뜻 |
|---|---|---|
| `gopcms.member.consent-version` | `1.0` | 동의 이력에 남길 약관 버전 |
| `gopcms.member.default-role-id` | `ROL_…1004` | ROLE_MEMBER |
| `gopcms.member.real-role-id` | `ROL_…1005` | ROLE_REAL (인증 완료 시 추가) |
| `gopcms.member.anonymous-writer-name` | `탈퇴한 회원` | 익명화 표기 |
| `gopcms.member.adm.export-limit` | `5000` | CSV 건수 상한 |
| `gopcms.member.otp.ttl-minutes` | `5` | 인증번호 유효시간 |
| `gopcms.member.otp.max-attempts` | `5` | 검증 시도 상한 |
| `gopcms.member.otp.resend-cooldown-seconds` | `60` | 재발송 쿨다운 |
| `gopcms.member.otp.rate.per-ip` | `10` | IP 당 발송 |
| `gopcms.member.otp.rate.window-minutes` | `60` | 발송 시간창 |
| `gopcms.member.lifecycle.dry-run` | `true` | **끄면 실제로 지운다** |
| `gopcms.member.lifecycle.batch-size` | `200` | 1회 상한 |
| `gopcms.member.lifecycle.dormant-days` | `365` | 휴면 전환 기준 |
| `gopcms.member.lifecycle.withdraw-days` | `365` | 탈퇴 전환 기준 |
| `gopcms.member.lifecycle.*-cron` | §7.3 | 잡별 스케줄 |

### 10.2 보존기간

| 키 | 기본 |
|---|---|
| `gopcms.retention.withdraw-months` | `36` |
| `gopcms.retention.privacy-log-months` | `60` |
| `gopcms.retention.log-months` | `36` |
| `gopcms.retention.login-history-months` | `36` |
| `gopcms.retention.legal-basis` | 개인정보보호법 제21조… |
| `gopcms.retention.purge.enabled` | `true` |
| `gopcms.retention.purge.dry-run` | `true` |
| `gopcms.retention.purge.batch-size` | `1000` |
| `gopcms.retention.purge.cron` | `0 20 4 * * *` |

### 10.3 본인인증 · 소셜 (비밀값은 `.env`)

`gopcms.nice.{enabled,site-code,site-password,return-url,error-url,auth-type,customize,popup-url}`

`gopcms.oauth2.{enabled,redirect-base-url}` · `gopcms.oauth2.{naver|kakao|google}.
{client-id,client-secret,callback-url}`

환경변수 키는 [.env.example](../.env.example) 참조. **`.env.example` 에 실제 비밀값을
넣지 말 것.**

---

## 11. URL 접근 규칙

**인가는 DB 단일 원천**(`tb_role_url_access`, priority ASC, 무매칭 DENY).
새 URL 은 규칙 INSERT 를 **같은 커밋에** 넣어야 열린다.

| priority | 패턴 | 유형 |
|---|---|---|
| 15 | `/adm/member/export` | ROLE (ROLE_PRIVACY) |
| 30 | `/login` | PERMIT_ALL |
| 50 | `/*/member/dormant`, `/*/member/dormant/**` | PERMIT_ALL |
| 51·52 | `/*/member/mypage`, `/*/member/mypage/**` | AUTHENTICATED |
| 52 | `/member/identity/nice`, `/member/identity/nice/**` | PERMIT_ALL |
| 52 | `/member/oauth2/**` | PERMIT_ALL |
| 53 | `/*/member/join`, `/*/member/join/**` | PERMIT_ALL |
| 54 | `/*/member/find-id`, `/*/member/find-password` | PERMIT_ALL |
| 55 | `/{site}/member/**` (사이트 스코프) | ROLE |
| 60 | `/*/member/**` | AUTHENTICATED |

**순서가 곧 정책이다.** 가입·찾기 규칙이 회원 영역 잠금(55·60)보다 **뒤**에 있으면
"가입하려면 로그인부터 하라" 는 순환에 빠진다.

`AntPathMatcher` 에서 `/*/member/join` 은 **하위 경로를 포함하지 않는다** — 마법사처럼
하위가 생기면 `/**` 규칙을 따로 넣어야 한다(실측으로 잡은 문제, V919).

관련 마이그레이션: V909(기본) · V914(가입) · V915·V916(찾기) · V917(마이페이지) ·
V918(휴면) · V919(본인인증·소셜·가입 하위) · V920(관리자 내려받기)

---

## 12. 메일 템플릿

`tb_mail_template` — 발송은 `MailService.sendAsync`(비동기, SMTP 실패가 본 처리를
막지 않는다).

| 코드 | 발송 지점 | 상태 |
|---|---|---|
| `ACCOUNT_DORMANT_NOTICE_30D/7D/1D` | 휴면 사전 안내 배치 | 사용 |
| `ACCOUNT_DORMANT` | 휴면 전환 배치 | 사용 |
| `ACCOUNT_WITHDRAW_NOTICE` | 탈퇴 전환 배치 (V921) | 사용 |
| `ACCOUNT_DORMANT_RESTORED` | 휴면 복원 (인증번호 포함) | 사용 |
| `PASSWORD_RESET` | 임시 비밀번호 | 사용 |
| `ACCOUNT_DORMANT_TRANSFERRED` `MEMBER_WELCOME` `MEMBER_WITHDRAW` `PASSWORD_CHANGED` | — | **시드만 있고 미사용** |

**템플릿 모델 주의**: 날짜는 포맷하지 말고 `LocalDateTime` 그대로 넘긴다. 템플릿이
`#temporals.format` 으로 직접 찍기 때문에, 문자열을 주면
`Unable to convert String to Temporal` 로 파싱이 깨진다.

변수는 **실제로 넣는 것만** 쓴다. 없는 변수를 템플릿에 적으면 빈칸으로 나가거나 파싱이
깨진다.

---

## 13. 알려진 미완 사항

고칠 때 참고할 것. 지금은 이렇게 되어 있다는 사실의 기록이다.

| 항목 | 현재 상태 |
|---|---|
| **가입 완료 안내 문구** | `join/complete.html` 이 "인증 메일이 발송됩니다" 라고 안내하지만 **가입 시 발송되는 메일이 없다**(`MEMBER_WELCOME` 미배선). 문구를 고치거나 발송을 붙여야 앞뒤가 맞는다 |
| `email_verified_yn` | 가입 시 `N` 으로 고정되고 이를 `Y` 로 바꾸는 경로가 없다 |
| `uk_member_identity` | 무력 상태 — [§1.3](#13-알려진-스키마-문제) |
| NICE 실 왕복 | 계약 자격이 없어 **암호화·복호화·팝업 왕복이 미검증**. 자격 확보 후 재검증 필요 |
| 소셜 실 왕복 | provider 자격이 없어 토큰 교환·userinfo 미검증 |
| `log_pii_purge` 5년 파기 | 정책상 기간은 있으나 배치가 손대지 않는다 — 필요 시점(5년 뒤)에 별도 절차 |
| 레이트리밋 저장소 | 인메모리. **다중화하면 인스턴스마다 따로 세므로 실질 한도가 인스턴스 수만큼 늘어난다** — 세션 저장소와 같은 시기에 Redis 등으로 옮겨야 한다 |
| 회원 2FA | 미도입(`LoginPrincipal.twoFactorPending` 은 관리자 전용) |

---

## 14. 기능 추가 체크리스트

회원 도메인에 무언가를 더할 때 이 순서로 확인한다.

**새 화면·엔드포인트**
- [ ] 컨트롤러 접미어가 맞는가 (`Usr` 사용자 / `Adm` `/adm/**` / `Api` JSON)
- [ ] **URL 접근 규칙 INSERT 를 같은 커밋에** 넣었는가 (무매칭 DENY)
- [ ] priority 가 회원 영역 잠금(55·60)보다 앞인가 (비로그인 화면이라면)
- [ ] 하위 경로가 생긴다면 `/**` 규칙도 넣었는가

**새 PII 컬럼**
- [ ] DTO 에 `@Encrypt` 표시
- [ ] **매퍼 XML 의 모든 조회·저장 구문에 `typeHandler` 명시** (빠뜨리면 평문 저장)
- [ ] 검색이 필요하면 `*_hash` 컬럼 병행 + 서비스가 해시 생성
- [ ] `Mask` 에 마스킹 규칙 추가 + 테스트
- [ ] 관리자 화면·CSV 가 마스킹 게터를 쓰는가
- [ ] **파기 대상에 포함**했는가 (`nullifyPii` / `nullifyDormantPii`)

**새 배치**
- [ ] dry-run 기본 켜짐
- [ ] 1회 건수 상한
- [ ] 대상 0건도 로그
- [ ] ShedLock
- [ ] 단건 독립 트랜잭션이면 **워커를 별도 빈으로** (자기호출은 `@Transactional` 무시)

**새 메일**
- [ ] 템플릿을 마이그레이션으로 추가(devdata)
- [ ] 변수는 실제로 넣는 것만
- [ ] 날짜는 `LocalDateTime` 그대로

**새 파기 대상 테이블**
- [ ] `RetentionTarget` 등록부에 추가 (제외라면 사유와 함께)
- [ ] `LogRetentionMapper_maria.xml` 에 `<choose>` 분기 추가
- [ ] 보존기간은 설정에서 (`RetentionProperties`)

---

## 15. 트랜잭션 · 보안 함정 요약

실제로 겪은 것들이다. 같은 실수가 반복되지 않게 여기 모아 둔다.

| 함정 | 증상 | 대응 |
|---|---|---|
| 자기호출 `this.txMethod()` | `@Transactional` 이 통째로 무시 | 빈 분리 (`*Worker`) |
| 클래스 `readOnly=true` 상속 | 쓰기 메서드가 조용히 실패 | 메서드마다 writable override |
| 매퍼 XML `typeHandler` 누락 | 평문 저장 / 화면에 `{AG}…` | 컬럼마다 명시, `resultMap` 사용 |
| CHECK 제약 위반 | 저장이 통째로 실패 | [§1.2](#12-상태값--ddl-check-와-11) 표 확인 |
| FK 때문에 하드 삭제 실패 | 휴면 전환이 조용히 실패 | soft delete 사용 |
| Thymeleaf elvis 체인 | `${a} ?: ${b} ?: '-'` 파싱 실패 | 삼항 연산자로 |
| soft delete 행이 UNIQUE 에 남음 | **재가입이 통째로 롤백** | 해제·탈퇴 시 행째로 삭제 |
| `th:if` + `th:replace` 동일 태그 | `th:replace`(100)가 먼저 실행 | 바깥 `th:block` 으로 감싼다 |
| 템플릿 캐시 | 뷰 수정이 반영 안 됨 | 재기동 (`spring.thymeleaf.cache`) |
| CSP `form-action` | 외부 폼 전송이 **조용히** 차단 | `csp-form-action-extra` |
| 라우팅 충돌 | `/batch/withdraw` 가 `/{id}/withdraw` 에 매칭 | 경로 변수에 접두어 제약 |

> **환경 이슈**: 짧은 간격의 연속 요청(초당 수 회)에서 JVM 이 `0xC0000005`
> (ACCESS_VIOLATION)로 죽는 것을 관측했다. 자바 예외가 아니라 네이티브 크래시이며,
> CLAUDE.md 가 경고하는 **Virtual Threads + HikariCP + Windows** 조합의 알려진 증상과
> 같은 계열이다. 부하 테스트 전 별도 확인이 필요하다.
