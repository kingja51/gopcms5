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

### P9-2b 위지윅 에디터 — 3종 교체형 (완료 2026-07-29)

**Tiptap(기본) · Namo CrossEditor 4 · CKEditor 5** 를 `application.yml` 하나로 고른다.
적용 대상은 `html_yn='Y'` 게시판 본문(관리자·사용자 폼)과 `tb_content.body` 셋이다.

```yaml
gopcms:
  editor:
    provider: tiptap        # tiptap | namo | ckeditor5
```

#### CSP 재검토 결과 (2026-07-29, 사용자 확정 "eval 사용 못 해")
- **Tiptap 은 `unsafe-eval` 을 요구하지 않는다.** ProseMirror 계열은 순수 JS 다.
  알려진 CSP 마찰은 `eval` 이 아니라 **인라인 스타일**인데, 우리 `style-src` 에는 이미
  `'unsafe-inline'` 이 있어 해당되지 않는다 — **CSP 를 한 글자도 고치지 않고 동작한다**
- `script-src` 의 `'wasm-unsafe-eval'` 은 HWP 뷰어용이며 WebAssembly 컴파일만 허용하는
  별개 토큰이다(`eval()` 을 여는 `unsafe-eval` 과 다르다)
- 후보 실측(npm, 2026-07-29): Tiptap 3.29.2 MIT(표·이미지·링크 확장 전부 MIT, 어제 배포) /
  Quill 2.0.3 BSD-3(정체) / Toast UI 3.2.2 **2023-02 이후 방치** / CKEditor 5 GPL 로고 또는 상용 /
  Trix **표 미지원**. → **Tiptap 유지**가 결론

#### 교체 지점 3개 (구현 완료)
- [x] `EditorProperties`(`gopcms.editor.provider`) + `EditorProvider` enum —
      provider 별 자산 목록·프래그먼트 이름만 들고 있다
- [x] `fragments/editor.html` — `editor(field, value, entityId, siteId)` 하나가 **대외 계약**.
      내부에서 `fragments/editor/editor-{provider}.html` 로 분기한다.
      폼 화면 3곳은 이 프래그먼트만 부르고 provider 를 모른다
- [x] provider 별 어댑터 JS — 공통 계약 하나: **제출 직전에 편집 결과 HTML 을
      `<textarea name="{field}">` 에 써 넣는다.** 서버 바인딩·저장·정화는 그대로다
- [x] 기동 검증 `EditorAssetSmokeRunner` — 선택된 provider 의 자산이 없으면 **기동 중단**.
      상용 번들은 라이선스 때문에 커밋하지 않으므로 "provider 만 바꾸고 번들은 없는" 상태가
      실제로 일어난다

#### provider 무관 고정 (교체해도 흔들리지 않는 축)
- [x] 서버 정화는 저장 시점 한 곳(`HtmlSanitizer`) — allowlist 는 두 에디터 출력의 **합집합**
      (인라인 style 보존). Tiptap 기준만 잡으면 CrossEditor 본문이 저장마다 깎인다
- [x] 이미지 업로드는 `/api/v1/file/image` 단일 경로(ROLE_STAFF) — 첨부(file-picker)와 분리
- [x] **`blob:` 미리보기 금지** — `img-src` 에 `blob:` 이 없다. 업로드를 먼저 끝내고
      반환된 `/file/{id}` 를 본문에 넣는다
- [x] 평문 게시판(`html_yn='N'`)에는 에디터를 붙이지 않는다 — 입력한 그대로가 값이다

#### Tiptap (기본)
- [x] 최소 기능 — 굵게·기울임·제목2/3·목록·인용·링크·표·이미지·실행취소.
      표는 **머리행을 켠 채** 삽입한다(표 머리셀은 KWCAG 판정 대상)
- [x] esbuild 번들 → `static/js/vendor/editor-tiptap.js`(423KB, IIFE, self-host).
      **Maven generate-resources 에 연결**해 Tailwind CSS 와 같은 방식으로 재생성하고
      산출물은 gitignore — 소스는 `src/editor/tiptap.js`
- [x] 인라인 스크립트 없음 — 설정은 `data-*` 로 넘긴다(CSP nonce 규약)

#### Namo CrossEditor 4 · CKEditor 5 (자리만)
- [x] 프래그먼트·자산 목록·기동 검증까지 준비. 번들을 `static/js/vendor/` 아래 배치하고
      provider 를 바꾸면 동작한다
- [ ] **벤더 번들은 저장소에 커밋하지 않는다** — Namo 는 1도메인 상용, CKEditor 5 는
      GPL 로고 또는 라이선스 키
- [ ] Namo: 벤더 Java 업로드 핸들러를 쓰지 않는다(다중 방어 우회 + `javax.servlet` 시대라
      Tomcat 10.1 에서 로드 불가). 우리 `/api/v1/file/image` 로 돌리고 응답만 변환
- [ ] Namo: iframe 기반이라 `frame-src` 를 요구할 수 있다 — 도입 시 실측.
      **`unsafe-eval` 은 열지 않는다**(사용자 확정)

**완료 확인(2026-07-29, 8081 + 브라우저 실측)**: `EditorSmoke OK — provider=tiptap 자산 2건` ·
관리자 게시글 폼에 툴바 12개 렌더 · **한글 입력 정상**(`<p>한글 본문 입력 테스트입니다.</p>`) ·
표 삽입 후 저장 시 `<th>`·colspan·인라인 style 이 정화를 통과해 보존 ·
평문 게시판에는 에디터가 붙지 않고 textarea 유지 · 컨텐츠 폼(`body` 필드)도 동일 동작 ·
번들 삭제 후 `mvnw compile` 이 재생성.

> **실측 결함 2건 발견·수정**:
> ① **표·이미지만 있는 본문이 빈 값으로 저장됐다.** `editor.isEmpty` 로 판단했는데 Tiptap 은
> 텍스트가 없으면 표가 있어도 empty 로 본다 — 이미지 한 장짜리 글도 저장이 안 되는 결함이라
> 정말 빈 문서일 때의 출력(`<p></p>`)만 빈 값으로 취급하도록 고쳤다.
> ② **평문 게시판에도 에디터가 붙었다.** `th:if` 와 `th:replace` 를 같은 태그에 두면
> Thymeleaf 가 `th:replace`(우선순위 100)를 `th:if`(300)보다 먼저 처리해 **조건이 무시된다** —
> 조건을 바깥 블록으로 분리했다.


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

### P9-5 사용자 화면 (완료 2026-07-29)
- [x] `BoardUsrController` — `/bbs/{siteCode}/{bbsCode}` 목록 · `/{articleId}` 상세 ·
      `/write` 작성·수정 · `/save`·`/delete` · 댓글 작성/삭제. 글 ID 경로변수는
      정규식(`BBA_…`)으로 제약해 `/write` 같은 고정 경로와 섞이지 않게 했다(P4 교훈)
- [x] **타입별 화면은 뷰를 8벌로 가르지 않는다** — 공용 뷰 한 벌이 타입별 조각을 고른다
      (`list-table`·`list-faq`·`list-gallery`). Resolver 3단 폴백(사이트/레이아웃/기본)과
      타입 분기가 곱해지면 조합이 폭발하고, 타입을 하나 더할 때마다 폴백 3벌을 함께
      만들어야 한다. FAQ 는 `<details>` 아코디언(목록=상세), 갤러리·영상은 카드 격자
- [x] 게시판 CSS 는 뷰의 `<head>` 에 `<link>` — Layout Dialect 가 레이아웃 head 에
      병합한다. 7종 레이아웃을 각각 고칠 필요가 없고, 게시판 밖 페이지는 받지 않는다
- [x] **P9-2 의 서비스 규칙이 화면에서 실증됨** — 비밀글은 `readable=false` 로 내려와
      본문·댓글·첨부를 **컨트롤러가 아예 담지 않고**(화면에서 감추면 소스 보기로 뚫린다),
      **조회수도 오르지 않는다**. 조회수 30분 쿠키 dedup 도 여기서 처음 실측
- [x] 좋아요/신고 밴드는 P9-4 조각 재사용 — 레이아웃 7종의 "좋아요/신고 밴드" 자리
      (P3~P7-4 에서 비워 둔 곳)를 이 한 벌이 채운다
- [x] KRDS 준수 — 시맨틱 토큰(`--c-*`·`--brand-*`)과 `.krds-*` 프리셋만, radius ≤12px,
      8px 그리드. raw hex 는 영상 카드의 재생 오버레이 반투명 배경 1곳뿐(토큰에 없는 값)
- [x] 남의 글 수정·삭제 차단 — **서비스 계층에서** 판정한다. 관리 화면만 있을 때는
      드러나지 않던 구멍으로, 쓰기 권한이 있다고 남의 글을 고칠 수 있는 것은 아니다

**완료 확인(2026-07-29, 8081 실측)**: 게시판 4종(공지·FAQ·갤러리·회원전용) ×
익명/회원 시나리오 — 표·아코디언·카드 3종 목록 렌더 · 공지 배지·비밀글 자물쇠 ·
`read_auth=MEMBER` 게시판은 익명 302(로그인 유도) · **비밀글 본문 미노출 + 조회수 0 유지** ·
조회수 dedup(같은 브라우저 3회=1 / 다른 브라우저 1회=2) · 글쓰기 버튼이 `write_auth` 에
따라 노출/비노출되고 URL 직접 호출도 403 · 회원 글 작성 시 작성자가 **세션에서** 채워짐
(홍길동) · 댓글 작성 후 `comment_count` 동기 · 남의 글 수정 403 ·
**layout-002 로 전환한 사이트에서 같은 게시판 코드가 그대로 렌더**(전체펼침 GNB 안에
게시판 표) — 레이아웃 축 독립 확인. 검증 데이터 전량 제거.

> **검증 도구 함정 1건(앱 결함 아님)**: `mysql -N` 이 NULL 을 문자열 `"NULL"` 로 출력해
> 사이트 저장에 존재하지 않는 테마 ID 가 실렸고, 앱이 복합 FK 검증으로 정확히 거부했다.
> 스크립트에서 NULL 가능 컬럼은 `IFNULL(col,'')` 로 뽑을 것.

### P9-6 통합 게시판 + canManage (완료 2026-07-29)
- [x] `grouped_board_ids` CSV — 채워져 있으면 read-only 합본. 정규화(중복 제거·중첩 금지·
      상한 24)는 P9-1 에서 완료. 조회는 `IN` 절 분기이며 **대상이 0건이면 sentinel 로
      빈 결과**를 만든다(빈 IN 절이 조건 없는 전체 조회로 무너지지 않게)
- [x] **대상은 매번 다시 검증한다** — CSV 는 스냅샷이라 대상이 지워지거나 사용 중지돼도
      값이 남는다. 살아 있고 사용 중인 것만 합친다(닫아 둔 게시판의 글이 계속 보이면 안 된다)
- [x] `canManage(article, context)` — 합본 문맥이면 **작성자 본인이라도 false**.
      어느 게시판 정책으로 저장할지가 모호해지기 때문이며, 원 게시판으로 가면 평소대로
      수정된다(상세에 "원 게시판에서 보기" 링크를 둔다)
- [x] **Service 단 가드** — `save()` 진입에서 합본을 거절하고 `canWrite()` 도 false.
      원전은 UI 만 가려 URL 직접 호출이 열려 있었다(§14-8 자인). 화면·서비스 양쪽을 함께
- [x] 합본 상세는 **원 게시판의 `read_auth` 를 다시 묻는다** — 통합이 ALL 이라고 회원 전용
      게시판의 글이 열리면 통합 게시판이 우회 통로가 된다
- [x] 목록 표시 — 합본에서는 분류 대신 **출처 게시판**을 보여주고, 분류 필터는 감춘다
      (게시판마다 분류 체계가 달라 섞으면 무의미하다)

**완료 확인(2026-07-29, 8081 실측 + 단위 테스트 5종)**: 공개 2 + 회원전용 1 을 묶은 합본에서
— 목록에 세 게시판 글이 출처와 함께 표시(회원 로그인 시) · 글쓰기 버튼 없음 ·
합본 상세에 수정 버튼 없고 원 게시판 링크 있음 · **합본 쓰기 폼 403 + URL 직접 저장 403**
(합본 글 0건 유지) · 원 게시판에서는 수정 버튼 정상 · 대상 게시판을 사용 중지하자
합본에서 즉시 빠짐 · 중첩 지정 거부.

> **실측 결함 1건 발견·수정**: 합본 목록이 **회원 전용 게시판 글의 제목을 익명에게
> 노출**했다. 상세는 302 로 막혀 있었지만 목록에서 이미 새어 나간 뒤였다 — 상세만 막는
> 방어가 왜 부족한지 보여주는 사례. 대상을 추릴 때 `isReadable(master)` 로 걸러
> **볼 수 없는 게시판은 합본에 들어가지 않게** 고쳤다(익명=공개 2종, 회원=3종 실측).

### P9-7 게시판 검색 — LIKE 검색 (완료 2026-07-29)
- [x] 제목·본문·작성자 `LIKE` 검색(P9-2 매퍼 + P9-5 검색바). **색인 테이블·FULLTEXT·Nori
      미도입** — 원전의 색인 동기화 훅 5경로가 통째로 불필요해졌다
- [x] `#{}` 바인딩 유지 — 와일드카드는 SQL 안에서 `CONCAT` 으로 붙인다(`${}` 금지)
- [x] **`%`·`_` 이스케이프 — 게시판만이 아니라 전 도메인 26곳 일괄 적용.**
      같은 결함이 role·url-access·site·menu·content·file·template·theme·layout 에도
      그대로 있었다. 게시판만 고치면 "검색창에 `%` 를 치면 전체가 나오는" 화면이 남는다
- [x] 이스케이프 문자는 역슬래시가 아니라 **`|`** — `ESCAPE '\\'` 는 MariaDB 와
      PostgreSQL 이 문자열 리터럴을 다르게 해석해(standard_conforming_strings) 벤더마다
      값이 달라진다. 멀티 벤더가 전제라 어느 쪽에서도 특별하지 않은 문자를 골랐다
- [x] 화면용/검색용 분리 — `getKeyword()`(입력창 되돌리기) vs `getKeywordLike()`(매퍼).
      한 값으로 겸하면 검색창에 `50|%` 같은 흔적이 남는다.
      페이징을 타지 않는 메뉴 검색은 서비스에서 `LikeQuery.escape()` 직접 호출
- [x] 검색 대상은 `bbs_master_id` + `status='PUBLISHED'` 로 먼저 좁힌 뒤 LIKE

**완료 확인(2026-07-29, 8081 실측 + 단위 테스트 5종)**: 제목 검색 정확 매칭
(안내 2·수강 1·없는말 0) · **`%` 검색이 4건이 아니라 1건**('50% 감면'만) ·
`_` 검색도 1건('학사_일정'만) · `%감면` 0건 · 이스케이프 문자 `|` 자신도 0건 ·
관리 화면·게시판 목록 검색도 동일 규칙 · 입력창에 `value="50%"` 로 원본 그대로 복귀 ·
`EXPLAIN` 에서 `idx_article_bbs_status` 사용 확인(type=ref). 검증 데이터 전량 제거.

---

**P9 완료(2026-07-29)** — P9-0 라우팅 · P9-1 마스터/카테고리 · P9-2 게시글/첨부 ·
P9-3 댓글 · P9-4 좋아요/신고 · P9-5 사용자 화면 · P9-6 통합 게시판 · P9-7 검색.
**남은 것은 P9-2b(위지윅)** — Tiptap 번들러 추가와 CrossEditor 도입 가부가 결정 대기라
착수하지 않았다(§결정 대기 참조). 현재 본문은 textarea + 저장 시점 정화로 동작한다.

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

### P10-0 착수 전 점검 (완료 2026-07-29 — 실측)
- [x] **본인확인 값이 CI 가 아니라 DI 다.** gopcms5 는 `di`/`di_hash`/`parent_di`/
      `parent_di_hash` 를 쓰는데, 원전 문서는 **DI 를 DROP 하고 CI 로 갔다**. 정반대라
      그대로 옮기면 컬럼이 안 맞는다. 스키마(DI)를 정본으로 삼는다
      → **실측 확인**: `di`·`di_hash`·`parent_di`·`parent_di_hash` 존재, `ci` 계열 없음
- [x] `tb_member` 에만 있는 컬럼 반영 — `email_verified_yn`(원전의 미구현 항목 M-01 자리),
      `birth_year`, `captcha_required_yn`, `site_code`. 반대로 원전의 `group_ids` 는 없다
      → **실측 확인**: 4종 모두 존재, `group_ids` 없음
- [x] `tb_member_withdraw` 는 **이미 PII 를 담지 않는 설계** — `login_id_hash`·`di_hash`·
      탈퇴일시·사유·`retention_expire_at`·`legal_basis` 뿐이다. 탈퇴 원장은 그대로 쓰면 된다
- [x] `vw_user_login` 은 `delete_yn='N'` 만 거른다. 휴면은 **행 자체가 `tb_member_dormant`
      로 이관**되므로 뷰에서 자동으로 사라진다 — 별도 조건이 필요 없다(설계 확인 완료)
- [x] 인가 방식 재검토 — 원전은 "회원은 role 매핑이 없으니 `AUTHENTICATED + user_type`" 으로
      갔지만, gopcms5 는 `role_ids` CSV 가 살아 있고 `ROLE_REAL`(실명인증 회원)도 있다.
      P6-2 의 site-scoped ROLE 규칙(`/ai/member/**` ROLE_REAL)이 이미 그 전제로 깔려 있다
- [x] **메일 템플릿 10종 실재 확인** — 휴면 3단계·전환·복원·가입환영·탈퇴·비밀번호 2종.
      탈퇴 전환 예고 템플릿만 없다(P10-4 에서 신규 필요)

> **추가로 발견한 어긋남**: `join_type` CHECK 허용값은
> `EMAIL|KAKAO|NAVER|GOOGLE|APPLE|HOMEPAGE|MOBILE` 이다 — 원전 용어 `SELF` 로 넣었다가
> 제약 위반 500 을 맞았다. 홈페이지 직접 가입은 **`HOMEPAGE`**.

### P10-1 가입 (부분 완료 2026-07-29)
- [x] **PII 암호화 배선** (P6 잔여분 회수) — `@Encrypt` 어노테이션(문서용) +
      `PiiTypeHandler`(실제 암복호화) + `PiiCrypto`(정적 홀더 — TypeHandler 는 스프링 빈이
      아니라 주입을 못 받는다) + `PiiHash`(HMAC-SHA256, AES 마스터키와 **분리된 키**).
      **매퍼 XML 이 컬럼마다 typeHandler 를 명시**해야 걸린다 — 어노테이션은 MyBatis 가 보지
      않는다. 빠뜨리면 평문으로 저장되므로 리뷰 포인트
- [x] 이행기 정책 — 읽을 때 `{AG}` 가 없으면 평문으로 간주(기존 dev 시드가 평문이라
      화면이 깨지지 않게). 쓸 때 이미 암호문이면 이중 암호화하지 않는다
- [x] 가입 폼·컨트롤러·서비스 — `/{siteCode}/member/join`. 폼 DTO 를 따로 둔다
      (`MemberDto` 를 그대로 쓰면 상태·역할·잠금 카운트처럼 **서버가 정하는 값**이 폼에
      실려 온다)
- [x] 중복 차단 — `login_id`(사이트 스코프) · `email_hash`(암호문엔 `=` 를 못 건다).
      해시는 소문자·trim 정규화 후 산출 — 안 하면 대소문자만 다른 이메일이 통과한다
- [x] 약관 동의 이력 INSERT 누적 — 필수 2종 + 선택 3종을 **모두** 남긴다.
      "동의하지 않음"도 기록이어야 나중에 증명할 수 있다. 버전·IP·UA 동반(UA 는 500자 절단)
- [x] 신규 회원은 `ROLE_MEMBER` 만 — `ROLE_REAL` 은 본인확인을 거쳐야 붙는다
- [x] V914 URL 규칙 — `/*/member/join` PERMIT_ALL(priority 53). 회원 영역 잠금(55·60)보다
      **앞**이어야 한다. 뒤면 가입하려면 로그인해야 하고 로그인하려면 가입해야 하는 상태가 된다
- [x] **5단계 마법사 분리**(유형선택 → 약관 → 본인인증 → 정보입력 → 완료) — 단계 상태는
      전부 세션(`JoinSession`)이다. 동의·인증 결과를 hidden 으로 넘기면 사용자가 값을 바꿔
      인증을 건너뛸 수 있다. 각 단계가 앞 단계 완료를 확인하고 아니면 되돌린다
- [x] ADULT / CHILD(14세 미만) 분기 — CHILD 는 **법정대리인**이 인증하고 결과가
      `parent_name`/`parent_di`/`parent_di_hash` 로 간다(아이 본인 정보는 직접 입력)
- [x] 중복가입 차단 — `countByDiHash`(사이트 스코프). CHILD 는 검사하지 않는다:
      한 법정대리인이 자녀 여럿을 가입시키므로 `parent_di` 중복이 정상이다
- [x] 인증을 마치면 `ROLE_MEMBER` + `ROLE_REAL`, 아니면 `ROLE_MEMBER` 만
- [x] V919 URL 규칙 — `/*/member/join/**`. V914 는 `/*/member/join` 하나만 열어 뒀고
      AntPathMatcher 에서 그 패턴은 하위 경로를 포함하지 않는다(2단계에서 튕긴다)

**완료 확인(2026-07-29, 8081 실측)**: 가입 302 → 로그인 화면 ·
**DB 원본이 `{AG}` 암호문**(이름·이메일·전화·생년월일), `email_hash` 는 별도 저장 ·
거부 5종(아이디 중복 / **대소문자만 다른 이메일 중복** / 약한 비밀번호 / 대문자 아이디 /
잘못된 이메일) · 필수 동의 누락 거부 · 동의 이력 5행(선택 미동의 `N` 포함) ·
가입 계정으로 로그인 성공. 검증 데이터 전량 제거.

> **실측 결함 1건 발견·수정**: 로그인 뷰(`vw_user_login`)의 `display_name` 이
> **암호문 그대로 화면에 노출**됐다. 뷰는 TypeHandler 를 타지 않는데 `resultType` 자동
> 매핑이라 컬럼별 지정을 못 했던 것 — `resultMap` 으로 바꿔 그 컬럼만 복호화했다.
> 기존 시드(user1)는 평문이라 정상으로 보였고, **새로 가입한 회원에서만 드러났다**.

#### P10-1b 본인인증(NICE CheckPlus) + 소셜 로그인(OAuth2) — 2026-07-30
- [x] NICE 모듈 이식(`primary/identity/**`, 원전 `gopcms2026`) — 규약에 맞춰
      `AbstractCmsService` **간접** 상속으로 교정(원전은 `EgovAbstractServiceImpl` 직접 상속).
      **DI 만 읽고 CI 는 복호화하지 않는다**
- [x] 콜백은 **사이트 무관 고정 경로**(`/member/identity/nice/**`) — NICE 콘솔에 등록한
      문자열 하나로만 응답이 돌아온다. 사이트별 경로를 두면 사이트 수만큼 계약이 필요하다
- [x] 위조 방어 — 세션의 요청번호(`REQ_SEQ`)와 콜백 응답의 `REQ_SEQ` 대조. 실패한 번호는
      즉시 폐기. 외부 도메인이 POST 하므로 **이 두 경로만** CSRF 예외
- [x] 팝업 배관 — 우리 페이지가 NICE 로 자동 POST → 콜백 결과 페이지가 부모창에 신호.
      COOP(`same-origin`)로 `window.opener` 가 끊기므로 localStorage·BroadcastChannel
      폴백 3중(`/js/nice-auth.js`, 인라인 스크립트 금지 규약 준수)
- [x] **CSP `form-action` 확장** — 기본 정책이 `'self'` 뿐이라 NICE 도메인 전송이
      조용히 차단됐다(콘솔에만 위반이 찍힌다). `gopcms.security.csp-form-action-extra`
- [x] `gopcms.nice.enabled=false` 면 STEP 3 이 통째로 빠진다(계약 전 개발용).
      **운영에서 끄면 실명 확인 없이 가입이 열린다** — 기본값은 켜짐
- [x] 소셜 로그인 — spring-security-oauth2-client 없이 `RestClient` 직접 구현.
      인가가 DB 단일 원천이라 자체 필터 체인을 끼우는 스타터보다 경계가 분명하다
- [x] `state` 세션 보관 + **1회 소비** — 없으면 공격자가 만든 인가 코드를 피해자
      브라우저에 흘려 남의 소셜 계정을 피해자 계정에 붙일 수 있다
- [x] 신규 소셜 사용자는 **회원을 바로 만들지 않고 가입 마법사로** 보낸다 — 약관 동의와
      본인인증은 소셜로 왔다고 건너뛸 절차가 아니다. 연결(`tb_member_oauth`)은 가입
      트랜잭션 안에서 만든다(부분 성공하면 "소셜로 가입했는데 소셜 로그인이 안 되는" 상태)
- [x] 자격이 없는 provider 는 로그인 화면에 **버튼이 아예 나오지 않는다**
- [x] 로그인 화면 재정비 — 소셜 버튼 + 회원가입·아이디 찾기·비밀번호 찾기·휴면 복원 링크

**완료 확인(2026-07-30, 8081 실측)**: STEP1~5 지표 렌더 · 단계 건너뛰기 차단(동의 전
`/form`·`/verify` 진입 → STEP1 반송) · 필수 동의 누락 거부 · 미인증 `/form` 직행 →
`/verify` 반송 · CSP 헤더에 `form-action 'self' https://nice.checkplus.co.kr` ·
NICE 자격 없을 때 버튼 미노출 + 안내 문구 · `nice.enabled=false` 로 STEP3 제외(지표가
1·2·4·5 로 축소) 후 가입 성공, PII `{AG}` 암호화 저장, 동의 5행, `role_ids` = ROLE_MEMBER 만 ·
소셜 버튼은 자격 주입한 naver 만 노출 · 인가 URL 조립(scope percent-encoding, state 생성) ·
state 불일치 거절 · 사용자 취소 분기. 단위 테스트 8건 추가(총 83건 통과). 검증 데이터 전량 제거.

> **실측 못 한 것**: NICE 계약 자격(`GOPCMS_NICE_SITE_CODE`/`_SITE_PASSWORD`)과 소셜
> provider 자격이 없어 **실제 인증 왕복은 검증하지 못했다**. 암호화 호출·팝업 배관·
> 토큰 교환은 자격이 들어온 뒤 재검증이 필요하다.

### P10-2 로그인·인증 (부분 완료 2026-07-29)
- [x] 로그인 경로 분리 — 관리자 `/adm/login`(IP 게이트+2FA) / 회원 `/login?siteCode=`.
      **원전의 `/member/login` 대신 사용자 확정 계약을 따른다**(P6-1). Provider 2종이
      `user_type` 으로 갈려 교차 로그인이 불가능하다
- [x] 실패 5회 잠금(30분) · CAPTCHA(`captcha_required_yn`) — P6-1·P6-3 에서 구현됨
- [x] **enumeration 방지** — 실패 문구는 언제나 하나(`GENERIC_FAILURE`)이고 사유는 이력에만.
      추가로 `LoginTiming.burn()` — 미존재 계정에도 더미 해시로 BCrypt 를 1회 돌린다.
      문구를 맞춰도 **응답 시간만으로** 계정 존재가 새어 나가고, 그렇게 모은 목록이
      크리덴셜 스터핑의 입력이 된다
- [x] **Bucket4j 이중 키 레이트리밋** — IP(30회/5분) + 아이디(10회/5분).
      한 축만으로는 부족하다: IP 만 걸면 봇넷이 IP 를 바꿔 한 계정을 두드리는 것을 놓치고,
      아이디만 걸면 남의 아이디를 일부러 잠그는 데 쓸 수 있다. **인증 체인 앞 필터**에서
      끊는다 — Provider 안에서 세면 이미 DB 조회를 한 뒤라 무차별 대입의 부하를 못 막는다
- [x] 아이디 찾기 — 이름+이메일로 조회, **앞 3자만 노출**(`fin******`). 찾았을 때와
      못 찾았을 때가 **같은 화면·같은 형태**로 돌아온다(이메일 가입 여부 확인 도구가 되지 않게).
      이메일은 암호문이라 해시로 찾고, 이름은 복호화 후 자바에서 대조
- [x] **메일 발송 인프라** — `MailService.sendAsync(templateCode, to, model)` 하나가 대외 계약.
      본문은 `tb_mail_template` 에서 읽어 Thymeleaf 로 렌더링한다(문구는 운영 중 자주 바뀌고
      그때마다 배포할 수 없다). **전용 엔진**을 쓴다 — 화면용 엔진은 `classpath:/templates/`
      리졸버가 달려 있어 DB 문자열을 넘길 수 없다
- [x] 발송은 **비동기 + `NOT_SUPPORTED`** — SMTP 는 느리고 실패할 수 있는데 그것 때문에
      가입이나 비밀번호 재설정이 롤백되면 안 된다. 실패는 삼키되 반드시 로그로 남긴다
- [x] `gopcms.mail.enabled=false` 기본 — SMTP 계정 없이 기동하고, 개발 중 실메일 발송
      사고를 막는다. 꺼져 있으면 로그만 남기며 **본문은 남기지 않는다**(임시 비밀번호 포함)
- [x] `@Async` 실행기는 **가상 스레드** — 메일은 대부분 대기 시간이라 풀 크기를 정할 이유가
      없다. 잘못 잡은 풀은 발송이 밀리거나 메모리를 먹는다
- [x] 비밀번호 찾기 — 임시 비밀번호 12자(혼동 글자 `0/O`·`1/l/I` 제외, 특수문자·숫자 보장).
      **만료 시각을 과거로** 두어 로그인은 되지만 곧바로 변경으로 유도한다(P6-3 `FAIL_EXPIRED`).
      실패 카운트·잠금도 함께 푼다 — 새 비밀번호를 받았는데 이전 실패로 잠겨 있으면
      여전히 못 들어온다. 메일로만 전달하고 화면에는 띄우지 않는다
- [x] V916 URL 규칙 — `/*/member/find-password` PERMIT_ALL(54)

**메일·비밀번호 찾기 완료 확인(2026-07-29, 8081 실측)**: 템플릿 렌더링 성공
(제목 `[GoPCMS] 임시 비밀번호 발송` 치환) · 미발송 모드 로그 · 비밀번호 해시 교체 ·
`password_expire_at` 과거 · 실패 카운트·잠금 해제 · **임시 비밀번호로 로그인 시
`FAIL_EXPIRED` 로 거부**(변경 강제) · 미존재 계정도 같은 안내. 검증 데이터 전량 제거.

> **실측 결함 3건 발견·수정**: ① 매퍼 XML 경로가 규약(`**/mapper/*_{vendor}.xml`)을
> 벗어나 `Invalid bound statement` — 수직 슬라이스(`mail/{mapper,dto,service}`)로 재배치.
> ② 그 뒤에도 같은 오류 — XML `namespace` 가 옛 패키지 그대로였다(경로만 옮기면 안 된다).
> ③ 템플릿이 `#temporals.format(sentAt, …)` 를 호출하는데 **이미 포맷한 문자열**을 넘겨
> 파싱이 깨졌다 — `LocalDateTime` 을 그대로 넘긴다.

**완료 확인(2026-07-29, 8081 실측)**: 타이밍 차이 **2.6배 → 1.15배**
(CSRF 없이 측정하면 인증까지 가지 않아 무의미 — 세션·토큰을 갖춰 재측정) ·
아이디당 11회째 차단(`?error&throttled`) + 서버 WARN 로그 ·
아이디 찾기 정상(대소문자 다른 이메일로도 조회, `fin******` 마스킹) ·
미존재·이름 불일치 모두 동일 응답. 검증 데이터 전량 제거.

> **규약 위반 1건 자체 교정**: 아이디 찾기 URL 규칙을 이미 적용된 V914 에 이어 붙였다가
> checksum 이 깨지는 것을 발견하고 **V915 로 전진**했다(flyway-migration.md).

### P10-3 마이페이지 (부분 완료 2026-07-29)
- [x] **step-up 재인증**(TTL 5분, 5회 실패 시 흔적 파기) 후에만 개인정보 열람·수정.
      로그인 세션만으로 열면 자리를 비운 사이나 세션 탈취 시 그대로 열린다.
      **저장 시점에 다시 확인**한다 — 폼을 여는 사이 5분이 지났을 수 있다
- [x] 본인인증 근간값 수정 불가 — `MemberProfileForm` 에 **아예 넣지 않았고**,
      UPDATE 문의 SET 절에도 없다. 컨트롤러가 걸러 주기를 기대하면 언젠가 빠뜨린다.
      실측: `loginId`·`birthDate`·`gender`·`di` 를 함께 POST 해도 값이 변하지 않는다
- [x] 이메일 변경 시 해시 재산출 + **인증 상태 리셋**(`email_verified_yn='N'`) —
      이전 인증은 다른 주소에 대한 것이다. 중복 검사는 자기 자신 제외
- [x] 동의 변경은 **바뀐 항목만** 이력에 쌓는다. 저장할 때마다 전부 쌓으면
      "언제 껐는가" 를 찾으려고 보는 표에서 그걸 알 수 없게 된다
- [x] 비밀번호 변경 — P6-3 의 `PasswordService`(MEMBER 분기)를 그대로 쓴다.
      변경 후 **세션 무효화 + 재로그인** 강제
- [x] V917 인가 규칙 — 마이페이지는 `ROLE_REAL` 규칙(55)보다 **앞**에서 인증만 요구(51·52)
- [x] 셀프 탈퇴 → P10-4 의 탈퇴 처리와 같은 경로(`MemberLifecycleService.withdraw`)를 탄다.
      (P10-4 와 함께 구현·실측됐는데 이 체크박스만 남아 있었다 — 2026-07-30 정리)

**완료 확인(2026-07-29, 8081 실측)**: 재인증 전 마이페이지 → `/verify` 리다이렉트 ·
틀린 비밀번호 거부 · 통과 후 200(이름·전화 복호화 표시) · **근간값 주입 시도 무시**
(loginId·birthYear·gender 불변) · 이메일 암호문 저장 + 인증 상태 리셋 ·
동의 변경 이력 적재 · 비밀번호 변경 후 세션 무효화(302). 검증 데이터 전량 제거.

> **실측 결함 1건 발견·수정**: 갓 가입한 회원이 **자기 마이페이지에 못 들어갔다**(403).
> 사이트 스코프 규칙(`/ai/member/**` ROLE_REAL, P6-2 실증용 시드)이 먼저 걸렸는데
> 신규 회원은 실명인증 전이라 그 역할이 없다 — 마이페이지는 실명인증과 무관해야 하므로
> 더 앞선 우선순위로 인증만 요구하게 열었다(V917).

### P10-4 생명주기 스케줄러 (부분 완료 2026-07-30)

```
ACTIVE ──[마지막 로그인 +1년]──▶ 휴면(tb_member_dormant)
휴면   ──[휴면 전환 +1년]──────▶ 탈퇴(tb_member_withdraw + tb_member PII 전부 NULL)
탈퇴   ──[탈퇴 +1년]───────────▶ 완전 삭제(hard delete)
```

- [x] **3개 잡 분리** — 휴면전환 / 탈퇴전환 / 완전삭제 + 사전안내. 하나로 묶지 않는 이유는
      셋의 위험도가 다르기 때문이다(휴면은 되돌릴 수 있고, 탈퇴는 PII 가 사라지며,
      완전삭제는 행이 없어진다). 따로 켜고 끌 수 있어야 한다. ShedLock 적용
- [x] **단건 `REQUIRES_NEW`** — `MemberLifecycleWorker` 를 **별도 빈**으로 분리했다.
      같은 빈에 두면 자기호출로 프록시를 우회해 `@Transactional` 이 통째로 무시되고,
      한 건 실패가 앞서 처리한 수백 건을 롤백시킨다(CLAUDE.md 트랜잭션 함정)
- [x] 기준 시각 `Asia/Seoul`. **`last_login_at` 이 NULL 이면 `created_at`** —
      빠뜨리면 가입 후 미로그인 계정만 영원히 늙지 않는다
- [x] **기본이 dry-run** — 스케줄은 돌지만 대상만 로그에 남는다. 배치를 처음 켜는 순간
      오래된 계정이 한꺼번에 사라지는 것이 가장 흔한 사고다(P8-5 와 같은 방식).
      1회 처리 상한 200 — 되돌릴 수 없는 배치에는 상한이 있어야 피해가 한 배치로 제한된다
- [x] 사전 안내 3단(30D·7D·1D) — 기존 템플릿 3종 사용. **안내 잡이 전환 잡보다 먼저**
      돈다(같은 날 함께 돌면 안내받은 그날 휴면이 되는 계정이 생긴다).
      이력을 먼저 적고 메일을 건다 — 발송이 비동기라 순서가 바뀌면 중복 판정을 못 한다.
      `(member_id, stage)` UNIQUE 가 중복 발송을 막고, 로그인하면 이력을 지워 사이클을 리셋
- [x] **탈퇴 순서 고정** — ① 원장 INSERT → ② PII NULL, 한 트랜잭션.
      PII 삭제는 되돌릴 수 없으므로 원장을 먼저 남기지 않으면 사고 시 복구·소명 근거가 없다
- [x] **셀프 탈퇴와 배치가 같은 경로**(`MemberLifecycleService.withdraw`) — 경로가 둘이면
      한쪽만 고쳐져 정책이 갈린다. 유형만 다르게 넘긴다(USER_REQUEST / DORMANT_EXPIRED)
- [x] 셀프 탈퇴 화면 — step-up 재인증 필수 + **확인 문구 직접 입력**(오클릭 방지)
- [x] 완전 삭제는 자식 행부터 순서대로(consent → password_history → oauth →
      dormant_notice → dormant → member → 원장). 순서가 틀리면 제약 위반으로 실패하는데,
      조용히 일부만 지워지는 것보다 낫다
- [x] `logging_db` 는 건드리지 않는다 — 크로스 DB 이고 보존주기가 별개다(§P10-7)
- [x] **탈퇴 전환 통지 템플릿 신규**(V921 `ACCOUNT_WITHDRAW_NOTICE`) — 그동안
      `ACCOUNT_DORMANT_TRANSFERRED`(휴면 전환 완료)를 재사용했는데, 그 문구는
      "복원 링크로 다시 쓸 수 있다" 고 안내한다. 탈퇴는 되돌릴 수 없으므로
      **사실과 다른 안내가 나가고 있었다**. 새 문구는 ①무엇이 파기됐는지 ②무엇이 남는지
      ③복원이 안 된다는 것을 분명히 한다
- [x] **게시글 작성자 익명화** — 탈퇴 시 `tb_bbs_article`·`tb_bbs_comment` 의
      `writer_name` 을 `탈퇴한 회원`(설정 `gopcms.member.anonymous-writer-name`)으로 바꾼다.
      확정 정책("회원 PII 즉시 파기")을 따르면 게시판에 실명이 남는 것은 앞뒤가 맞지 않는다.
      · **글은 지우지 않는다** — 대화의 맥락이 통째로 사라지면 남은 사람들의 글이 읽히지
        않는다. 지워야 하는 것은 "누가 썼는지" 이지 "무엇을 썼는지" 가 아니다
      · **`writer_user_id` 는 남긴다** — 회원 행이 사라진 뒤에는 그 값으로 사람을 되짚을
        수 없어 식별정보가 아니고, 같은 작성자의 글을 묶는 운영 기능이 거기 걸려 있다
      · 탈퇴 트랜잭션 안에서 처리한다(별도 배치로 미루면 그 사이 실명이 노출된 채 남고,
        배치가 실패하면 영영 남는다)

**완료 확인(2026-07-30, 8081 실측)**: 셀프 탈퇴 — 확인 문구 불일치 거부 → 정확히 입력 시
302, **원장에 해시만**(login_id_hash·withdraw_status=USER_REQUEST·보존기한 2029-07-30) ·
`tb_member` 의 이름·이메일·전화 **모두 NULL**, status=SUSPENDED, delete_yn=Y ·
탈퇴 후 로그인 차단. 배치 — dry-run 이 대상 1건을 집어내고 **DB 를 전혀 건드리지 않음**,
`dry-run=false` 로 바꾸자 휴면 이관 완료(`tb_member` 0건 → `tb_member_dormant` 1건,
`vw_user_login` 에서도 사라짐), ShedLock 락 2건 생성. 시드 계정 복구 완료.

> **실측 결함 3건 발견·수정**: ① `withdraw_status` 는 상태가 아니라 **탈퇴 유형**이었다
> (CHECK: `USER_REQUEST`/`ADMIN_FORCE`/`DORMANT_EXPIRED`) — `'COMPLETED'` 로 넣어 제약 위반.
> 경로별로 유형을 넘기도록 고쳤다. ② `tb_member.password` 는 **NOT NULL** 이라 PII NULL
> 처리에서 비울 수 없었다 — BCrypt 형식이 아닌 값(`'-'`)을 넣어 어떤 입력과도 매칭되지
> 않게 했다. ③ 검증 스크립트에서 한글 확인 문구가 셸 코드페이지에 변형돼 거부됐다
> (앱 결함 아님 — P7-4 와 같은 함정. UTF-8 파일로 전달해 해결).

### P10-7 보존기간 정책 (완료 2026-07-30 — 정책은 2026-07-29 사용자 확정)

**원칙: 회원 개인정보는 최소로, 이력은 길게.** 보유한 개인정보가 적을수록 유출 시
피해가 작다. 반대로 "누가 언제 무엇을 했는가" 는 길게 남겨야 사고 때 추적이 된다.

| 대상 | 보존 | 비고 |
|---|---|---|
| **회원 PII**(`tb_member` 본체) | **즉시 파기** | 탈퇴 시 PII 컬럼 전부 NULL → 1년 후 행 완전 삭제 |
| 개인정보 접근·파기 **이력** | **5년** | `log_privacy_access` · `log_pii_purge` — 파기했다는 사실 자체의 증빙 |
| 탈퇴 원장 `tb_member_withdraw` | **36개월** | `retention_expire_at` 컬럼이 이미 있다 — 계산해 채우고 배치는 이 값만 본다 |
| 나머지 로그 `log_*` | **36개월** | `log_access` · `log_audit` · `log_error` · `log_security` · `log_file_download` |
| 통계 `stat_*` | **영구** | 개인 식별 정보가 없는 집계값. 36개월로 묶으면 연도 비교가 영영 불가능해진다 |

- [x] 보존기간을 **설정 한 곳**(`RetentionProperties` ← `gopcms.retention.*`)에 모았다.
      흩어져 있던 `@Value("${...withdraw-months}")` 도 흡수 — 코드에 남은 기본값이
      곧 두 번째 진실이 된다
- [x] 파기 배치는 **대상 테이블별 보존기간을 설정에서 읽는다** — `RetentionTarget` 등록부가
      테이블 → 보존기간 키를 가리키고, 기간 값은 설정에만 있다(코드에 박히면 정책 변경에
      빌드가 필요하다). 요약 로그에 적용된 개월수를 함께 찍는다
- [x] **`log_pii_purge` 는 배치가 손대지 않는다** — 등록부에 `purgeable=false` + 제외
      사유로 **명시**한다(목록에서 빼면 다음 사람이 누락으로 보고 되살린다).
      단위 테스트로 고정
- [x] 로그 파기 배치 `LogRetentionJob` — ShedLock, **건수 상한 + dry-run + 요약 로그**
- [x] `tb_login_history`(primary) 는 등록부의 **별도 목록**에 둔다 — 같은 36개월이지만
      다른 DataSource·TxManager 를 탄다는 사실이 코드에서 보여야 크로스 DB 트랜잭션을
      실수로 묶지 않는다. 테이블 단위 독립 트랜잭션(워커 빈 분리 — 자기호출이면
      `@Transactional` 이 무시된다)
- [x] **안전장치** — dry-run 기본 켜짐 · 테이블당 1회 상한 · 상한에 걸리면 요약 로그에
      "남았다" 를 적는다(안 적으면 다 지워진 줄 안다) · 한 테이블 실패가 나머지를 막지 않음
- [x] **`${}` 없이 테이블명 분기** — 테이블명은 `#{}` 로 바인딩할 수 없고 `${}` 는 금지
      규약이라, 매퍼 XML 이 `<choose>` 로 미리 적어 둔 문장 중 하나를 고른다.
      파라미터는 분기 선택에만 쓰이고 SQL 문자열이 되지 않는다(+ 서비스가 등록부로 재검증)

#### P10-7b `log_pii_purge` 기록 배선 — 2026-07-30
- [x] **테이블만 있고 아무도 쓰지 않던 상태였다**(실측). 파기 증빙을 5년 보존한다는
      정책의 전제가 비어 있었다 — 탈퇴·완전삭제 두 지점에서 적재하도록 배선
- [x] 회원 ID 는 **해시만**(`PiiHash`) — 평문을 적으면 파기 이력 자체가
      "이 사람이 우리 회원이었다" 는 개인정보가 된다
- [x] **이력을 먼저, 파기를 나중에** — 크로스 DB(logging_db)라 한 트랜잭션으로 묶을 수
      없다(`REQUIRES_NEW`). 순서가 유일한 안전장치다: 뒤에 남기면 파기는 됐는데 흔적이
      없는 상태가 생긴다
- [x] `table_list` 에 실제로 손댄 테이블을 남긴다 — 파기 **범위**의 증빙

**완료 확인(2026-07-30, 8081 실측)**: 경계값 시드(40개월/30개월/70개월)로 검증 ·
dry-run 에서 대상 8건 보고 + 삭제 0건 · **보존기간 구분 실증** — `log_privacy_access`
40개월 행은 살아남고(5년 이내) 70개월 행만 대상, 같은 40개월이라도 `log_access` 는 대상 ·
`log_pii_purge` 제외 + 사유 로그 · `stat_*` 무손상 · `tb_login_history`(primary) 40개월 삭제 /
30개월 보존 · **상한 1로 두 회차에 나눠 파기**(1회차 "상한에 걸려 남음" 안내 → 2회차 완료) ·
강제 탈퇴 시 `WITHDRAW` 이력, 완전 삭제 시 `RETENTION_EXPIRED` 이력 적재 —
**두 기록의 `user_id_hash` 가 동일**(같은 사람 추적은 되되 역추적 불가) ·
평문 `MBR_` 문자열 0건. 단위 테스트 5건 추가(총 95건 통과). 검증 데이터 전량 정리.

> **남은 항목**: `log_pii_purge` 자체의 5년 경과분 파기는 이 배치가 하지 않는다.
> 정책상 기간은 있으나 "자기 기록을 같은 배치가 지우는" 구조를 만들지 않기로 했으므로,
> 필요해지는 시점(5년 뒤)에 별도 절차로 다룬다.

### P10-5 휴면 복원 — 이메일 인증번호 (부분 완료 2026-07-30)
- [x] 휴면 계정은 `vw_user_login` 에 없어 평범한 로그인은 그냥 실패한다.
      **아이디·비밀번호가 모두 맞을 때만** 휴면으로 판정한다 —
      아이디만으로 "휴면입니다" 를 알려 주면 그 자체가 계정 존재 확인 도구가 된다
- [x] 복원 수단 ② **이메일 인증번호** — 6자리, TTL 5분, 시도 5회, 재발송 쿨다운 60초.
      **평문 저장 금지**(HMAC 해시만, PiiHash 와 같은 키). ※ 관리자 2FA(TOTP)와 다른 것 —
      TOTP 는 앱이 시간으로 만들지만 이 OTP 는 서버가 만들어 메일로 보내고 한 번 쓰면 끝난다
- [x] **시도 횟수는 행에 둔다**(`tb_member_otp.attempt_count`). 세션에 두면 세션을 새로 잡아
      무제한 대입할 수 있다. 재발급 시 이전 코드를 즉시 만료 — 살아 있는 코드가 둘이면
      시도 제한이 무의미해진다
- [x] 대상 회원은 **세션이 들고 있는다**. 폼에 member_id 를 실으면 남의 계정으로 코드를
      발송시킬 수 있다
- [x] V10 `tb_member_otp` 신설 + 접두어 `MOT` 등록(conventions §2). `purpose` 로 용도를 나눠
      휴면 복원 코드로 이메일 인증을 통과하는 교차 사용을 막는다
- [x] 성공 시 `tb_member` 복원(status=ACTIVE, delete_yn='N', last_login_at 갱신) +
      `tb_member_dormant.restored_at` 표시 + 안내 이력 삭제(남겨 두면 다시 휴면이 될 때
      안내가 발송되지 않는다 — `(member_id, stage)` UNIQUE)
- [x] 실패 응답은 만료인지 오답인지 구분하지 않는다 — 알려 주면 대입에 도움이 된다
- [x] V918 URL 규칙 — `/*/member/dormant/**` PERMIT_ALL(50). 로그인할 수 없는 상태에서
      쓰는 화면이라 회원 영역 잠금보다 앞서 열려야 한다
- [x] 복원 수단 ① **실명인증(NICE)** — `restoreByIdentity(siteId, di)` 로 붙였다.
      DI 해시로 휴면 계정을 찾아 <b>같은 `restore()`</b> 를 탄다(수단이 둘, 처리는 하나).
      **아이디·비밀번호를 묻지 않는다** — 비밀번호를 잊어 못 들어오는 것이 휴면의 흔한
      사정이라, 그걸 요구하면 복원 수단이 하나 더 필요해진다.
      실명인증 없이 가입한 계정(`di_hash` 없음)은 이 경로를 쓸 수 없고 OTP 경로로 간다
- [x] 인증번호 발송 레이트리밋 — `OtpRateLimiter`(IP 축, 기본 10회/60분).
      계정별 쿨다운은 "한 계정에 연달아" 만 막는다. **아이디를 바꿔 가며 요청하면
      쿨다운에 한 번도 걸리지 않으면서 대량 발송이 가능**했다 — 우리 서버가 스팸
      발신자가 되고 메일 평판이 떨어지면 정상 메일까지 막힌다.
      휴면 복원과 **비밀번호 찾기** 두 경로에 함께 건다(관리자 발급은 제외 — 인증을
      통과한 운영자의 업무이고 여기서 막으면 정작 필요할 때 못 쓴다)

**완료 확인(2026-07-30, 8081 실측)**: 휴면 계정 로그인 → 실패 ·
잘못된 비밀번호로는 코드가 발급되지 않음(OTP 0건) · 올바른 비밀번호 → 코드 1건 발급
(**DB 에는 해시만**) · 재발송 쿨다운 문구 · 틀린 코드 5회 시 `attempt_count=5` 이고
복원되지 않음 · 정상 코드 → 복원(302) → `tb_member` 활성 1건 ·
`restored_at`·`verified_at` 기록 · `vw_user_login` 복귀 · **복원 후 로그인 성공**.
검증 데이터 전량 제거.

> **실측 결함 1건 발견·수정 — P10-4 로 소급**: **가입 절차를 거친 회원은 휴면 전환이
> 실패했다.** 동의 이력·비밀번호 이력이 FK 로 `tb_member` 를 참조하는데 그 둘은 증빙이라
> 함께 지울 수 없어, 하드 삭제가 제약에 막힌 것이다. P10-4 실측에서는 시드 계정(user1)에
> 동의 이력이 없어 드러나지 않았다 — **실제 가입 흐름을 거친 계정으로 시험해야 나오는
> 결함**이었다. 휴면 전환을 소프트 삭제(`delete_yn='Y'`)로 바꿔 해결했다.
> `vw_user_login` 이 `delete_yn` 을 거르므로 로그인 차단이라는 목적은 그대로이고,
> 복원도 INSERT 가 아니라 UPDATE 한 번으로 단순해졌다.

### P10-6 관리자 회원 관리 (완료 2026-07-30)
- [x] `MemberAdmController` `@RequestMapping("/adm/member")` — 목록·상세·상태변경·
      비밀번호 초기화·잠금해제·강제탈퇴. **관리자는 회원을 생성하지 않는다**(정책 —
      본인 동의와 본인확인 없이 만들어진 계정이 생긴다. 인터페이스에 create 가 없다)
- [x] 검색은 평문 컬럼(`login_id`·`nickname`)만 LIKE, 이메일·전화는 해시 정확 매칭 —
      암호화 컬럼에 LIKE 를 걸 수 없다는 제약이 화면 설계를 규정한다(안내 문구까지)
- [x] 해시 값은 **표시값과 분리**(`emailHash`/`phoneHash`) — 입력 자리에 덮어쓰면
      검색창에 64자 해시가 되돌아와 관리자가 방금 친 값을 잃는다
- [x] 목록·상세 개인정보 **마스킹 기본**(`common/util/Mask` 단일 경로 — 뷰에서 하면
      CSV 처럼 뷰를 안 타는 출구에서 빠뜨린다). 단위 테스트 7건으로 고정
- [x] 원본 열람은 **사유 5자 이상 + ROLE_PRIVACY** — 이 역할은 계층 밖 독립 역할이라
      ROLE_ADMIN 이어도 상속되지 않는다(V907). 마스킹 해제는 상세와 같은 URL 이라
      URL 규칙으로 가를 수 없어 컨트롤러가 직접 판정한다
- [x] **DI 는 해제해도 표시하지 않는다** — 전 기관 공통 식별자라 화면에 띄울 업무상
      이유가 없다. 있고 없음만
- [x] CSV 내려받기 — 사유 필수 + 건수 상한(기본 5000) + **마스킹 유지**. 셋이 함께
      있어야 의미가 있다. 상한에 걸리면 파일 끝에 잘렸다고 적는다(모르면 전부라고 믿는다).
      CSV 인젝션 방어(`=`·`+`·`-`·`@` 로 시작하는 값은 따옴표 고정), UTF-8 BOM
- [x] URL 규칙 V920 — `/adm/member/export` 는 ROLE_PRIVACY 전용(priority 15,
      `/adm/**` ROLE_ADMIN 규칙 20 보다 **앞**이어야 제한이 산다)
- [x] **`log_privacy_access` 배선**(테이블만 있고 미사용이었다) — 목록 조회(SEARCH)·
      상세(READ)·마스킹 해제(DECRYPT)·내려받기(EXPORT)·처리(UPDATE/DELETE)와
      **거부(DENIED)까지** 적재. `REQUIRES_NEW` 격리 — 대상 업무가 롤백돼도
      "보려고 시도했다" 는 사실은 남아야 한다. 상세 화면에 해당 회원의 취급 내역 표시
- [x] 휴면·탈퇴 현황 조회 + 배치 수동 실행(운영 복구 경로) — **dry-run 설정이 그대로
      적용된다**. 손으로 돌릴 때만 진짜로 지워지는 동작은 사고를 부른다
- [x] 임시 비밀번호는 **본인 요청과 같은 발급 경로**를 탄다(`issue()` 공통) — 경로가
      둘이면 "관리자가 낸 것만 만료가 안 걸리는" 식으로 갈린다. 값은 회원 메일로만 가고
      **관리자 화면에는 표시하지 않는다**(계정을 되찾아 주는 것과 가져가는 것은 다르다)
- [x] 강제 탈퇴는 셀프 탈퇴·배치와 같은 `MemberLifecycleService.withdraw()` 경로

**완료 확인(2026-07-30, 8081 실측)**: 암호화된 PII 를 가진 회원을 가입 절차로 만들어 검증 ·
목록·상세에서 `{AG}` 암호문 0건 + 마스킹 표시(`김*리` / `ad*******@example.com` /
`010-****-5432` / `1985-**-**`) · 이메일 해시 검색이 **대소문자 달라도 매칭**
(`AdmTest01@Example.COM` 등록 → `admtest01@example.com` 로 검색 성공), 없는 주소는 0건,
검색창에는 입력값 그대로 · ROLE_PRIVACY 없이 열람 시도 → 차단 + `DECRYPT/DENIED` 적재 ·
사유 5자 미만 → 거부 · 권한 부여 후 원본 표시(DI 는 여전히 `(설정됨)`) ·
상태 변경(`updated_by` 기록) / 허용 밖 상태 거부 / ACTIVE 복귀 시 잠금·실패횟수 초기화 /
잠금 해제 · 임시 비밀번호 발급(해시 변경 + 만료시각 과거 + 화면 미노출) ·
배치 4종 실행 · 사유 없는 강제 탈퇴 거부 → 사유 포함 시 PII 전부 NULL +
원장 `ADMIN_FORCE` + 보존만료 36개월 · CSV(BOM·마스킹·사유 이력 적재, 사유 없으면 400).
단위 테스트 15건 추가(총 90건 통과). 검증 데이터·부여한 역할 전량 원복.

> **실측 결함 3건 발견·수정**
> 1. **라우팅 충돌** — `/adm/member/batch/withdraw`(배치)가 `/{memberId}/withdraw`
>    (강제 탈퇴)에도 매칭돼 배치 버튼이 400 을 뱉었다. `{memberId:MBR_.+}` 로 제약
>    (PK 규약이 접두어를 보장하므로 라우팅과 현실이 일치한다).
> 2. **배치가 0건일 때 침묵** — 수동 실행의 확인 수단이 로그뿐인데 대상이 없으면
>    아무것도 안 찍혀 "돌았는데 0건" 과 "안 돌았다" 를 구분할 수 없었다. 0건도 로그로.
> 3. **사유 없는 강제 탈퇴가 빈 400 페이지** — `@RequestParam` 필수라 스프링이 먼저
>    막아 서비스의 안내 문구가 화면에 닿지 않았다. 선택 파라미터로 바꿔 서비스가 판정.
>
> **주의**: Thymeleaf 는 `${a} ?: ${b} ?: '-'` 같은 elvis 체인을 파싱하지 못한다
> (템플릿 캐시가 켜져 있어 수정 후 재기동해야 반영된다).

### P10 마무리 (2026-07-30)

P10 하위 항목 <b>전부 완료</b>. 마지막 회차(잔여 4건)의 실측 결과:

**완료 확인(2026-07-30, 8081 실측)**: V921 템플릿 적재 + 본문에 "휴면 계정 복원" 안내
0건(사실과 다른 문구가 빠졌다) · 회원이 쓴 글·댓글이 강제 탈퇴와 동시에 `탈퇴한 회원` 로
바뀌고 **본문과 `writer_user_id` 는 그대로** · 회원 PII 는 파기 · 발송 제한은 10회까지 통과
11·12회차 차단(`발송 제한 초과` 로그 2건) · 휴면 복원 화면에 실명인증 버튼과 팝업 URL
(`/member/identity/nice?purpose=SELF&siteCode=ai&next=/ai/member/dormant`) 렌더.
단위 테스트 9건 추가(총 99건 통과). 검증 데이터 전량 정리.

> **실측 못 한 것**: 실명인증 복원의 **DI 대조 자체**. 유효한 DI 는 NICE 계약 자격으로
> 실제 인증을 거쳐야만 나오므로, 화면·세션 배선까지만 확인했고 `restoreByIdentity` 의
> 일치 판정은 자격이 들어온 뒤 재검증이 필요하다.

> **환경 이슈 관측**: 검증 중 짧은 간격의 연속 요청(초당 수 회)에서 JVM 이
> `0xC0000005`(ACCESS_VIOLATION)로 죽었다. 자바 예외가 아니라 네이티브 크래시이며,
> CLAUDE.md 가 경고하는 <b>Virtual Threads + HikariCP + Windows</b> 조합의 알려진
> 증상과 같은 계열이다. 요청 간격을 두니 재현되지 않았다. 이번 변경분(레이트리밋·익명화·
> 템플릿)과의 인과는 확인되지 않았다 — 배포 전 부하 조건에서 별도 확인이 필요하다.

### P10 후속 — 코드 리뷰 반영 (2026-07-30)

리뷰 지적 중 사용자가 처리 방향을 정한 것들.

- [x] **1. 소셜 계정 재연결이 UNIQUE 에 막힌다** → 탈퇴 시 `tb_member_oauth` 를
      `use_yn='N'` 이 아니라 **행째로 DELETE**. 탈퇴 사실은 원장이 해시로 보관한다
- [x] **5. 파일 업로드 권한** → `ROLE_REAL` 은 "실명확인을 거쳤다" 는 **사실 표시**이며
      권한 게이트가 아니다(사용자 확정). 업로드 최소 권한을 `ROLE_MEMBER` 로 내렸다.
      `vw_user_login` 이 회원의 `role_codes` 를 `'ROLE_MEMBER'` 로 하드코딩하므로
      `ROLE_REAL` 은 Security 권한에 애초에 나타날 수 없었다 — 회원 업로드가 전부 403
- [x] **4. 파기 이력 실패가 탈퇴를 롤백시킨다** → 부가 기록 적재 실패는 **삼키고 계속**,
      대신 `log_error` 에 남겨 관리자 화면에서 확인한다(아래)
- [x] **logback 파일 로깅** — `gopcms.log` · `gopcms-error.log`(WARN+) · `sql.log`
      (MyBatis `logPrefix("gopcms.sql.")` 로 분리). `GOPCMS_LOG_DIR` 미지정 시
      `./data/gopcms/logs`. **운영(war)에서는 절대경로 지정 필수** — 상대경로는
      Tomcat 작업 디렉터리 기준으로 풀린다
- [ ] **3. 미처리** — `SocialLoginAuthenticator` 가 `lockedUntil`·`captchaRequiredYn` 를
      건너뛴다(`MemberAuthenticationProvider` 는 검사한다). 잠긴 계정이 소셜 경로로
      들어올 수 있다
- [ ] **미처리** — 배치 재시도 시 `log_pii_purge` 중복 행

#### 삼킨 실패를 드러내는 창구 — `log_error` + `/adm/error-log`

- [x] `logging/error/` 수직 슬라이스 — DTO·Mapper(+XML)·Service·`ErrorLogger`
- [x] `ErrorLogger` 2겹 방어: DB 적재 실패 시 파일 로그만(재귀 금지), `Throwable` 까지 차단
- [x] 5개 채널 배선 — `PII_PURGE_LOG` · `PRIVACY_ACCESS_LOG` · `LOGIN_HISTORY` ·
      `FILE_DOWNLOAD_LOG` · `ACCESS_LOG`(요청마다 불리는 자리라 **60초 스로틀**,
      억제 건수는 다음 행에 함께 기록)
- [x] 쿼리스트링 민감 파라미터 마스킹(`reason`·`state`·`encodedata`·`token` 등),
      세션 ID 는 마지막 8자만
- [x] `UnhandledErrorRecorder` — `HandlerExceptionResolver` 최우선 order, `null` 반환으로
      **응답 흐름은 그대로**. 4xx(`ErrorResponse`)·인가 거부는 제외
- [x] `/adm/error-log` 목록(분류별 집계 + 필터) · 상세(스택트레이스). 읽기 전용.
      URL 규칙 추가 없음 — `/adm/**` ROLE_ADMIN(pri 20)이 덮는다
- [x] LNB "운영" 그룹에 등록

**완료 확인(2026-07-30, 8081 실측)**: `log_privacy_access` 를 RENAME 으로 치워
적재를 실제로 깨뜨린 뒤 `/adm/member` 호출 → 회원 목록은 **200 정상 응답**,
`log_error` 에 `RECORD_FAILURE:PRIVACY_ACCESS_LOG` 1건(스택 26,014자, `trace_id` 보유),
목록·상세 화면 렌더 확인. 테이블 원복 후 검증 데이터 전량 정리.

#### 탈퇴 원장에 마스킹 이름 (V11) — 2026-07-30

- [x] `V11__member_withdraw_masked_name.sql` — `member_name varchar(150)`
- [x] `Mask.name()` 결과를 원장 INSERT 파라미터로 전달(`findNameSource` /
      `findDormantNameSource` 로 복호화된 원본을 **`nullifyPii` 앞에서** 읽는다)
- [x] 암호화하지 않는다 — 이미 되돌릴 수 없는 값이고, `PiiTypeHandler` 를 붙이면 깨진다
- [x] 원장 화면(`/adm/member/withdraw`)에 이름 열 추가

> `INSERT … SELECT` 로 컬럼을 그대로 복사하면 **암호문이 원장에 박힌다** — 키가 있는
> 쪽에서는 그것이 곧 평문이다. 그래서 앱이 마스킹해서 넣는다.

**완료 확인(2026-07-30, 8081 실측)**: 이름 `김철수` 회원을 관리자 강제 탈퇴 →
원장 `member_name='김*수'`, 원본 `tb_member.member_name`·`email` 은 NULL,
`status=SUSPENDED`·`delete_yn='Y'`. 원장 화면에 `김*수` 표시. 검증 회원 전량 삭제.
단위 테스트 105건 통과.

#### 이름은 평문 저장으로 되돌림 — 2026-07-30 사용자 확정

`member_name` · `parent_name` 의 `@Encrypt` + `PiiTypeHandler` 를 제거했다.

- [x] 근거 — 이름은 개인정보지만 **저장 암호화 의무 대상이 아니다**(고유식별정보·비밀번호·
      생체정보만 의무). 반대로 **이름 검색은 실무 필수**다
- [x] V6 DDL 의 컬럼 주석은 처음부터 `'회원 이름 (평문)'` 이었다 — 코드가 어긋나 있었다
- [x] 제거 지점 5곳 — `MemberMapper`(insert + resultMap) · `MemberAdmMapper`(admRow·dormantRow) ·
      `MemberLifecycleMapper`(targetMap·dormantMemberMap) · `AuthMapper`(display_name resultMap)
- [x] `MemberDto` 의 `@Encrypt` 제거 + 판단 근거를 javadoc 에 기록
- [x] **이름 부분일치 검색 활성화** — 회원 목록·휴면 현황의 `keyword` 에 `member_name` 추가
- [x] 마이그레이션 없음 — DDL 변경이 아니라 **해석**만 바뀐다(컬럼 타입·주석 그대로)

> 마스킹·접근이력·파기 의무는 그대로다. 암호화를 뺀 것이 보호를 놓은 것은 아니다.

**부수 효과 — 죽어 있던 제약이 (부분적으로) 살아났다.** `uk_member_identity` 는
`member_name` 을 포함하는데, 암호화 시절에는 난수 IV 때문에 같은 이름이 매번 다른
암호문이 되어 **중복을 하나도 잡지 못했다.** 평문 전환 후 실측:

```
ERROR 1062: Duplicate entry 'SIT_…302-동일이름-DIH_SAM…' for key 'uk_member_identity'
```

단 **성인 회원은 여전히 통과한다** — `parent_di_hash` NULL 때문이다(결정 대기 표에 등재).

**완료 확인(2026-07-30, 8081 실측)**: `GOPCMS_NICE_ENABLED=false` 로 가입 마법사를
끝까지 통과시켜 회원 생성 → `member_name='박서연'`·`nickname='서연이'` **평문**,
`email`·`phone`·`birth_date` 는 `{AG}` 암호문 유지. 관리자 목록에서 `서연` 부분일치 검색
1건 적출, `홍길동` 전체 일치로 시드 회원 적출, 표시는 마스킹(`박*연`), 응답 전체에
`{AG}` 노출 0건. 회원 로그인(`display_name` 경로) 정상. 검증 회원 전량 삭제.
단위 테스트 105건 통과.

> **실측 못 한 것**: 마스킹 해제(`?reason=`). `admin` 계정에 `ROLE_PRIVACY` 가 없어
> 정책대로 거부됐다(`log_privacy_access` 에 `DECRYPT / DENIED / ROLE_PRIVACY 없음` 적재
> 확인 — 거부 경로는 정상 동작). 해제 후 평문 표시는 권한 보유 계정으로 재검증 필요.

#### `member_name` NOT NULL + 탈퇴 시 마스킹 (V12) — 2026-07-30 사용자 확정

- [x] `V12__member_name_not_null.sql` — 기존 NULL(탈퇴 파기 행)을 **원장의 마스킹 이름으로
      되메우고**(V11 이후 탈퇴), 되메울 값이 없으면 `'-'`. 그 다음 NOT NULL 적용.
      순서를 바꾸면 기존 NULL 때문에 ALTER 가 실패한다
- [x] 파기 시 이름은 NULL 이 아니라 **마스킹 값**(홍*동) — `nullifyPii`·`nullifyDormantPii`
      가 파라미터로 받는다. 원장·tb_member·tb_member_dormant **세 값이 동일**(한 번 계산해 돌려씀)
- [x] `parent_name`·`tb_member_dormant.member_name` 은 NOT NULL 을 걸지 않았다 —
      법정대리인은 14세 미만에만 있고, 스냅샷은 원본이 아니다
- [x] 마이그레이션 없이 되는 부분: 가입 경로는 이미 이름을 필수 검증하고 있었고
      `tb_member` INSERT 지점도 1곳뿐이다(사회 로그인은 회원을 만들지 않는다)

**🔴 실측으로 찾은 결함 — 휴면 스냅샷 PII 가 파기되지 않았다.**
`insertWithdrawLedger` 의 조회에 `delete_yn` 필터가 없고, 휴면 전환이 복사 + soft-delete 라
휴면 회원도 `tb_member` 행을 갖고 있다 → 원장이 **언제나** `tb_member` 에서 적재되고
`nullifyDormantPii` 는 사실상 실행되지 않았다. 그래서 휴면 만료 자동 탈퇴가
**휴면 스냅샷의 이름·이메일·전화·주소를 통째로 남겨** 두고 있었다(로그에 `탈퇴 처리(휴면
경유)` 가 아니라 `탈퇴 처리` 만 찍히는 것이 단서). 분기 구조를 없애고 **두 테이블을 항상
파기**하도록 고쳤다. `log_pii_purge.table_list` 도 실제 손댄 테이블만 적는다.

**완료 확인(2026-07-30, 8081 실측)** — 두 경로를 각각:

| | tb_member | tb_member_dormant | 원장 | table_list |
|---|---|---|---|---|
| A 활성 강제탈퇴 | `이*성` · 나머지 NULL | (없음) | `이*성` | `tb_member,tb_member_oauth` |
| B 휴면 만료 배치 | `최*면` · 나머지 NULL | **`최*면` · 나머지 NULL** | `최*면` | `tb_member,tb_member_dormant,tb_member_oauth` |

수정 전 B 의 스냅샷은 `최휴면`·암호문 이메일·전화가 그대로 남았다. 단위 테스트 105건 통과,
검증 회원 전량 삭제.

> **함정 기록**: 매퍼 XML 의 `--` SQL 주석도 **XML 본문**이다. 주석 안에 `<b>` 를 썼다가
> `Element type "b" must be declared` 로 SqlSessionFactory 생성이 실패해 기동이 깨졌다.
> HTML 태그는 `<!-- -->` XML 주석 안에서만 쓸 것.

#### 감사 로그 `log_audit` 적재 — 2026-07-30 사용자 요청 (기존 "미도입" 결정 번복)

`log_audit` 은 V1 부터 테이블이 있고 보존기간 배치(36개월)도 대상에 넣고 있었지만
**적재 코드가 없어 0건**이었다. 이 세션에서 반복 확인한 "스키마만 있는 기능" 중 하나다.

- [x] `logging/audit/` 수직 슬라이스 — DTO·Mapper(+XML)·Service·`AuditTrailRecorder`
- [x] `AuditTrailInterceptor`(`HandlerInterceptor`) + `WebMvcConfig` —
      `/adm/**` 비-GET 전수, `/adm/login`·`/adm/logout` 제외
- [x] URL 에서 기계적으로 해석 — `target_entity`(중첩 자원까지: `BOARD_ARTICLE_COMMENT`) ·
      `action`(`CREATE`/`UPDATE`/`DELETE`/`BATCH_NOTICE`…) · `target_id`
- [x] 결과 판정 — 예외 · `flashError` · **쓰기 요청 2xx**(= 폼 재표시 = 검증 실패)
- [x] 적재 실패는 삼키고 `log_error` 의 `RECORD_FAILURE:AUDIT_LOG` 로 올린다
- [x] `/adm/audit-log` 목록(결과·행위·대상·검색어 필터) · 상세. 읽기 전용, LNB "운영" 등록
- [x] 마이그레이션 없음 — 테이블·인덱스·보존기간이 이미 있다

> **왜 도메인별 호출이 아닌가.** 테이블 주석이 "관리자 CUD 전수" 다. 서비스마다 호출을
> 심으면 새 화면에서 한 줄 빠뜨릴 때 그 화면만 조용히 감사에서 빠진다. `/adm/**` 패턴
> 하나면 새 화면이 등록 없이 자동으로 걸린다 — 전수를 규율이 아니라 구조로 보장한다.

**완료 확인(2026-07-30, 8081 실측)** — 관리자 세션으로 변경 요청을 돌려 기록 대조:

| 요청 | action | target_entity | target_id | result |
|---|---|---|---|---|
| `POST /adm/board/save` (신규) | `CREATE` | `BOARD` | — | SUCCESS |
| 같은 URL (bbsCode 중복) | `CREATE` | `BOARD` | — | **FAIL** |
| 같은 URL (id 있음) | `UPDATE` | `BOARD` | `BBM_…` | SUCCESS |
| `POST /adm/board/{id}/article/save` | `SAVE` | `BOARD_ARTICLE` | `BBA_…` | SUCCESS |
| `POST /adm/board/category/save` | `CREATE` | `BOARD_CATEGORY` | — | SUCCESS |
| `POST /adm/member/{id}/status` | `STATUS` | `MEMBER` | `MBR_…` | SUCCESS |
| `POST /adm/member/batch/notice` | `BATCH_NOTICE` | `MEMBER` | — | SUCCESS |
| `POST /adm/password` (틀린 비번) | `UPDATE` | `PASSWORD` | — | **FAIL** |
| `POST /adm/board/delete` (글 있음) | `DELETE` | `BOARD` | `BBM_…` | **FAIL** |
| `GET /adm/index` | — 기록 없음 | | | |

목록·상세·필터 4종 200, `log_error` 0건. 검증 데이터 전량 정리, 단위 테스트 105건 통과.

**🔴 실측으로 잡은 결함 3건** (모두 이번 구현 중 발생·수정):

1. **`siteId` 를 대상으로 오인** — "알려진 ID 파라미터 목록" 을 순서대로 훑는 방식이
   원인. 게시판 신규 등록은 `bbsMasterId` 가 비어 `siteId` 가 잡혀
   *사이트를 고친 것처럼* 기록되고 `CREATE` 도 `UPDATE` 가 됐다.
   → 엔티티별 대상 파라미터 매핑으로 교체(`siteId`·`fileGroupId` 는 상위 참조라 제외)
2. **검증 실패가 SUCCESS 로 기록** — 폼 재표시(200)의 오류는 Model 에 있고 플래시 맵에
   없어 잡히지 않았다. → "쓰기 요청 + 2xx = FAIL" 규칙 추가
3. **`Map.ofEntries`/`Set.of` 의 null 키 NPE** — `/adm/password` 는 두 번째 마디가
   행위 단어라 필터에 전부 걸러져 `entity=null` 이 되고 `Map.get(null)` 이 NPE 를 냈다.
   → 전부 걸러지면 두 번째 마디를 엔티티로 쓰고, 조회 전 null 을 방어

> 3번은 **`log_error` 배선이 스스로를 증명한 사례**다. NPE 로 감사 적재가 실패했지만
> 관리자 업무는 그대로 진행됐고, 실패가 `RECORD_FAILURE:AUDIT_LOG` 로 드러나 원인을
> 바로 찾았다. 파일 로그만 있었다면 0건인 채로 넘어갔을 것이다.

> **함정 기록**: `@Builder` 만 붙인 DTO 는 Lombok 이 기본 생성자를 없애 MyBatis 가
> **생성자 자동 매핑**으로 빠진다. 컬럼을 골라 읽는 목록 쿼리(13개)와 생성자 인자(18개)가
> 어긋나 500 이 났다 — 상세는 `SELECT *` 라 우연히 통과하고 목록만 깨졌다.
> 조회에 쓰이는 DTO 에는 `@NoArgsConstructor` + `@AllArgsConstructor` 를 함께 붙일 것.
> (형제 DTO `AccessLog` 는 적재 전용이라 이 함정을 밟지 않았다.)

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
| `AuditLogger` 5경로 | ~~미도입~~ → **도입**(2026-07-30) | `log_audit` 은 스키마만 있고 0건이었다. 사용자 요청으로 적재 — 단 원전의 도메인별 5경로 호출이 아니라 `/adm/**` 인터셉터 1곳(아래) |
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
| **`uk_member_identity` 가 성인 중복가입을 못 막는다** | `UNIQUE (site_id, member_name, di_hash, parent_di_hash)` — MariaDB UNIQUE 는 NULL 을 서로 다른 값으로 취급한다. `parent_di_hash` 는 **14세 미만에만** 채워지므로 성인 회원은 항상 NULL → 같은 `di_hash` 로 몇 번이든 가입된다(2026-07-30 실측). 이름 평문화로 제약이 살아난 범위는 **아동 회원뿐**. 성인까지 막으려면 `(site_id, di_hash)` 별도 UNIQUE 추가 또는 `parent_di_hash` NOT NULL 기본값(`''`) — **스키마 결정 사항이라 임의 변경하지 않았다** | 결정 필요 |
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
| ~~위지윅 에디터 선정~~ | ✅ **완료(2026-07-29)**: Tiptap 적용 + `gopcms.editor.provider` 3종 교체 구조(tiptap/namo/ckeditor5). CSP 재검토 결과 Tiptap 은 `unsafe-eval` 불필요 — CSP 무수정으로 동작 | P9-2b 완료 |
| ~~Tiptap 번들 빌드~~ | ✅ 해소: esbuild 추가 + Maven generate-resources 연결(`npm run editor`). 산출물은 gitignore, 소스는 `src/editor/tiptap.js` | P9-2b 완료 |
| **CrossEditor 도입 가부** | 자리(프래그먼트·자산 목록·기동 검증)는 준비 완료 — 번들을 배치하고 provider 를 `namo` 로 바꾸면 동작한다. **`unsafe-eval` 은 열지 않는다**(사용자 확정 2026-07-29). iframe 이라 `frame-src` 를 요구하면 그때 판단 | 번들 반입 시 |
| **CrossEditor 라이선스** | 1도메인 단위 상용. 구매·적용 도메인 확정 전에는 벤더 번들을 저장소에 커밋하지 않는다 | 도입 전 |
| **에디터 간 본문 호환** | provider 를 바꿔도 기존 `body` 가 열려야 한다. CrossEditor 는 HTML4.01/XHTML 계열 마크업을 뱉으므로 allowlist 를 두 출력의 합집합으로 잡지 않으면 기존 글이 저장할 때마다 깎인다 | P9-2b |
| **탈퇴 시 `di_hash` 존치 여부** | 탈퇴 원장(`tb_member_withdraw`)의 `di_hash`·`login_id_hash` 는 **재가입 제한·중복가입 차단·분쟁 대응의 유일한 근거**다. "개인정보 필드 모두 NULL" 을 원장까지 적용하면 그 기능이 사라진다. `tb_member` 는 전부 NULL, 원장 해시는 존치가 기본안 — 확인 필요 | P10-4 착수 전 |
| **완전 삭제 후 게시글 작성자** | `tb_bbs_article.writer_user_id`·`writer_name` 은 크로스 참조라 회원을 지워도 남는다. **`writer_name` 은 개인정보다** — "PII 즉시 파기" 원칙을 따르면 탈퇴 시 작성자명도 익명 표기로 바꿔야 앞뒤가 맞는다. 게시글 자체는 보존 | P10-4 |
| ~~탈퇴 원장 보존기간~~ | ✅ 확정: **36개월**(`retention_expire_at` 에 계산해 채운다). 로그 `log_*` 도 36개월 | P10-7 |
| ~~개인정보 5년 vs 탈퇴 후 완전삭제~~ | ✅ 확정: **회원 PII 는 즉시 파기**(탈퇴 시 NULL → 1년 후 행 삭제). "5년" 은 개인정보 접근·파기 **이력**(`log_privacy_access`·`log_pii_purge`)에만 적용 | P10-7 |
| ~~통계 `stat_*` 보존~~ | ✅ 확정: **영구 보존** — 개인 식별 정보가 없는 집계값이라 파기 대상이 아니다 | P10-7 |
| **휴면 정책의 법적 근거** | 1년 미이용자 분리보관·파기 의무(개인정보 유효기간제)는 폐지된 것으로 알고 있다 — 그렇다면 1년 휴면은 법적 의무가 아니라 서비스 정책이고 사전통지 의무 범위도 달라진다. 개인정보 담당자 확인 권장(내 판단을 근거로 삼지 말 것) | P10-4 착수 전 |
| **프로그램 네임스페이스 3중 동기화** | `/bbs/`·`/prg/` 목록이 `SKIP_PREFIXES`·컨텐츠 예약 slug·사이트코드 예약어 세 곳에 흩어지면 라우팅이 조용히 깨진다 — 상수 한 곳을 세 곳이 참조하도록 | P9-0 |
