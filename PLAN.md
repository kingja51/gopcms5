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

### P6-2 DB RBAC (다음)
- [ ] `DynamicAuthorizationManager` — tb_role_url_access(priority ASC, **무매칭 DENY**)
- [ ] 새 URL 추가 시 접근 규칙 등록 절차 문서화 (빠뜨리면 화면이 안 열림)
- [ ] 회원 전용 영역(front) 조임 — default 체인 permitAll 해제
- [ ] 역할 계층 변경 시 closure 재전개 + 사용자 role_ids/role_codes CSV 재계산 배치

### P6-3 강화
- [ ] 2FA(TOTP — googleauth·QR) 관리자 강제(tb_admin_group.two_factor_required)
- [ ] 세션 정책 — 쿠키명 GOPCMS_SID · changeSessionId() · maximumSessions(1)
- [ ] CSP nonce + 인라인 정리(hc FOUC-free 복원 포함) · 보안 헤더
- [ ] X-Forwarded-For 신뢰 프록시(GOPCMS_TRUSTED_PROXIES) — client_ip 해석 일원화
- [ ] 허용 IP CIDR/RANGE 매칭 (현재 SINGLE 완전일치만)
- [ ] AccessLog actor_* 연결(세션 SecurityContext) + 로그인 이력(LGH) 적재
- [ ] 비밀번호 만료·이력 재사용 방지(APH/MPH) · CAPTCHA(잠금 해제 후)

## P7+ — 이후 로드맵 (예고)

관리자 모듈(사이트/템플릿/메뉴/컨텐츠 CRUD — SQL 수작업 대체) → 게시판(primary V4 +
BBS 도메인) → 회원·조직(V5) → 템플릿 CSS 진짜 구현(blueprint-001 부터, SG 스타일가이드
병행) → 나머지 레이아웃 양산(C·F 우선 검증 후) → 공통 프로그램 → eGov 호환성 확인 신청.

---

## 결정 대기 / 리스크

| 항목 | 내용 | 기한 |
|---|---|---|
| ~~UUIDv7 구현~~ | ✅ 확정: 선행 프로젝트 `UuidV7Generator` 이식 + `Uid` enum 래퍼 | P1 완료 |
| ~~감사컬럼 처리~~ | ✅ 확정: MyBatis `AuditInterceptor` (3개 SqlSessionFactory 공통) | P1 완료 |
| .env 주입 | IntelliJ Run Config 수동 입력 vs EnvFile 플러그인 | P0 |
| eGov RTE 다운로드 | maven.egovframe.go.kr 접근·좌표 실검증 (pom 주석의 주의사항) | P0 |
| layout-001 외 레이아웃 | P5 레이아웃 전환 테스트에 최소 1종 추가 필요(layout-003 권장) | P4~P5 |
| 시큐리티 공백기 | P0~P5 는 인증 없음 — **로컬 개발 한정**, 외부 노출 금지 | 상시 |
