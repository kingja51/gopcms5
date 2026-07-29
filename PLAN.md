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

### P7-4 레이아웃 양산 (완료 2026-07-29)
- [x] **layout-003 (C안)** — 좌측 고정 사이드바(240px, 어두운 면). 사이드바 본문을
      `th:fragment` 한 벌로 만들어 데스크톱 `<aside>` 와 모바일 `<dialog>` 드로어가
      **같은 마크업을 공유**(메뉴 이중 유지 방지) · 중첩 `<details>` 는 breadcrumb 으로
      서버에서 `open` 결정(JS 없이 현재 경로가 펼쳐진 채 도착)
- [x] **layout-004 (D안)** — 히어로 위 투명 헤더. breadcrumb 유무로 `.l4-transparent`/
      `.l4-solid` 교대(홈=투명·컨텐츠=불투명, 실측 확인) · 본문 720px 가독 컬럼 +
      `.l4-bleed` 로 넓은 요소만 탈출
- [x] **layout-005 (E안)** — 포털형 벤토. 바탕을 surface-subtle 로 눌러 본문 카드
      (`.l5-panel`)가 떠 보이게 · 상시 검색 · menuTree 상위 3그룹 카드형 푸터
- [x] **layout-006 (F안)** — 모바일 퍼스트 앱형. **한 벌 마크업 두 모습** — 데스크톱 상단 탭 /
      모바일 하단 고정 탭바를 `display:none` 으로 교대(스크린리더가 메뉴를 두 번 읽지
      않게 visibility 대신 완전 제거) · `env(safe-area-inset-bottom)` 여백
- [x] **layout-007 (G안)** — 공공기관 정석 3단 헤더(유틸·로고+검색·풀폭 GNB) + 좌 LNB ·
      타이틀 밴드와 breadcrumb 줄 분리(B안과의 차이) · GNB 드롭은 `:hover` 와
      `:focus-within` 양쪽으로 열어 키보드 도달 보장 · 1023px 이하는 ☰ 전체메뉴로 대체 ·
      풀 사이트맵 푸터 + 플로팅 퀵메뉴

**P7-4 완료 확인(2026-07-29, 8081 실측)**: 5종 모두 관리 화면에서 저장(302) 후
홈·컨텐츠·사이트맵 3종 200 + 구조 CSS 200 · 각 안의 고유 마크업이 실제로 출력되는지
클래스 카운트로 대조 · D안 헤더 모드 전환(홈=transparent, 컨텐츠=solid) 실측 ·
기동 시 `LayoutSmoke OK`. 검증 후 ai 사이트는 시드 상태(blueprint-001 기본, layout-001)로 복구.

> **검증 도구 함정(앱 결함 아님)**: 저장소 파일·앱 인코딩은 전부 UTF-8 이고
> `server.servlet.encoding.force=true` 도 켜져 있다. 다만 Windows 셸을 거쳐 전달된
> curl 명령줄의 한글이 콘솔 코드페이지에서 변형돼 사이트명이 깨져 저장됐다.
> `--data-urlencode name@file`(UTF-8 파일)로 재전송하니 정상 왕복 — 복구 완료.
> **검증 스크립트에서 한글은 셸 리터럴 대신 UTF-8 파일로 넘길 것.**


## P8 — 파일 관리 (업로드·다운로드)

> 원전: 선행 프로젝트 gopcms500 의 `GoPCMS_파일_기술명세서.md`(2026-05-01판).
> **테이블은 P7-2(V9)에서 이미 만들어 두었다** — `tb_file_group`·`tb_file`,
> 로그는 logging V2 의 `log_file_download`. 이 페이즈는 그 위에 코드를 올리는 일이다.
> 원전과 다른 지점은 §P8 말미 "이식 시 교정" 에 모아 둔다. **선행 문서보다 이 저장소의
> DDL·규약이 우선**이며, 원전 문서를 이 저장소로 복사해 오지 않는다.

### P8-1 공통 업로드 엔진 (`com.gonet.common.file` — DB 미의존)
- [ ] `FileUploadProperties` (`gopcms.file.upload.*`) — base/quarantine/thumb 경로,
      최대 크기, 카테고리별 확장자 화이트리스트(any·document·image·video)
- [ ] **다중 방어 파이프라인** — 확장자 화이트리스트(널바이트·경로·이중확장자 차단) →
      Tika 매직바이트(카테고리와 family 교차 검증) → 이미지 재인코딩(Thumbnailator,
      EXIF·삽입 스크립트 제거) → SHA-256(FIM) → 격리 디렉터리 저장 후 정식 이동 →
      백신 큐 enqueue. **저장소는 웹루트 밖**(V9 DDL 주석에 이미 명시)
- [ ] 파이프라인 진입 직후 SecurityContext 검사 — 비인증 업로드는 엔진 레벨에서 401
- [ ] `VirusScanQueue` 인터페이스 + `NoOpVirusScanQueue` 기본 + ClamAV INSTREAM TCP
      직구현(전용 의존성 없음). `gopcms.file.clamav.enabled=false` 가 기본
- [ ] 비동기 썸네일(`@Async`) — 긴 외부호출이므로 주 트랜잭션과 분리(`NOT_SUPPORTED`)

### P8-2 도메인 파일 서비스 (`com.gonet.primary.file`)
- [ ] `FileService`/`Impl`(+`AbstractCmsService`) · `FileMapper`/`FileGroupMapper`
      (`@EgovMapper`) — 업로드 커밋·목록·soft delete
- [ ] `ensureGroup(entityType, entityId, siteId, downloadAuth)` 단일 진입점 —
      도메인 Service 가 **폼 저장 전에** 그룹 정책을 확정한다. picker 가 lazily 만들면
      DB DEFAULT(`ROLE_MEMBER`)가 박히는 지연 윈도우가 생기는데, 그걸 없애는 게 목적
- [ ] `syncAttachments(fileGroupId, keep)` — picker 에서 뺀 파일 soft delete
- [ ] 다운로드 엔진 — Range/ETag/If-* 조건부 헤더, `Cache-Control: private, no-store`
- [ ] `virus_scan_status` **6값 정책 재정의** — V9 는 원전의 4값에 `QUARANTINED`·
      `RESCANNING` 을 더했다. 다운로드 허용은 `CLEAN`·`PENDING` 만, 나머지 4종은 차단
      (관리자 강제 다운로드만 예외)

### P8-3 업로드·다운로드 권한 (2026-07-29 확정)
- [ ] **업로드 = `ROLE_REAL` 이상.** 전개 집합에 `ROLE_REAL` 이 포함되면 통과 —
      실측상 ADMIN·MANAGER·STAFF·MEMBER·REAL 이 해당하고, **단독 역할인 `ROLE_PRIVACY`
      보유자만으로는 업로드할 수 없다**(계층 단절의 의도된 결과). 실명인증 전 계정으로
      파일이 올라오는 경로를 원천 차단하는 것이 목적
- [ ] **다운로드 권한은 등록 시점에 정한다** — 업로드 폼이 `download_auth` 를 명시하거나
      `ANONYMOUS`. 도메인 Service 는 `ensureGroup(..., downloadAuth)` 에 **항상 정책을
      명시 전달**하고, DB DEFAULT(`ROLE_MEMBER`)는 사고 방지용 안전망으로만 남긴다
      (기본값에 기대면 정책 미지정 그룹이 조용히 회원공개가 된다)
- [ ] `enforceDownloadAuth()` 단일 진입점 — `tb_file_group.download_auth` 가 유일 원천
- [ ] **OWNER_PRIVACY** — 민원글처럼 *본인만* 열람해야 하는 첨부용 정책값.
      통과 조건은 ① `ROLE_PRIVACY` 보유(role_ids CSV **토큰 정확 매치**, substring 금지)
      또는 ② 글 작성자 본인. **`ROLE_ADMIN` 도 자동 통과하지 않는 것이 의도**이며,
      시드의 `ROLE_PRIVACY` 가 이미 parent NULL(계층 단절)이라 admin 의 상속 CSV 에
      들어가지 않아 이 성질이 그대로 성립한다(V906 실측)
- [ ] `created_by` sentinel 가드 — `""`/`ALL`/`ANONYMOUS`/`SYSTEM` 은 본인으로 인정하지
      않는다. 시드·시스템 계정 글에서 대량 owner-bypass 가 나는 것을 막는 장치
- [ ] **`ROLE_EMPLOYEE` 는 사용하지 않는다** — 정책 enum·폼 선택지에서 제외.
      V9 CHECK 제약에는 값이 남지만 앱이 절대 쓰지 않으므로 무해한 상위집합으로 둔다
      (적용된 마이그레이션 수정 금지 — 굳이 조이려면 V10 으로 전진)
- [ ] 다운로드 이력 `log_file_download` 적재 — **logging_db 라 크로스 DB JOIN 금지**,
      주 트랜잭션과 격리(`REQUIRES_NEW` + try/catch 를 트랜잭션 밖에)

### P8-4 화면
- [ ] `fragments/file-picker` — 드래그앤드롭·다중·순서·제거. **인라인 스크립트 금지**
      (외부 `.js` + `htmx:load` 멱등 초기화 + `data-action` 위임), KRDS 토큰만
- [ ] 폼 GET 시점에 도메인 PK 사전 발급(`Uid.next()`) → hidden + picker `entityId`.
      폼 취소 시 남는 orphan 은 purge 배치가 정리
- [ ] `FileAdmController` `@RequestMapping("/adm/file")` — 목록·상세·강제 다운로드·삭제
- [ ] `FileApiController` `@RequestMapping("/api/v1/file")` — 업로드 JSON 응답
- [ ] `FileDownUsrController` `@RequestMapping("/file")` — 단일·그룹 ZIP·썸네일 스트리밍.
      `/file` 을 사용자 프로그램 네임스페이스로 신설하고 `SKIP_PREFIXES` 에 추가한다
      (바이너리 응답이라 사이트 컨텍스트·템플릿이 필요 없다)

### P8-5 스케줄러 (완료 2026-07-29)
- [x] **ShedLock 실제 배선** — 그동안 pom 에만 있고 코드가 없었다(락 없이 도는 상태).
      `LockProvider` 를 logging_db 에 두고 `usingDbTime()` — 인스턴스 간 시계가 어긋나면
      락이 예정보다 일찍 풀려 중복 실행이 난다
- [x] **파일 정리 배치**(`FilePurgeJob`, 04:00) — 보존기간 경과 soft-delete 파일의
      물리 삭제 + 썸네일 + 고아 그룹 회수
- [x] **기본값이 dry-run** — 스케줄은 돌지만 대상만 로그에 남는다. 배치를 처음 켜는 순간
      오래된 파일이 한꺼번에 사라지는 사고가 가장 흔해서, 운영자가 로그를 확인하고
      명시적으로 꺼야 삭제가 시작되게 했다
- [x] 삭제 순서 고정 — **디스크 먼저, DB 나중**. 뒤바뀌면 참조를 잃은 파일이 디스크에
      영원히 남는다(반대 방향 사고가 훨씬 고치기 어렵다)
- [x] 단건 독립 트랜잭션 — `FilePurgeWorker` 를 **별도 빈**으로 분리했다.
      같은 빈에 두면 자기호출로 프록시를 우회해 `@Transactional` 이 통째로 무시된다
      (CLAUDE.md 트랜잭션 함정). 그러면 배치 전체가 한 트랜잭션처럼 묶여, 디스크는 이미
      지웠는데 DB 만 되살아나는 최악의 조합이 된다
- [x] **백신 재검사 배치**(`VirusScanRetryJob`) — `clamav.enabled=true` 일 때만 빈이
      만들어진다. 미연동 구성에서는 모든 파일이 영원히 PENDING 이라 재검사가 무의미하다

**P8-5 완료 확인(2026-07-29, 8081 실측)**: cron 을 15초로 당겨 검증 —
dry-run 이 대상 2건·고아그룹 1건을 정확히 집어내고 **디스크·DB 를 전혀 건드리지 않음**,
dry-run 을 끄자 정확히 2건만 삭제(살아 있는 1건은 보존)되고 고아 그룹도 회수,
`shedlock` 테이블에 `filePurgeJob` 락 레코드 생성 확인, 백신 잡은 비활성 구성에서
빈 자체가 만들어지지 않음. 검증 데이터 전량 제거.

### P8 이식 시 교정 (원전 문서를 그대로 따르면 깨지는 것)
| 원전 | gopcms5 | 이유 |
|---|---|---|
| `FG0_` 접두어 | **`FGR_`** | conventions §2 레지스트리 확정값 (`UidPrefix.FGR`) |
| `UuidV7Generator.generate("FIL")` | **`Uid.next(UidPrefix.FIL)`** | 채번 단일 경로 |
| `PageResponse` | **`PageResult`** | P7-1 공통 기반 |
| `FileMngController` | **`FileAdmController`** | 컨트롤러 접미어 Usr/Adm/Api |
| `/admin/system/file` | **`/adm/file`** | URL 네임스페이스 = 보안 경계 |
| `/fileDown/**` | **`/file/**`** | 사용자 프로그램 네임스페이스 규약(`/bbs/`·`/prg/`·`/file/`)에 맞춤 + `SKIP_PREFIXES` 등록 |
| URL별 권한을 SecurityConfig 에 | **`tb_role_url_access` INSERT** | 인가는 DB 단일 원천, 무매칭 DENY |
| `virus_scan_status` 4값 | **6값** | V9 CHECK 제약 |
| 업로드 권한 = 인증 전체 | **`ROLE_REAL` 이상** | 실명인증 전 계정의 업로드 차단 |
| `ROLE_EMPLOYEE` 정책값 | **미사용** | 역할 자체를 쓰지 않기로 확정 |
| — | `tb_file.original_content` | gopcms5 에만 있는 컬럼(문서 본문 Markdown 추출) |

---

## P9 — 게시판 관리

> 원전: gopcms500 `GoPCMS_게시판_기술명세서.md`(2026-05-02판).
> **테이블 6종은 P7-2(V9)에 이미 있다** — `tb_bbs_{master,category,article,comment,like,report}`.
> P8(파일) 이 선행 — 첨부가 file-picker + `ensureGroup` 을 그대로 재사용한다.
> layout-001~007 의 좋아요/신고 밴드에 이미 자리를 잡아 두었다("게시판 페이즈에서 활성").

### P9-0 사용자 프로그램 라우팅 선행 작업 (완료 2026-07-29)
- [x] **URL = `/bbs/{siteCode}/{bbsCode}`** — 사용자 프로그램은 `/bbs/`·`/prg/` 처럼
      **프로그램 네임스페이스가 앞**에 오고 siteCode 가 뒤따른다. 컨텐츠 URL
      (`/{siteCode}/{slug}`)과는 자리 순서가 반대이며, 둘 다 유지된다
- [x] `SiteResolveFilter` 확장 — 프로그램 네임스페이스에서 **두 번째 세그먼트를 사이트코드로**
      읽는다. 건너뛰지 않는 이유: 사이트 컨텍스트가 서야 3축(layout·template·theme)이
      적용된다 (`/file/**` 은 바이너리라 SKIP 이 맞다 — 다르게 다룬다)
- [x] **`UrlNamespaces` 단일 원천 신설** — SKIP(사이트 개념 없음) / PROGRAM(2번째가 사이트) /
      RESERVED(둘의 합 + 고정 라우트)를 한 곳에서 정의하고,
      `SiteResolveFilter`·`SiteServiceImpl`(사이트코드 검증)·`ContentUsrController`
      (예약 slug)가 전부 이것을 본다. 목록이 흩어져 있으면 하나만 빠졌을 때
      조용히 깨진다 — `bbs` 를 예약어에서 빠뜨리면 site_code 를 `bbs` 로 만들 수 있고
      그 순간 `/bbs/…` 가 게시판인지 그 사이트인지 구분되지 않는다
- [x] 빈 마디를 건너뛰는 세그먼트 파서 — `//bbs//ai//notice` 도 같은 판정을 받아야
      우회 시도가 다른 결과를 내지 않는다
- [x] `tb_role_url_access` 규칙(V912) — `/bbs/**`·`/prg/**` PERMIT_ALL(priority 200).
      여기서 잠그면 공개 게시판이 비로그인에게 닫히고, 없으면 무매칭 DENY 로 아예 안 열린다.
      실제 판정은 안쪽 두 겹(`tb_bbs_master.read_auth`·`tb_file_group.download_auth`)이 한다

**P9-0 완료 확인(2026-07-29, 8081 실측)**: `/bbs/nursingcollege/free` 가 접속 로그에
`nursingcollege` 로 기록 — 두 번째 세그먼트 해석 동작 확인. `/bbs/ai/notice`·
`/prg/ai/professor` 는 404(컨트롤러 미구현)이고 403 이 아니므로 인가 통과.
`/ai/about`·`/ai/index` 는 200 으로 컨텐츠 경로 영향 없음. 예약어 5종(bbs·prg·file·adm·index)
사이트코드 등록 거부(사이트 3건 유지). 라우팅 규칙 단위 테스트 11건 신규.

### P9-1 마스터 + 카테고리 (완료 2026-07-29)
- [x] `BoardAdmController` (`/adm/board/**`) — 마스터 CRUD, 사이트 내 `bbs_code` UNIQUE,
      사용중지/재사용, **글이 남은 게시판 삭제 차단**(사용 중지로 유도 — soft delete 로
      감췄는데 그 글이 어디에도 안 보이는 상태가 더 나쁘다)
- [x] **8타입** NOTICE·BODO·FREE·FAQ·QNA·GALLERY·FILE·**YOUTUBE** (V9 CHECK 기준)
- [x] 카테고리 CRUD — **마스터 폼 안에서 관리**(분류는 게시판 없이 존재할 수 없고 수도 적다) ·
      게시판 내 `category_code` UNIQUE · 매핑된 글이 있으면 삭제 차단
- [x] 첨부 상한은 화면에서 **MB 로 받고 저장 때 byte 로 환산** — 조회 시 되돌리지 않으면
      수정 화면이 늘 빈 칸으로 열려 저장할 때마다 기본값으로 덮인다
- [x] 통합 게시판 CSV 정규화 — 중복 제거·**중첩 금지**(통합이 통합을 품으면 목록 질의가
      재귀가 된다)·상한 24개(VARCHAR(1000) ÷ 41자)
- [x] `download_auth` 변경 시 소속 글의 file_group 일괄 cascade — **공지글 제외**
      (`notice_yn='N'`) 로 ANONYMOUS 보존, 삭제글도 제외(복구 시 그 시점 정책을 다시 받게)

**완료 확인(2026-07-29, 8081 실측)**: 게시판 등록(한글명 왕복 정상·MB→byte 환산
10485760) · 분류 등록 · 거부 2종(중복 코드·대문자 코드) · **cascade 실측** — 마스터를
ANONYMOUS→ROLE_MEMBER 로 바꾸자 일반글 첨부만 ROLE_MEMBER 로 바뀌고 **공지글·삭제글
첨부는 ANONYMOUS 유지**(로그 `groups=1 (공지 제외)`). 검증 데이터 전량 제거.

### P9-2 게시글 + 첨부 (완료 2026-07-29)
- [x] `BoardArticleService` — 작성/수정/삭제, `write_auth` 검증(ADMIN=담당자 이상),
      **공지 지정은 STAFF 이상**. 작성자는 세션에서 채운다 — 폼 hidden 을 믿으면
      남의 이름으로 글이 올라간다
- [x] `resolveArticleDownloadAuth()` — **`notice_yn='Y'` 면 ANONYMOUS 강제**(마스터 정책보다
      우선), 아니면 마스터 값 → `ensureGroup(정책 명시)` + `syncAttachments()`.
      첨부를 모두 빼면 `file_group_id` 참조를 끊고 빈 그룹은 purge 배치가 회수
- [x] 조회수 — `ArticleViewCounter`(30분 쿠키, 상한 80건, HttpOnly). 세션 대신 쿠키를 쓰는
      이유는 비로그인 열람마다 세션이 생기는 비용을 피하기 위함. 증가 쿼리는 **감사컬럼
      미주입** + `updated_at = updated_at` 로 ON UPDATE 갱신까지 차단
- [x] 비밀글 — `canRead()`(작성자 본인 또는 담당자 이상). 관리자 목록에서도 배지로 표시 —
      열어 보고 나서 아는 것은 늦다. **사용자 화면 연결과 "차단 시 조회수 미증가" 는 P9-5**
- [x] **본문은 저장 시점 정화** — `HtmlSanitizer`(OWASP) 단일 지점. `html_yn='Y'` 만 정화하고
      평문 게시판은 렌더에서 이스케이프한다. allowlist 는 **두 에디터 출력의 합집합**
      (인라인 style 보존 — Tiptap 기준만 잡으면 CrossEditor 본문이 저장마다 깎인다)
- [x] `BoardArticleAdmController`(`/adm/board/{bbsMasterId}/article`) + 목록·폼 화면.
      폼 GET 에서 PK 사전 발급(첨부 picker 가 저장 전에 그 ID 로 올린다)

**완료 확인(2026-07-29, 8081 실측 + 단위 테스트 10종)**: XSS 정화 실측 —
`<script>`·`onclick`·`<iframe>`·`javascript:` 전부 제거되고 `<b>` 는 보존 ·
공지 전환 시 첨부 그룹이 ROLE_MEMBER→**ANONYMOUS 자동 전환**(일반글은 마스터 정책 유지) ·
조회수 +1 에도 `updated_by`·`updated_at` 불변 · 목록 공지 우선 정렬·제목 검색 1건 적중.
검증 데이터 전량 제거.

> **실측 결함 2건 발견·수정**: ① **PK 사전 발급 함정** — "PK 가 비었으면 신규" 로 판정해
> 신규 글이 수정으로 오인됐고, 없는 행을 UPDATE 해 **0건 갱신으로 조용히 사라졌다**(302 는
> 정상 반환). 실제 존재 여부를 물어 판정하도록 고치고, 겸사겸사 잘못된 PK 주입과
> **다른 게시판 글을 이 게시판 URL 로 수정하는 시도**도 여기서 막았다.
> ② 정화 정책이 표 머리셀의 `scope` 를 떨어뜨렸다(단위 테스트가 발견). `Sanitizers.TABLES`
> 는 요소만 열어 줄 뿐이라 속성 규칙이 붙지 않았던 것 — **접근성(KWCAG) 판정 대상**이라
> 그대로 두면 스크린리더가 표를 못 읽는다.

### P9-2b 위지윅 에디터 — 2종 교체형 (2026-07-29 확정)

**Tiptap(기본) · Namo CrossEditor 4** 두 가지를 두고 `application.yml` 하나로 고른다.
적용 대상은 `html_yn='Y'` 게시판 본문과 `tb_content.body` 공용이다.

```yaml
gopcms:
  editor:
    provider: tiptap        # tiptap | crosseditor
```

#### 교체 지점을 3개로 못 박는다
에디터마다 손대는 곳이 흩어지면 교체가 곧 회귀가 된다. 아래 3개만 provider 별로 갈리고,
**나머지(저장·정화·업로드·권한)는 provider 와 무관하게 한 벌**이다.

- [ ] `EditorProperties`(`@ConfigurationProperties("gopcms.editor")`) + `EditorProvider` enum —
      provider 별 자산 목록(js/css)과 프래그먼트 이름만 들고 있는다
- [ ] `fragments/editor.html` — `th:fragment="editor(field, value)"` 하나가 대외 계약.
      내부에서 provider 로 분기해 `editor-tiptap` / `editor-crosseditor` 를 include 한다.
      **폼 화면은 이 프래그먼트만 부르고 provider 를 몰라야 한다**
- [ ] provider 별 어댑터 JS — 공통 계약은 하나: *제출 직전에 편집 결과 HTML 을
      `<textarea name="{field}">` 에 써 넣는다*. 폼 쪽 코드는 그대로 두고 어댑터만 갈린다
- [ ] 기동 검증 — 선택된 provider 의 자산이 실제로 없으면 **fail-fast**
      (`LayoutSmokeRunner` 와 같은 방식). 폼을 열고 나서야 깨진 걸 알게 되면 늦다

#### provider 와 무관하게 고정할 것 (교체해도 흔들리면 안 되는 축)
- [ ] **서버 새니타이저 allowlist 는 provider 와 무관하게 하나** — 에디터를 바꿨다고
      보안 수준이 달라지면 안 된다. 저장 시점 단일 지점에서 정화하고, 프런트 정화는
      편의 기능으로만 취급
- [ ] **저장 HTML 은 상호 호환이어야 한다** — provider 를 바꿔도 기존 본문이 열려야 하므로
      allowlist 는 두 에디터 출력의 **합집합**으로 잡고 저장 시 정규화한다. CrossEditor 는
      HTML 4.01/XHTML 계열 마크업·인라인 스타일을 뱉으므로(벤더 명세) Tiptap 스키마만
      기준으로 잡으면 기존 글이 저장할 때마다 깎여 나간다
- [ ] **업로드는 이미지 전용 + `ROLE_STAFF` 만**, 첨부(file-picker)와 경로 분리.
      `/api/v1/file/image` 단일 엔드포인트에 `uploadImage` 카테고리(확장자×Tika 교차검증 +
      재인코딩) 강제 — provider 는 이 엔드포인트에 맞춰 어댑터로 붙인다
- [ ] **미리보기에 `blob:` 금지** — `img-src` 에 `blob:` 이 없다(실측). 업로드를 먼저
      끝내고 반환된 `/file/{id}` URL 을 본문에 넣는다

#### Tiptap (기본)
- [ ] ProseMirror 기반이라 스키마 밖 마크업이 모델 단계에서 떨어진다 — 서버 allowlist 와
      스키마를 같은 목록으로 맞추면 "에디터에선 되던 게 저장 후 사라지는" 불일치가 없다
- [ ] **번들 빌드 필요** — 현재 npm 은 Tailwind CLI 만 돌린다. ESM 묶음이라 esbuild 정도를
      추가해 `static/js/vendor/editor-tiptap.js` 한 장으로 떨군다(CDN 금지·self-host 유지)
- [ ] CSP 실측 — `script-src` 에 `unsafe-eval` 이 없다. 요구하면 CSP 를 여는 대신 재검토

#### Namo CrossEditor 4 (상용)
- [ ] **벤더 번들 self-host** — `static/js/vendor/crosseditor/` 아래. 어댑터가
      공통 계약(제출 직전 textarea 주입)을 구현
- [ ] **벤더가 주는 Java 업로드 핸들러를 쓰지 않는다.** 이유가 둘이다.
      ① 보안 — 샘플 핸들러는 우리 다중 방어(확장자·Tika·재인코딩·격리)를 우회한다.
      웹쉘 침해 이력을 생각하면 업로드 경로가 둘이 되는 것 자체가 위험이다.
      ② 기술 — 벤더 명세가 **JDK 1.7+** 기준이라 `javax.servlet` 시대 코드다.
      Tomcat 10.1 / Spring Boot 3.5 는 `jakarta.servlet` 이라 그대로는 로드조차 안 된다.
      → 업로드는 우리 `/api/v1/file/image` 로 돌리고 응답 형식만 어댑터가 변환한다
- [ ] **CSP 실측이 도입 조건** — iframe 기반 상용 번들이라 `unsafe-eval`·`frame-src` 를
      요구할 수 있다. 요구한다면 관리자 전 구간의 CSP 를 여는 셈이므로, 열기 전에
      "이 provider 를 쓸 것인가" 를 다시 판단한다(§결정 대기)
- [ ] **라이선스 확인 후 반입** — 1도메인 단위 상용 라이선스다. 구매·적용 도메인이
      확정되기 전에는 번들을 저장소에 커밋하지 않는다
- [ ] 접근성 — 공공 사이트 대상 제품이라 접근성을 표방하지만, **KWCAG 판정 기준은
      에디터 UI 가 아니라 산출 HTML** 이다. 표 머리셀·대체텍스트가 실제로 붙는지 실측

### P9-3 댓글 (완료 2026-07-29)
- [x] 대댓글 **depth 2 제한 — 초과는 거절이 아니라 평탄화**. 3단을 시도하면 조부모의
      자식으로 붙여 같은 대화 줄기에 남긴다(사용자가 "답글을 눌렀는데 실패" 하지 않는다)
- [x] 삭제는 **자식 대댓글까지 함께** — 부모 없는 답글이 떠 있으면 무슨 말에 대한 답인지
      알 수 없다. 다른 글의 댓글에 답글 다는 시도는 거부
- [x] 비밀댓글 `canRead()` — 작성자·**글쓴이**·담당자. 비밀댓글은 그 둘 사이의 대화라
      글쓴이가 못 보면 성립하지 않는다
- [x] 관리자 모더레이션 — 게시글 폼 안에서 숨김/노출/삭제. **본문은 지우지 않고 상태만**
      바꾼다(왜 숨겼는지 확인할 근거 보존)
- [x] `comment_count` 재계산 — 증분이 아니라 재계산. 세는 대상은 `delete_yn='N'` +
      **`status='PUBLISHED'`** 로 화면에 보이는 것만

**완료 확인(2026-07-29, 8081 실측 + 단위 테스트 8종)**: 폼에 댓글 3건 트리 표시
(대댓글 들여쓰기 1건) · 숨김→HIDDEN · **부모 삭제 시 자식도 DELETED** ·
숨김/노출 왕복에 댓글 수가 1↔0 으로 정확히 따라감. 검증 데이터 전량 제거.

> **실측 결함 1건**: 재계산 SQL 이 `delete_yn` 만 봐서 **숨긴 댓글도 세고 있었다** —
> "댓글 3" 인데 하나도 안 보이는 상태가 된다. 상태 조건을 더해 화면과 일치시켰다.

### P9-4 좋아요 / 신고 (완료 2026-07-29)
- [x] `BoardLikeApiController` / `BoardReportApiController` (`/api/v1/board/**`) — 순수 JSON.
      **토글은 `ON DUPLICATE KEY UPDATE` 한 문장** — 조회 후 분기하면 연타 시 UNIQUE 충돌로
      500 이 난다. 취소했다 다시 눌러도 같은 행이 되살아나 행이 늘지 않는다
- [x] 카운트 비정규화 동기 — **증분이 아니라 재계산**(취소·재클릭이 섞이면 증감은 어긋난다).
      감사컬럼·`updated_at` 은 건드리지 않는다 — 좋아요는 글의 수정이 아니다
- [x] 신고 임계(기본 5, `gopcms.board.report-threshold`) 도달 시 PUBLISHED → **REPORTED
      자동 숨김**. 삭제하지 않는 것이 핵심 — 자동 조치가 최종 판단이 되면 조직적 신고로
      멀쩡한 글을 내릴 수 있다. `0` 으로 두면 자동 전환이 꺼진다
- [x] **기각(REJECTED)은 무조건 복원** — 사람 판단이 자동 조치보다 위다. 기각한 신고는
      유효 신고 수에서도 빠진다(근거 없는 신고를 모아 임계를 채우는 길 차단)
- [x] 관리자 검토 큐 `/adm/board-report` — 대상 본문·작성자·누적 신고 수를 함께 보여준다
      (사유만 보고는 판단할 수 없다). 숨김 처리 / 기각 2버튼
- [x] 신고 응답에 **누적 건수를 담지 않는다** — 신고자가 임계까지 몇 건 남았는지 알면
      여럿이 맞춰 채우기 쉬워진다. 좋아요는 반대로 숫자를 돌려준다(클라이언트 ±1 금지)
- [x] 재사용 조각 — `fragments/reaction-band` + `/js/board-reaction.js`(data-action 위임,
      인라인 스크립트 없음). **CSRF 토큰을 밴드가 직접 들고 있어** 레이아웃 수정 없이 붙는다.
      P9-5 에서 7종 레이아웃의 반응 밴드와 게시판 화면이 이 한 벌을 쓴다
- [x] V913 URL 규칙 2건 — 좋아요·신고 모두 `AUTHENTICATED`(priority 72·73 — `/api/**`
      DENY(80)보다 앞). 익명 좋아요는 중복을 막을 수 없어 숫자가 의미를 잃고,
      익명 신고는 남용을 추적할 수 없다

**완료 확인(2026-07-29, 8081 실측 + 단위 테스트 8종)**: 토글 on→off→on 왕복에
`tb_bbs_like` 행은 1개 유지·`like_count` 동기 · 비로그인 401 JSON(P6-2 API 계약 준수) ·
잘못된 대상 유형/식별자/사유 400 · 중복 신고 409 · **임계 5 도달 시 자동 REPORTED** ·
전량 기각 시 count 0 + **PUBLISHED 복원** · 숨김 처리 시 HIDDEN. 검증 데이터 전량 제거.

### P9-5 사용자 화면
- [ ] `/bbs/{siteCode}/{bbsCode}` 목록 · `/{articleId}` 상세 · `/write`·`/{id}/edit`
- [ ] 8타입별 `front/board/{TYPE}/{list,detail,write}.html` — 레이아웃 축과 무관하게
      동작해야 한다(컨트롤러는 논리 뷰명만 반환, Resolver 가 재작성)
- [ ] **KRDS 준수는 타협 없음** — 시맨틱 토큰(`bg-surface`·`text-fg-subtle`·`border-line`)과
      `.krds-*` 프리셋만. raw hex·Tailwind 기본색(`bg-blue-600`)·기본 타이포(`text-xl`)
      금지, radius ≤12px, 8px 그리드. 7종 레이아웃 어디에 얹혀도 무너지지 않아야 한다

### P9-6 통합 게시판 + canManage
- [ ] `grouped_board_ids` CSV(V9 에 컬럼 존재) — 채워져 있으면 read-only 합본 뷰.
      정규화 규칙: 중복 제거·중첩 금지·상한
- [ ] `canManage = (master.bbsMasterId == article.bbsMasterId)` — 통합 URL 로 들어오면
      수정·삭제·모더레이션 비노출
- [ ] **Service 단 가드까지 함께** — 원전은 UI 가드만 넣고 Service 가드를 후속으로
      미뤄 URL 직접 호출 우회가 열려 있었다(원전 §14-8 자인). 처음부터 같이 넣는다

### P9-7 게시판 검색 — LIKE 검색 (2026-07-29 확정)
- [ ] 제목·본문·작성자 `LIKE` 검색. **색인 테이블(`tb_search_index`)·FULLTEXT·Nori 는
      쓰지 않는다** — 원전의 색인 동기화 훅 5경로가 통째로 불필요해진다
- [ ] `#{}` 바인딩 유지 — 와일드카드는 `CONCAT('%', #{keyword}, '%')` 로 SQL 안에서
      붙인다(`${}` 금지). `%`·`_` 는 이스케이프 처리
- [ ] 검색 대상은 `bbs_master_id` + `status='PUBLISHED'` 로 먼저 좁힌 뒤 LIKE 를 건다 —
      선행 인덱스(`idx_article_bbs_status`)가 먹어야 전체 스캔이 안 난다

### P9 이식 시 교정
| 원전 | gopcms5 | 이유 |
|---|---|---|
| `ART_` / `CMT_` / `BLK_` / `BRP_` | **`BBA_` / `BBC_` / `LIK_` / `RPT_`** | 레지스트리 확정값 |
| `/bbs/{siteCode}/{bbsCode}` | **그대로 채택** | 사용자 프로그램은 `/bbs/`·`/prg/` 로 시작 — 컨텐츠(`/{siteCode}/{slug}`)와 자리 순서가 반대인 것이 의도 |
| `/admin/system/board/**` | **`/adm/board/**`** | URL 네임스페이스 = 보안 경계 |
| `BoardMngController` | **`BoardAdmController`** | 컨트롤러 접미어 규약 |
| 7타입 | **8타입**(+YOUTUBE) | V9 CHECK 제약 |
| **버튼 토큰 §9 전체** | **이식 금지** | `bg-blue-600`·`rose-*`·`!text-white` 는 raw Tailwind 색 — KRDS 규약 위반. `.krds-btn-*` 프리셋으로 대체 |
| 감사 이벤트 5경로 | **미도입** | 6컬럼 감사(created/updated_by·ip·at)와 접속·보안 로그로 충분하다고 판단 |
| `tb_search_index` + ngram | **LIKE 검색** | 색인 테이블·동기화 훅 5경로 전부 불필요 |
| textarea + sanitize | **Tiptap** | 위지윅 확정 — 이미지 업로드는 `ROLE_STAFF` 만 |
| — | `html_yn`·`captcha_yn` | gopcms5 에만 있는 컬럼. 원전에 없던 정책이라 새로 설계 |

---

## P10 — 회원 관리

> 원전: gopcms500 `GoPCMS_회원_기술명세서.md`(2026-04-23판) — `doc/` 에 사본이 있다.
> **테이블 7종은 이미 있다**: `tb_member` · `_consent` · `_dormant` · `_dormant_notice` ·
> `_oauth` · `_password_history` · `_withdraw` + `vw_user_login`.
> 메일 템플릿 10종도 V910 에 시드돼 있다(휴면 알림 3단계·전환·복원 포함).
> **생명주기 정책은 원전과 다르다** — 사용자 확정값(§P10-4)이 우선한다.

### P10-0 착수 전 점검 (원전과 스키마가 어긋나는 지점)
- [ ] **본인확인 값이 CI 가 아니라 DI 다.** gopcms5 는 `di`/`di_hash`/`parent_di`/
      `parent_di_hash` 를 쓰는데, 원전 문서는 **DI 를 DROP 하고 CI 로 갔다**. 정반대라
      그대로 옮기면 컬럼이 안 맞는다. 스키마(DI)를 정본으로 삼는다
- [ ] `tb_member` 에만 있는 컬럼 반영 — `email_verified_yn`(원전의 미구현 항목 M-01 자리),
      `birth_year`, `captcha_required_yn`, `site_code`. 반대로 원전의 `group_ids` 는 없다
- [ ] `tb_member_withdraw` 는 **이미 PII 를 담지 않는 설계** — `login_id_hash`·`di_hash`·
      탈퇴일시·사유·`retention_expire_at`·`legal_basis` 뿐이다. 탈퇴 원장은 그대로 쓰면 된다
- [ ] `vw_user_login` 은 `delete_yn='N'` 만 거른다. 휴면은 **행 자체가 `tb_member_dormant`
      로 이관**되므로 뷰에서 자동으로 사라진다 — 별도 조건이 필요 없다(설계 확인 완료)
- [ ] 인가 방식 재검토 — 원전은 "회원은 role 매핑이 없으니 `AUTHENTICATED + user_type`" 으로
      갔지만, gopcms5 는 `role_ids` CSV 가 살아 있고 `ROLE_REAL`(실명인증 회원)도 있다.
      P6-2 의 site-scoped ROLE 규칙(`/ai/member/**` ROLE_REAL)이 이미 그 전제로 깔려 있다

### P10-1 가입
- [ ] 7단계 플로우(유형선택 → 약관 → 본인인증 → 폼 → 가입 → 완료), 세션이 유일한 신뢰원 —
      `userType` 같은 값은 폼 hidden 을 믿지 않는다
- [ ] ADULT / CHILD(14세 미만, 법정대리인 DI 공유) 분기
- [ ] 중복 차단 — `login_id` · `email_hash` · UNIQUE(사이트+이름+di_hash+parent_di_hash)
- [ ] PII 는 `@Encrypt`(`{AG}`) + 검색용 `*_hash` 병행 (conventions §6)
- [ ] 약관 동의 이력은 UPDATE 가 아니라 INSERT 누적(버전·IP·UA 동반)

### P10-2 로그인·인증
- [ ] `/member/login` — 관리자(`/adm/login`)와 물리 분리, UserDetailsService 교차 차단
- [ ] 실패 5회 잠금(30분) · CAPTCHA(`captcha_required_yn`) · Bucket4j 이중 키(IP + loginId)
- [ ] **enumeration 방지** — 실패 사유를 URL 에 싣지 않고, 미존재 계정에도 더미 해시로
      BCrypt 를 1회 돌려 응답 시간을 균일화한다
- [ ] 아이디 찾기(마스킹 노출) · 비밀번호 찾기(임시 비밀번호 즉시 만료 → 변경 강제)

### P10-3 마이페이지
- [ ] step-up 재인증(TTL 5분, 5회 실패 시 흔적 파기) 후에만 개인정보 수정·탈퇴 진입
- [ ] 본인인증 근간값(loginId·birthDate·gender·di·parent*)은 수정 불가
- [ ] 비밀번호 변경 — 최근 이력 재사용 금지(P6-3 에서 이미 만든 규칙 재사용)
- [ ] 셀프 탈퇴 → §P10-4 의 탈퇴 처리와 **같은 경로**를 탄다(경로가 둘이면 정책이 갈린다)

### P10-4 생명주기 스케줄러 (2026-07-29 사용자 확정)

```
ACTIVE ──[마지막 로그인 +1년]──▶ 휴면(tb_member_dormant)
휴면   ──[휴면 전환 +1년]──────▶ 탈퇴(tb_member_withdraw + tb_member PII 전부 NULL)
탈퇴   ──[탈퇴 +1년]───────────▶ 완전 삭제(hard delete)
```

- [ ] 3개 잡을 **각각 분리** — 휴면전환 / 탈퇴전환 / 완전삭제. `com.gonet.scheduler` 에 두고
      **ShedLock** 적용(pom 에 이미 있다). 단건은 `REQUIRES_NEW` 로 격리해 1건 실패가
      나머지를 막지 않게 한다
- [ ] 기준 시각은 `Asia/Seoul` 고정. **`last_login_at` 이 NULL 인 계정(가입 후 미로그인)은
      `created_at` 을 기준**으로 삼는다 — NULL 을 빠뜨리면 그 계정만 영원히 안 늙는다
- [ ] **11개월(=휴면 30일 전) 사전 안내 메일** — 마지막 로그인 335일 경과 회원에게
      "휴면 정책 안내 + 로그인하면 계속 일반회원으로 유지된다" 를 보낸다.
      기존 `30D` 단계·`ACCOUNT_DORMANT_NOTICE_30D` 템플릿과 정확히 맞물린다
      (`tb_member_dormant_notice.stage` CHECK 이 이미 `30D/7D/1D`). **템플릿 문구만
      정책 안내형으로 갱신**하면 되고 스키마 변경은 없다
- [ ] 7일 전·1일 전 알림도 같은 방식(기존 템플릿 2종). 중복 발송은
      `(member_id, stage)` UNIQUE 로 막고, 로그인하면 이력을 지워 다음 사이클을 리셋한다
- [ ] **탈퇴 전환 예고 템플릿은 신규 필요**(현재 시드에 없음) — 휴면 만료가 다가온
      회원에게 미리 알린다. 되돌릴 수 없는 전환 전에는 통지가 있어야 한다
- [ ] **탈퇴 처리 순서를 고정한다** — ① 원장(`tb_member_withdraw`) INSERT →
      ② `tb_member` PII 컬럼 전부 NULL → ③ 커밋, **한 트랜잭션**. PII NULL 은 되돌릴 수
      없으므로 원장을 먼저 남기지 않으면 사고 시 복구·소명 근거가 사라진다
- [ ] **완전 삭제 전 자식 행 정리 순서 확인** — `tb_member_consent`·
      `_password_history`·`_oauth`·`_dormant_notice`. FK CASCADE 여부를 실측하고, 없으면
      명시 순서로 지운다
- [ ] **게시판 글 자체는 지우지 않되 작성자 표기는 익명화한다** —
      `tb_bbs_article`/`tb_bbs_comment` 의 `writer_user_id`·`writer_name` 은 크로스 참조라
      회원을 지워도 남는다. **`writer_name` 은 그 자체로 개인정보**이므로, "PII 즉시 파기"
      원칙을 지키려면 탈퇴 시점에 익명 표기로 바꿔야 앞뒤가 맞는다(§결정 대기)
- [ ] **logging_db 는 이 배치가 건드리지 않는다** — 크로스 DB 이고 보존주기가 별개다
      (§P10-7). 개인정보 파기 이력은 이미 있는 `log_pii_purge` 에 남긴다

### P10-7 보존기간 정책 (2026-07-29 사용자 확정)

**원칙: 회원 개인정보는 최소로, 이력은 길게.** 보유한 개인정보가 적을수록 유출 시
피해가 작다. 반대로 "누가 언제 무엇을 했는가" 는 길게 남겨야 사고 때 추적이 된다.

| 대상 | 보존 | 비고 |
|---|---|---|
| **회원 PII**(`tb_member` 본체) | **즉시 파기** | 탈퇴 시 PII 컬럼 전부 NULL → 1년 후 행 완전 삭제 |
| 개인정보 접근·파기 **이력** | **5년** | `log_privacy_access` · `log_pii_purge` — 파기했다는 사실 자체의 증빙 |
| 탈퇴 원장 `tb_member_withdraw` | **36개월** | `retention_expire_at` 컬럼이 이미 있다 — 계산해 채우고 배치는 이 값만 본다 |
| 나머지 로그 `log_*` | **36개월** | `log_access` · `log_audit` · `log_error` · `log_security` · `log_file_download` |
| 통계 `stat_*` | **영구** | 개인 식별 정보가 없는 집계값. 36개월로 묶으면 연도 비교가 영영 불가능해진다 |

- [ ] 보존기간을 **설정 한 곳**(`gopcms.retention.*`)에 모은다 — 코드 상수로 흩어지면
      정책이 바뀔 때 누락이 난다. 현재 yml 에 보존 관련 키가 하나도 없다(실측)
- [ ] 파기 배치는 **대상 테이블별 보존기간을 설정에서 읽는다** — 5년/36개월/영구가
      한 배치 안에 섞여 있으므로, 테이블마다 다른 값을 쓴다는 사실이 코드에 드러나야 한다
- [ ] **`log_pii_purge` 는 파기 대상이 아니다**(5년 보존) — 파기 기록을 파기하면
      "지웠다는 증빙" 이 사라진다. 배치가 자기 자신을 지우지 않도록 명시적으로 제외
- [ ] 로그 파기 배치 — logging_db 전용, ShedLock, **건수 상한 + dry-run**
- [ ] `tb_login_history`(primary, P6-3) 는 로그지만 logging_db 가 아니라 primary 에 있다 —
      36개월 대상에 넣되 **배치가 다른 DataSource** 를 탄다는 점에 주의
- [ ] **안전장치 필수** — dry-run 모드 + 1회 실행 건수 상한 + 실행 요약 로그.
      첫 가동에서 오래된 계정이 한꺼번에 삭제되는 사고를 막는 장치이며,
      되돌릴 수 없는 배치에는 상한이 있어야 한다

### P10-5 휴면 복원 — 실명인증 / 이메일 인증번호 (2026-07-29 사용자 확정)
- [ ] 휴면 계정은 `vw_user_login` 에 없으므로 평범한 로그인은 그냥 실패한다.
      **아이디·비밀번호가 맞을 때만** 휴면 안내로 분기한다(`tb_member_dormant.password` 가
      남아 있어 검증 가능). 아이디만으로 "휴면입니다"를 알려주면 계정 존재가 새어나간다
- [ ] 복원 수단 ① **실명인증** — NICE 본인인증(jar 는 `lib/`, JPMS 플래그 pom 반영 완료).
      결과 DI 를 `di_hash` 와 대조해 본인 확인. 성공 시 `ROLE_REAL` 연계
- [ ] 복원 수단 ② **이메일 인증번호** — 6자리 OTP, TTL 5분, 시도 5회 제한, 재발송 쿨다운,
      Bucket4j 레이트리밋. **평문 저장 금지(해시)** · 로그·예외 메시지에 노출 금지.
      OTP 보관 테이블 신규 + 접두어 레지스트리 등록 필요
- [ ] 성공 시 `tb_member_dormant` → `tb_member` 역이관(status=ACTIVE) + 알림 이력 삭제 +
      복원 메일(`ACCOUNT_DORMANT_RESTORED`, 시드 완료)
- [ ] 실패 응답은 어느 항목이 틀렸는지 구분하지 않는다(단일 메시지 + 타이밍 균일화)

### P10-6 관리자 회원 관리 (P7-3 잔여분 #19 회수)
- [ ] `MemberAdmController` `@RequestMapping("/adm/member")` — 목록·상세·상태변경·
      비밀번호 초기화·잠금해제·강제탈퇴. **관리자는 회원을 생성하지 않는다**(정책)
- [ ] 검색은 평문 컬럼(`login_id`·`nickname`)만 LIKE, 이메일은 `email_hash` 정확 매칭 —
      암호화 컬럼에 LIKE 를 걸 수 없다는 제약이 화면 설계를 규정한다
- [ ] 목록·상세의 개인정보는 **마스킹 기본**. 엑셀 내려받기는 사유 필수 + 건수 상한
- [ ] 휴면·탈퇴 현황 조회 + 배치 수동 실행(운영 복구 경로)

### P10 이식 시 교정
| 원전 | gopcms5 | 이유 |
|---|---|---|
| `ci` / `ci_hash` (DI 는 DROP) | **`di` / `di_hash`** | 스키마가 DI 기준 — 정반대다 |
| `VARCHAR(36)` UUID | **`VARCHAR(40)` `MBR_`+UUIDv7** | PK 규약 |
| `MemberMngController` | **`MemberAdmController`** | 컨트롤러 접미어 |
| `/admin/system/member` | **`/adm/member`** | URL 네임스페이스 = 보안 경계 |
| 쿠키 `PCMS_SID` | **`GOPCMS_SID`** | SecurityConfig 상수 |
| 휴면 5년·탈퇴 5년 보관 | **휴면 1년 → 탈퇴, 탈퇴 1년 → 완전삭제** | 사용자 확정 |
| 복원 = 3요소(이름·이메일·비번) | **실명인증 또는 이메일 OTP** | 사용자 확정 |
| `AuditLogger` 5경로 | **미도입** | 6컬럼 감사 + 접속·보안 로그로 충분(기결정) |
| `group_ids` | 컬럼 없음 | 등급 개념 미도입 |
| SSO 미구현 | `tb_member_oauth` **존재** | 확장 지점이 이미 열려 있다 |

---

## P11+ — 이후 로드맵 (예고)

조직(직원·부서) → 템플릿 CSS 진짜 구현(blueprint-001 부터, SG 스타일가이드 병행,
레이아웃 7종은 P7-4 로 완료) → 배너/팝업·일정·설문 → 공통 프로그램 →
eGov 호환성 확인 신청.

---

## 결정 대기 / 리스크

| 항목 | 내용 | 기한 |
|---|---|---|
| ~~UUIDv7 구현~~ | ✅ 확정: 선행 프로젝트 `UuidV7Generator` 이식 + `Uid` enum 래퍼 | P1 완료 |
| ~~감사컬럼 처리~~ | ✅ 확정: MyBatis `AuditInterceptor` (3개 SqlSessionFactory 공통) | P1 완료 |
| .env 주입 | IntelliJ Run Config 수동 입력 vs EnvFile 플러그인 | P0 |
| eGov RTE 다운로드 | maven.egovframe.go.kr 접근·좌표 실검증 (pom 주석의 주의사항) | P0 |
| ~~layout-001 외 레이아웃~~ | ✅ 해소: 7종 전부 구현·실측(P7-4). 남은 것은 시각 마감(템플릿 CSS) | P7-4 완료 |
| ~~시큐리티 공백기~~ | ✅ 해소: P6 로 인증·인가·세션·헤더 적용 (P0~P5 는 무인증이었음) | P6 완료 |
| 인증 기준 데이터 위치 | 역할·관리자·URL 규칙이 dev 시드(V906/V909)에 있다 — 운영 이관 필요 | P7 |
| 세션 저장소 | 현재 인메모리(단일 인스턴스 전제) — 다중화 시 Redis 등 공유 저장소 필요 | 배포 전 |
| ~~파일 다운로드 URL 자리~~ | ✅ 확정: `/file/**` — 사용자 프로그램 네임스페이스(`/bbs/`·`/prg/`·`/file/`) 규약에 맞추고 `SKIP_PREFIXES` 등록 | P8 |
| ~~ROLE_EMPLOYEE 부재~~ | ✅ 확정: **사용하지 않는다.** 앱 enum·폼에서 제외, V9 CHECK 의 값은 무해한 상위집합으로 존치 | P8 |
| ~~감사 이벤트 저장소~~ | ✅ 확정: 도입하지 않는다. 6컬럼 감사 + 접속·보안 로그로 충분 | — |
| ~~한국어 전문검색 방식~~ | ✅ 확정: **LIKE 검색.** 색인 테이블·FULLTEXT·Nori 모두 미도입 | P9-7 |
| ~~위지윅 에디터 선정~~ | ✅ 확정: **Tiptap(기본) + Namo CrossEditor 4** 2종을 `gopcms.editor.provider` 로 교체. 업로드는 provider 무관하게 이미지 전용 + `ROLE_STAFF` 한정 | P9-2b |
| **Tiptap 번들 빌드** | 현재 npm 은 Tailwind CLI 만 돌린다. ESM 번들러(esbuild 등) 추가 필요 | P9-2b 착수 시 |
| **CrossEditor 도입 가부** | 상용 iframe 번들이라 CSP `unsafe-eval`·`frame-src` 를 요구할 수 있다. 요구하면 **관리자 전 구간 CSP 를 여는 셈** — 실측 후 도입 여부를 다시 판단한다 | P9-2b 착수 시 |
| **CrossEditor 라이선스** | 1도메인 단위 상용. 구매·적용 도메인 확정 전에는 벤더 번들을 저장소에 커밋하지 않는다 | 도입 전 |
| **에디터 간 본문 호환** | provider 를 바꿔도 기존 `body` 가 열려야 한다. CrossEditor 는 HTML4.01/XHTML 계열 마크업을 뱉으므로 allowlist 를 두 출력의 합집합으로 잡지 않으면 기존 글이 저장할 때마다 깎인다 | P9-2b |
| **탈퇴 시 `di_hash` 존치 여부** | 탈퇴 원장(`tb_member_withdraw`)의 `di_hash`·`login_id_hash` 는 **재가입 제한·중복가입 차단·분쟁 대응의 유일한 근거**다. "개인정보 필드 모두 NULL" 을 원장까지 적용하면 그 기능이 사라진다. `tb_member` 는 전부 NULL, 원장 해시는 존치가 기본안 — 확인 필요 | P10-4 착수 전 |
| **완전 삭제 후 게시글 작성자** | `tb_bbs_article.writer_user_id`·`writer_name` 은 크로스 참조라 회원을 지워도 남는다. **`writer_name` 은 개인정보다** — "PII 즉시 파기" 원칙을 따르면 탈퇴 시 작성자명도 익명 표기로 바꿔야 앞뒤가 맞는다. 게시글 자체는 보존 | P10-4 |
| ~~탈퇴 원장 보존기간~~ | ✅ 확정: **36개월**(`retention_expire_at` 에 계산해 채운다). 로그 `log_*` 도 36개월 | P10-7 |
| ~~개인정보 5년 vs 탈퇴 후 완전삭제~~ | ✅ 확정: **회원 PII 는 즉시 파기**(탈퇴 시 NULL → 1년 후 행 삭제). "5년" 은 개인정보 접근·파기 **이력**(`log_privacy_access`·`log_pii_purge`)에만 적용 | P10-7 |
| ~~통계 `stat_*` 보존~~ | ✅ 확정: **영구 보존** — 개인 식별 정보가 없는 집계값이라 파기 대상이 아니다 | P10-7 |
| **휴면 정책의 법적 근거** | 1년 미이용자 분리보관·파기 의무(개인정보 유효기간제)는 폐지된 것으로 알고 있다 — 그렇다면 1년 휴면은 법적 의무가 아니라 서비스 정책이고 사전통지 의무 범위도 달라진다. 개인정보 담당자 확인 권장(내 판단을 근거로 삼지 말 것) | P10-4 착수 전 |
| **프로그램 네임스페이스 3중 동기화** | `/bbs/`·`/prg/` 목록이 `SKIP_PREFIXES`·컨텐츠 예약 slug·사이트코드 예약어 세 곳에 흩어지면 라우팅이 조용히 깨진다 — 상수 한 곳을 세 곳이 참조하도록 | P9-0 |
