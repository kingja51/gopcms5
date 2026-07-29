# PLAN.md — gopcms5 개발 플랜

> **이 문서는 진행 추적 정본이다.** 작업 시작 전 현재 페이즈를 확인하고,
> 완료 시 체크박스를 갱신한다. 규약은 [CLAUDE.md](CLAUDE.md)·[doc/conventions.md](doc/conventions.md) 우선.

## 목표 (이번 플랜 범위)

**데모 사이트 2종(main·ai)을 브라우저에서 실제 테스트** — `/ai/index` 랜딩,
`/ai/about` 등 컨텐츠 10종, `/ai/sitemap`, GNB 메뉴 트리, 템플릿·테마·레이아웃 전환.

**범위 제외 (이후 페이즈)**: Spring Security(다중 체인·2FA·RBAC — **템플릿·사이트·메뉴
테스트 완료 후 착수**), 게시판(V4), 회원·조직, 관리자 화면, 파일 업로드, 배치.

```
P0 스캐폴딩 → P1 코어 인프라 → P2 Resolver 파이프라인 → P3 뷰 기반 → P4 메뉴·컨텐츠 → P5 통합 테스트
                                                                                    └→ (완료 후) P6 Security
```

---

## P0 — 프로젝트 스캐폴딩 (기동 + Flyway 적용까지)

- [x] pom.xml 루트 승격(doc/pom.xml 제거) + `mvnw` wrapper 생성 — JDK 21(`C:\Program Files\Java\jdk-21`)
      로 컴파일 검증 완료(eGov RTE 좌표 해석 확인). ※ 시스템 기본 java 는 1.8 — JAVA_HOME 지정 필요
- [x] **spring-boot-starter-security 임시 주석** — `TODO(P6)` 표기
      (spring-security-test·thymeleaf-extras-springsecurity6 함께 주석)
- [x] 패키지 골격 — `com.gonet.{common,config,logging,primary,scheduler,secondary}`
      package-info.java 로 역할 문서화 / NICE jar `lib/` 배치
- [x] resources 필터에 `**/*.sql` 추가 — 원안 누락분(빠뜨리면 Flyway 마이그레이션이 war 에서 탈락)
- [x] `GopcmsApplication`(main) + `ServletInitializer` 이중 진입점
- [x] `application.yml` + `application-local.yml` — 3-DB 접속(환경변수), flyway 자동구성 off,
      virtual threads on, `spring.profiles.default: local`
- [x] 로컬 MariaDB **11.8.3** — DB 3개: **`gopcms5_primary` / `gopcms5_secondary` / `gopcms5_logging`**
      (사용자 생성분 사용. ⚠ `gopcms_*` 3개는 구 프로젝트 DB — 접근 금지.
      수동 생성돼 있던 코어 6테이블은 시드 재생성 가능 확인 후 드롭 → Flyway 소유로 전환)
- [x] 환경변수 — 사용자가 GOPCMS_DB_* 12종 직접 세팅 (.env.example 키와 1:1)
- [x] `FlywayConfig` — DB 별 Flyway 빈 3개(@DependsOn 순차, vendor 자동판별, devdata 토글)
      + `DataSourceConfig`(Hikari ×3, P1 의 TxManager/SqlSessionFactory 전 단계)
- [x] Tailwind 경로 — `npm run css` → `static/css/output.css` (37KB 빌드 확인, gitignore 등록)

**완료 기준 충족 확인(2026-07-28)**: `mvnw spring-boot:run` 기동 성공 ·
primary `flyway_schema_history` = V1·V2·V3·V900·V901 전부 success(시드: 사이트 2 ·
템플릿 8 · 테마 23 · 레이아웃 7 · 메뉴 21 · 컨텐츠 10) · secondary/logging 이력 테이블
생성(0건 정상) · `/actuator/health` = {"status":"UP"}. **→ P0 완료, 다음 P1.**

## P1 — 코어 인프라 (규약의 골격)

- [x] `UuidV7Generator` — 선행 프로젝트 검증 구현 **원본 이식**(사용자 지시) +
      `Uid.next(UidPrefix)` 타입 가드 래퍼(enum 28종 = conventions §2) + 테스트 5종 그린
- [x] 3-DB Config(`MyBatisConfig`) — TxManager(`primary/secondary/loggingTransactionManager`)·
      SqlSessionFactory ×3 + **eGov `MapperConfigurer` + `@EgovMapper`** 패키지별 스캔
      (FQN 은 RTE 5.0.0 jar 실측: `org.egovframe.rte.psl.dataaccess.mapper.*`).
      매퍼 XML 콜로케이션 + `VendorDatabaseIdProvider`(mariadb/postgresql databaseId 분기)
- [x] `AbstractCmsService extends EgovAbstractServiceImpl` — 전 서비스 공통 부모
- [x] 감사컬럼 — **MyBatis Interceptor 확정·구현**(`Auditable`+`AuditorContext`+
      `AuditInterceptor`, INSERT=6종/UPDATE=3종, 기존 값 존중, 3개 팩토리 공통 등록)
- [x] Caffeine `CacheConfig` (`siteContext` 10분·200 / `viewExists` 1시간·2000)

**완료 기준 충족 확인(2026-07-28)**: 컴파일·UidTest 5/5 그린 · 8081 스모크 기동에서
MapperConfigurer 3개 스캔 로그 확인(mapper 0건 = P2 전 정상) · health UP.
※ 사용자 IntelliJ 인스턴스(8080)와 분리해 검증. **→ P1 완료, 다음 P2.**

## P2 — 사이트 해석 + 템플릿 Resolver 파이프라인 (아키텍처 핵심)

- [x] `SiteContext` + `SiteMapper(@EgovMapper)+XML`/`SiteService(Impl)` — 3축 조인 쿼리,
      폴백(krds·''·layout-001)은 서비스 단일 지점, `@Cacheable(siteContext, unless null)`
- [x] `SiteResolveFilter` — 첫 세그먼트 해석 → default_yn 폴백, ThreadLocal+attr 이중
      바인딩 finally clear, AuditorContext(IP) 세팅 겸임
- [x] `SiteTemplateViewResolver` — `front/**` 재작성 + `_default` 폴백(viewExists 캐시).
      **뷰 캐시 off 필수**(원본 뷰명 키 캐시 → 레이아웃 오염 — ThymeleafViewConfig 주석)
- [x] `SiteContextModelAdvice` — `site`·`siteLayout`·`themeClass`·`currentUri` 주입
- [x] 기동 스모크(`LayoutSmokeRunner`) — 활성 사이트의 layout.html+템플릿 CSS 검증, 실패 시 기동 중단
- [x] **`EgovConfig`(leaveaTrace)** — `EgovAbstractServiceImpl` 이 `@Resource` 로 요구하는
      필수 빈(클래식 context-common.xml 대응). 미등록 시 전 서비스 빈 생성 실패 — P2 실측 발견
- [ ] (P5 로 이월) `?tmpl=`·`?layout=` 프리뷰 세션 sticky

**완료 기준 충족 확인(2026-07-28)**: 8081 스모크 — LayoutSmoke OK ·
`/ai/ping` = blueprint-001.css + 인공지능학과 + layout-001 decorate ·
`/main/ping` = krds.css · `/nosite/ping` = 기본 사이트(main) 폴백. 부수 산출:
layout-001 플레이스홀더·`_default/ping.html`·템플릿 CSS 스텁(krds·blueprint-001).
※ Thymeleaf 함정 실측: 링크식은 `@{${…}}` 형태 필수(`@{'a'+(식)}` 은 미평가).
**→ P2 완료, 다음 P3.**

## P3 — 뷰 기반 (_default + layout-001 + CSS)

- [x] `templates/layouts/_default/` — `index.html`(히어로+바로가기+새소식) ·
      `content.html`(th:utext 본문) · `sitemap.html`(4뎁스 전개) — 전부 null-safe,
      P4 모델 계약(menuTree: name/href/children · content · recentContents · breadcrumb) 명시
- [x] `layout-001/layout.html` — A안 슬롯 계약 9종 전체(스킵링크·마스트헤드(hc 토글·
      날씨/로그인 자리)·GNB 1~3뎁스+검색폼·SUB_HERO 슬롯·breadcrumb·content·
      좋아요/신고 밴드·푸터·scripts 슬롯). 구조 CSS 는 `/css/layouts/layout-001.css`
      (3층 분리: 전역 KRDS → 템플릿 색 → 레이아웃 구조 — 색은 토큰 변수만)
- [x] `fragments/` — site-footer · breadcrumb 2종
- [x] 정적 자원 — output.css 재빌드(39KB, 템플릿 유틸 자동 스캔 확인) + 템플릿 CSS 스텁(P2 완)
- [x] htmx 2.0.10 self-host(npm htmx.org → /js/vendor/) + `app.js`(위임·hc localStorage·
      htmx:load 멱등 가드) — CSP nonce 는 P6, favicon 은 `data:,` 로 요청 억제(P7 사이트관리)

**완료 기준 충족 확인(2026-07-28)**: 8081 스모크 — 자산 6종 전부 200(404 없음) ·
/ai/ping 이 A안 레이아웃 전 슬롯 안에서 KRDS 스타일 렌더(브라우저 스크린샷 확인) ·
hc 토글 동작(body 흑색 전환·복원·localStorage). **→ P3 완료, 다음 P4.**

## P4 — 메뉴 · 컨텐츠 도메인 (테스트 대상 기능)

- [x] `MenuMapper`/`MenuService` — 트리 조립(1회 순회)+href 해석(CONTENT=/{sc}/{slug} ·
      URL=link_url · BOARD=V4 대기 · FOLDER=null), menuTree 는 SiteContext 에 동반 적재
      (siteContext 캐시에 트리째 — evict 시 함께 갱신), breadcrumb 경로 탐색(menuId/href)
- [x] `ContentMapper`/`ContentService` — PUBLISHED+게시기간 slug 조회, 조회수 증가
      (**쓰기 메서드 writable override 실전 적용** — 트랜잭션 함정 규약), 최신 5건(홈)
- [x] `ContentUsrController` — `/` 리다이렉트(기본 사이트) + index/sitemap/{slug} 캐치올.
      **경로 변수 정규식 제약 필수**(실측 이슈: `/{siteCode}/{slug}` 가 `/css/output.css`
      를 삼켜 정적 리소스 404 — `{slug:[a-z0-9-]{1,200}}` 로 점(.) 경로 제외).
      경로 siteCode ↔ 컨텍스트 불일치는 404(canonical 원칙)
- [x] 예약 slug 404 + 에러 페이지 2장(`error/404.html`·`error.html` — ERROR 디스패치는
      ThreadLocal 해제 후라 standalone 구성) / 임시 Ping 컨트롤러·뷰 제거
- [x] breadcrumb — content.menuId → menuTree 경로 해석 → fragments/breadcrumb 렌더

**완료 기준 충족 확인(2026-07-28, 8081 스모크 + 브라우저 스크린샷)**:
V901 10개 URL 전부 200 · `/ai/index` GNB 6대메뉴+드롭다운(/ai/greeting href)+새소식 ·
`/ai/about` breadcrumb(홈›학과소개›학과소개)+조회수 증가 실측 · `/ai/sitemap` 4뎁스 ·
`/main/index`(krds) · `/` → 기본 사이트 리다이렉트 · 404 3종(미존재·예약 slug·미등록 사이트).
**→ P4 완료, 다음 P5(통합 테스트 — 사용자 시나리오).**

## P5 — 통합 테스트 (사용자 실제 테스트 시나리오)

- [x] **템플릿 전환**: 사용자 실측(nursingcollege trust-002→blueprint-001) + Claude 검증
      (CSS 링크 전환 확인). 반영은 재기동 경유 — 무재기동 evict 는 P7 사이트관리 저장 훅에서
- [x] **레이아웃 전환**: 실증 — nursingcollege layout-001→002(V903, B안 전체펼침 GNB) ·
      사용자 재전환 실측. 커스텀 랜딩(sites/)·컨텐츠는 레이아웃 무관 유지 확인
- [x] **테마 전환 + 복합 FK**: teal 적용 실증 · **교차 검증 2종**(존재하지 않는 테마,
      타 템플릿 테마(blueprint+trust teal)) 모두 ERROR 1452 거부 — DB 가 무결성 강제
- [x] **폴백**: template_id NULL → krds CSS 폴백(명시 layout·커스텀 랜딩 유지) ·
      미해석 siteCode → 404(canonical — P4 에서 default 폴백 대신 404 로 확정) ·
      루트(/) → 기본 사이트 리다이렉트
- [x] **hc(고대비)**: 토글·복원·localStorage 실측(P3) / **모바일**: 767px 미디어쿼리
      실측(LNB 스택·전체펼침 패널 억제·1뎁스 가로 스크롤)
- [x] IntelliJ HTTP Client — [http/01-front-smoke.http](http/01-front-smoke.http)
      (헬스·3사이트 25+ URL·정적 자산 10종·404 계약 4종, local/smoke 환경 분리)

**P5 완료(2026-07-28)** — 전 항목 통과. 3축(템플릿·테마·레이아웃) 전환·거부·폴백이
DB 제약과 Resolver 파이프라인 양쪽에서 실증됨. **→ 다음 P6(Security) — 착수 조건
"템플릿·사이트·메뉴 테스트 완료" 충족. P6 상세 계획 수립부터 시작.**

## P6 — Spring Security (착수 2026-07-28 — P5 완료로 조건 충족)

**로그인 계약(확정)**: 관리자 `/adm/login` · 사용자 `/login?siteCode={sc}`(사이트 레이아웃
으로 렌더, 성공 시 `/{sc}/index`). 인증 원천 = **vw_user_login 단일 뷰**(user_type +
MEMBER 는 site 스코프 필터 필수 — 동일 login_id 양측 공존 가능).
**관리자 IP 게이트(확정)**: `/adm/login` 폼은 요청 IP 가 tb_admin_allow_ip 에 존재할 때만
노출(미등록 IP → `/`), 인증은 **(admin_id, ip_address) 쌍 매칭 필수**.

### P6-1 로그인 골격 (완료 2026-07-28)
- [x] pom Security 3종 해제 (starter·thymeleaf-extras·security-test)
- [x] **V6 auth 테이블** — 원안(D:\test\auth테이블.sql) 검토 15건 교정 적용:
      실행불가 5(중복 컬럼·미존재 컬럼 참조·tb_auth 누락·생성 순서) + 규약(ascii_bin·
      소문자·uuid_v7() 기본값 제거·접두어 14종 신등록) + vw_user_login 수정판
      (ADMIN 타입·department 조인·EMPLOYEE 확장 지점 주석)
- [x] V906 시드 — 역할 계층 5종(ADMIN>MANAGER>STAFF>MEMBER>REAL) + closure 15행
      (self 5 + 전개 10) + admin/admin1234!(허용 IP 127.0.0.1·::1) + ai 회원 user1/user1234!
- [x] SecurityConfig — 다중 체인(adm ①: hasRole ADMIN + RoleHierarchy / default ②:
      permitAll + 회원 폼) · BCrypt · CSRF 기본 on
- [x] Provider 2종 — Admin(IP 쌍 매칭 → 잠금 → 상태 → 비밀번호, 실패 5회=30분 잠금·
      성공 리셋) / Member(siteCode 파라미터 → 사이트 스코프 조회)
- [x] LoginAdmController(IP 게이트) · LoginUsrController · 뷰 3종(adm/login ·
      adm/index 대시보드 골격 · _default/member/login)
- [x] SiteResolveFilter — 해석 ② 순위로 siteCode 쿼리 파라미터 추가 (/login?siteCode=)

### P6-2 DB RBAC (완료 2026-07-29)
- [x] `DynamicAuthorizationManager` — tb_role_url_access(priority ASC → **사이트 규칙 우선** →
      긴 패턴 우선, 첫 매칭 확정, **무매칭 DENY**+WARN). access_type 7종 전부 구현
      (ROLE 은 규칙 role_id ∩ 사용자 전개 role_ids). 규칙·권한은 Caffeine `urlAccess`(5분).
      **두 체인 모두 DB 단일 원천** — `/adm/login` 허용까지 규칙(priority 10)으로 이관
- [x] V909 규칙 시드 20건 — /adm/**·로그인·정적자원·error·health·회원영역·API DENY·최후 /**.
      규칙 0건 기동은 `RbacSmokeRunner` 가 중단(전면 차단 상태로 뜨는 침묵 장애 방지)
- [x] 등록 절차 문서화 — [conventions.md §7](doc/conventions.md) (표·평가 규칙·절차 4단계·
      지우면 안 되는 규칙) + CLAUDE.md 보안 항목에 "SecurityConfig 예외 금지" 명시
- [x] 회원 전용 영역 조임 — default 체인 permitAll 해제, `/*/member/**` AUTHENTICATED(MEMBER).
      미인증은 `SiteAwareAuthenticationEntryPoint` 가 `/login?siteCode=` 로 사이트 유지 유도,
      `/api/**` 는 401 JSON 계약 (로그인 HTML 응답 금지)
- [x] `RoleService.rebuildHierarchy()` — adjacency→closure 전량 재전개 + 관리자(tb_admin_role
      기준)·회원(현재 CSV 기준, 가산만) 역할 CSV 재계산. 순수 로직 `RoleClosure` 분리 +
      테스트 5종 그린(순환 참조 예외 포함). 배치는 `RoleHierarchyRebuildJob`(cron 기본 비활성 —
      정상 경로는 P7 저장 훅 직접 호출)

**완료 기준 충족 확인(2026-07-29, 8081 실측)**: RbacSmoke 20건 로드 ·
익명(front 200 / 정적 200 / `/adm/index`→302 adm/login / `/ai/member/**`→302 `/login?siteCode=ai` /
`/actuator/env`→302) · 회원 user1 로그인 후 `/ai/member/**` 통과·`/adm/index` 403 ·
관리자 admin 로그인 후 `/adm/**` 200·`/ai/member/**` 403(user_type 경계) ·
**사이트 스코프 실증**: user1 의 ROLE_REAL 제거 시 `/ai/member/**` 403(ai 규칙) /
`/nursingcollege/member/**` 404(전역 규칙 통과) · 재전개 배치 실행으로 role_ids 원복
(역할 6·closure 16·회원 1건 교정). **→ P6-2 완료, 다음 P6-3.**

### P6-3 강화 (완료 2026-07-29)
- [x] **2FA(TOTP)** — `TotpService`(googleauth) + QR **서버 생성 data: URI**(외부 차트
      서비스로 시크릿 전송 금지·CSP img-src 준수) · 등록 화면 `/adm/2fa/setup` ·
      시크릿은 확정 전까지 세션에만(중도 이탈 시 계정 잠김 방지) · 그룹 강제
      (`two_factor_required`)면 `TwoFactorEnrollmentFilter` 가 등록 전 전 화면 차단
- [x] **세션 정책** — GOPCMS_SID(HttpOnly·Lax·30m) · changeSessionId() ·
      maximumSessions(1, 선점 방식) + 체인별 만료 URL · 로그아웃 쿠키 삭제
- [x] **CSP nonce + 보안 헤더** — `SecurityHeadersFilter`(요청당 난수 nonce, 전 응답에
      적용) · script-src 'self' 'nonce-…' · frame-ancestors/object-src none 등 5종 헤더 ·
      인라인은 **hc 복원 1개만** 남기고 nonce 부착(FOUC 제거 — app.js 복원 로직 이관)
- [x] **client_ip 일원화** — `ClientIpResolver`(GOPCMS_TRUSTED_PROXIES CIDR CSV,
      **미설정이면 XFF 무시**, 오른쪽→왼쪽 첫 비신뢰 홉 채택). 접근 로그·감사컬럼·
      IP 게이트·인증 Provider 전부 이 경로 경유
- [x] **허용 IP CIDR/RANGE** — `IpMatch`(IpAddressMatcher + RANGE 바이트 비교) ·
      SQL 완전일치 → 활성 행 Java 매칭으로 전환, touch 는 매칭된 ip_id 기준
- [x] **AccessLog actor_* + 로그인 이력** — `ActorCaptureFilter`(체인 안쪽에서 주체를
      request attr 로 이관 — 바깥 AccessLogFilter 시점엔 SecurityContext 가 비어 있음) ·
      V7 `tb_login_history` + `LoginHistoryRecorder`(REQUIRES_NEW·실패 격리),
      결과코드 9종으로 **진짜 사유는 이력에, 사용자 응답은 항상 일반 문구**
- [x] **비밀번호 만료·이력·CAPTCHA** — `PasswordPolicy`(2종 10자/3종 8자·90일) ·
      `PasswordService`(재사용 금지) + `/adm/password` 화면 · 만료 시 로그인 거부
      (FAIL_EXPIRED) · `CaptchaService`(잠금 이력 계정 한정 세션 산술 문답, 1회용)
- [x] **TOTP 시크릿 암호화** — conventions §6 형식(`{AG}` + AES-256-GCM) 프리미티브
      `Aes256Gcm` 구현. 회원 PII `@Encrypt` TypeHandler 는 이 클래스를 감싸는 형태로 확장

**완료 기준 충족 확인(2026-07-29, 8081 실측 + 단위 테스트 22종 그린)**:
CSP 헤더-본문 nonce 일치·요청마다 상이 · GOPCMS_SID 발급 · 동시 세션 1개(선점당한 쪽
`?expired`) · 로그인 이력 SUCCESS/FAIL_NOT_FOUND/FAIL_PASSWORD/FAIL_CAPTCHA/FAIL_EXPIRED/
FAIL_2FA 적재 · 접근 로그 actor(ADMIN/MEMBER/ANONYMOUS) · 비밀번호 변경 4종 거부
(현재값 오류·확인 불일치·구성 위반·재사용) + 성공 시 강제 재로그인 ·
2FA 미등록 시 전 관리 화면 → `/adm/2fa/setup` 강제, 등록 후 OTP 없는 로그인 거부,
시크릿 `{AG}` 암호문 저장 확인. **P6-2 인가 회귀 통과.**

> **실측 결함 2건 발견·수정**: ① 비밀번호 이력에 *새* 값을 넣어 방금 버린 비밀번호로
> 되돌아갈 수 있었다 → *물러나는* 값을 남기도록 수정. ② MariaDB 컬럼 인라인 CHECK 는
> `DROP CONSTRAINT` 로 못 지운다 → `MODIFY COLUMN` 재정의(flyway-migration.md §5 반영).
> dev 시드는 검증 후 원상복구(관리자 비밀번호·2FA 비강제·이력 테이블 정리).

### P6 잔여 (후속 페이즈에서 회수)

- 회원 비밀번호 변경 화면 — 서비스(`PasswordService`)는 MEMBER 분기까지 구현됐고
  화면만 없다. 회원 도메인 페이즈에서 `/{sc}/member/password` 로 추가.
- 만료 계정 셀프 재설정(본인확인 경유) — 현재는 로그인 거부 + 관리자 재설정.
- 회원 PII `@Encrypt` TypeHandler — `Aes256Gcm` 은 준비됨, MyBatis 배선만 남음.
- 관리자 접속 허용 시간대(`allowed_time_from/to`) 강제.
- 로그인 이력 조회 화면 · 이상징후 알림 — P7 관리자 모듈.

## P7 — 관리자 모듈 (진행 2026-07-29)

### P7-1 코어 6테이블 CRUD (완료)
- [x] 공통 기반 — `PageRequest`/`PageResult`(1-based 페이지·블록 페이저) ·
      `adm/fragments/adm-ui`(검색바·페이저·플래시·빈목록) · layout-adm LNB 활성화 ·
      목록/폼 CSS(ladm-table·ladm-form) · 인라인 핸들러 없는 `data-confirm`·
      `data-submit-on-change` 위임(CSP 규약 유지)
- [x] **사이트**(tb_site) — 3축 선택·기본사이트 단일성·저장 시 캐시 evict(**무재기동 반영**,
      P5 숙제 회수) · site_code 예약어(SiteResolveFilter.SKIP_PREFIXES) 차단
- [x] **템플릿·테마·레이아웃** — 참조 중인 행 삭제 차단(사이트/테마/템플릿 역참조 카운트) ·
      테마의 템플릿 종속(복합 FK) 사전 검증으로 500 대신 안내 문구
- [x] **메뉴**(tb_menu) — 트리 목록(페이징 없음)·depth 자동 계산·순환 지정 차단·
      4단계 제한·menu_type 별 링크 필드 정규화
- [x] **컨텐츠**(tb_content) — 사이트별 목록·slug 패턴/예약어/중복 검증(ContentUsrController
      와 목록 공유)·상태 전환 시 게시일시 자동 채움·version_no 증가

**완료 기준 충족 확인(2026-07-29, 8081 실측)**: 6개 목록·폼 200 ·
컨텐츠 등록(초안→비공개 404) → 게시 수정(즉시 200 반영) → 삭제(404 복귀) 왕복 ·
검증 거부 6종(예약 slug·중복 slug·대문자 slug·예약 사이트코드·중복 코드·대문자 코드).

> **실측 결함 3건 발견·수정**: ① 전역 advice 의 `site`(SiteContext)와 폼 모델 `site`
> (SiteAdmDto) 이름 충돌로 저장 실패 시 500 → 폼 모델을 `siteForm` 으로 분리.
> ② 빈 문자열이 null 로 변환되지 않아 3축 "선택 안 함" 저장이 거부됨 →
> `FormBinderAdvice`(StringTrimmerEditor) 전역 적용. ③ 물리 뷰가 없는 레이아웃 조합을
> 저장해 사이트를 500 으로 만들 수 있었음 → 저장 시점 자원 존재 검증 추가
> (기동 시 LayoutSmokeRunner 가 하던 검사의 런타임 판).

### P7-2 확장 테이블 반영 (완료 2026-07-29)
- [x] **primary V9** — 21테이블(파일 2·게시판 6·배너/팝업 2·일정 2·설문 6·직원 1·공통 2),
      전 테이블·컬럼 주석. 원안(D:\test\primary.sql) 대비 교정: DROP TABLE 제거 ·
      **tb_department 재생성 제외**(V6 기존 테이블·tb_admin FK 보호) · ascii_bin ·
      대문자 컬럼명 교정(PASSWORD/STATUS/POSITION) · 상충 CHECK 통합 ·
      FK 6개 보강 · article.file_group_id NOT NULL 완화 · holiday.`year` 개명
- [x] **logging V2** — 로그 5종(error·file_download·privacy_access·pii_purge·security) +
      통계 5종. 시각 포함 PK(파티셔닝 대비) 유지, client_ip 폭 통일
- [x] **V910 메일 템플릿 시드 10건** — PK 를 규약 형식(MTP_ + UUIDv7)으로 교정해 적재
- [x] 접두어 레지스트리 12종 신규 등록(BCT·FGR·EMP·SCM·SVM·SVQ·SVO·SVR·HOL·MTP·PPG) +
      실제 테이블명 확정 반영(FIL·LIK·RPT) — conventions §2, UidPrefix enum 동기

> **실측 함정 2건**: ① `WITH PARSER ngram` 은 MySQL 전용 — MariaDB 에서 마이그레이션 실패.
> 기본 파서로 대체(한국어 검색은 nori 색인 예정). ② Flyway 플레이스홀더 치환이 메일
> 본문의 Thymeleaf `${siteName}` 을 잡아 실패 → `placeholderReplacement(false)` 전역 해제.

> **tb_member 5건은 적재하지 않았다** (사용자 판단 필요):
> 컬럼 불일치(PASSWORD/STATUS/group_ids 는 gopcms5 tb_member 에 없음) ·
> 존재하지 않는 site_id 참조 · 다른 프로젝트 마스터키로 암호화된 `{AG}` 값(복호화 불가) ·
> 실제 인물의 이름·이메일·전화번호 해시 포함. 필요하면 익명화 후 별도 시드로 요청.

### P7-3 남은 관리 화면 (진행 중)
- [x] **역할 관리**(/adm/role) — 계층(상위 역할)·ROLE_ 코드 검증·순환 차단 ·
      참조(계정/하위 역할/URL 규칙) 있으면 삭제 거부 · **저장·삭제 시 closure 자동 재전개**
      (계층만 바꾸고 권한 스냅샷이 낡는 것을 구조적으로 막는다) + 수동 재전개 버튼
      (배치가 기본 비활성이라 콘솔 수기 수정 후의 복구 경로)
- [x] **URL 접근 규칙 관리**(/adm/url-access) — P6-2 에서 SQL 로만 편집 가능하던 인가 원천을
      화면으로. 목록 순서 = 실제 평가 순서 · 저장 시 캐시 evict 로 **다음 요청부터 반영** ·
      타입별 필수값·패턴 형식·중복 검증 · **마지막 규칙 삭제 거부**(0건이면 전 요청 차단) ·
      캐시 비우기 버튼(DB 직접 수정 시의 탈출구)
- [ ] 회원·직원 관리 · 공통코드 · 배너/팝업 · 일정 · 설문 · 파일
- [ ] 접속/보안 로그 조회 화면(logging V2 테이블 활용) · 통계 대시보드
- [ ] 컨텐츠 에디터(위지윅) + 파일 업로드 연동 · tb_content_history 버전 비교

**P7-3 부분 완료 확인(2026-07-29, 8081 실측)**: 역할 6종 목록·계층 표시 ·
신규 역할(ROLE_EDITOR, 상위 ROLE_STAFF) 등록 시 closure 조상 4행 자동 생성 ·
잘못된 코드(editor) 거부 · 참조 있는 ROLE_ADMIN 삭제 거부(보존 확인) ·
규칙 목록이 평가 순서(10·11·20·30…)대로 정렬 · `/api/**` 를 DENY→PERMIT_ALL 로 바꾸자
**재기동 없이** 401→404 전환, 되돌리자 401 복귀 · 패턴 형식·ROLE 필수값·중복 3종 거부.

## P8+ — 이후 로드맵 (예고)

게시판 도메인 구현(V9 테이블 위) → 회원·조직 → 템플릿 CSS 진짜 구현(blueprint-001 부터,
SG 스타일가이드 병행) → 나머지 레이아웃 양산(C·F 우선 검증 후) → 공통 프로그램 →
eGov 호환성 확인 신청.

---

## 결정 대기 / 리스크

| 항목 | 내용 | 기한 |
|---|---|---|
| ~~UUIDv7 구현~~ | ✅ 확정: 선행 프로젝트 `UuidV7Generator` 이식 + `Uid` enum 래퍼 | P1 완료 |
| ~~감사컬럼 처리~~ | ✅ 확정: MyBatis `AuditInterceptor` (3개 SqlSessionFactory 공통) | P1 완료 |
| .env 주입 | IntelliJ Run Config 수동 입력 vs EnvFile 플러그인 | P0 |
| eGov RTE 다운로드 | maven.egovframe.go.kr 접근·좌표 실검증 (pom 주석의 주의사항) | P0 |
| layout-001 외 레이아웃 | P5 레이아웃 전환 테스트에 최소 1종 추가 필요(layout-003 권장) | P4~P5 |
| ~~시큐리티 공백기~~ | ✅ 해소: P6 로 인증·인가·세션·헤더 적용 (P0~P5 는 무인증이었음) | P6 완료 |
| 인증 기준 데이터 위치 | 역할·관리자·URL 규칙이 dev 시드(V906/V909)에 있다 — 운영 이관 필요 | P7 |
| 세션 저장소 | 현재 인메모리(단일 인스턴스 전제) — 다중화 시 Redis 등 공유 저장소 필요 | 배포 전 |
