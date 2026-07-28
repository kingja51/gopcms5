# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 따라야 할 지침입니다.

## 프로젝트

gopcms5 — **eGovFrame 5.0 호환성 확인 획득이 목표**인 KRDS UI 멀티사이트 CMS.
Java 21(Virtual Threads) / Spring Boot 3.5.x / MyBatis 전용(JPA 금지) / Thymeleaf+htmx+Tailwind v4 CLI.
`war` 패키징(외부 Tomcat 10.1.x + 임베디드 로컬 이중 진입점). DB는 **MariaDB 11.8 기본**, PostgreSQL 병행 지원(멀티 벤더).
주요 모듈 9종: 템플릿·사이트·메뉴·컨텐츠·게시판·관리자회원·사용자회원·부서·직원.

**현재 P0 진행 중** ([PLAN.md](PLAN.md)) — 설계·DB 마이그레이션(V1~V3, dev 시드) 완료,
pom 루트 승격 완료. **JDK 21 = `C:\Program Files\Java\jdk-21`** (시스템 기본 java 는 1.8 —
빌드 전 JAVA_HOME 지정 필수). Security 는 P6 까지 pom 에서 임시 주석(`TODO(P6)`).

**패키지 구조**: `com.gonet.{config, common, primary, secondary, logging, scheduler}` —
도메인은 `primary/<domain>/{controller,service,mapper,dto}` 수직 슬라이스(DB 축 우선,
conventions.md §3~4). 컨트롤러 클래스 접미어(Usr/Adm/Api)는 동일 도메인 패키지에 공존.

## 정본 문서 (역할 분담)

- [README.md](README.md) — 프로젝트 조망·기술 스택·로드맵. **처음 읽는 문서.**
- [PLAN.md](PLAN.md) — 페이즈별 작업 목록·진행 추적(체크박스). **작업 시작 전 현재 페이즈 확인, 완료 시 체크박스 갱신.**
- [doc/template-resolver-design.md](doc/template-resolver-design.md) — 3축(layout·template·theme) 템플릿 아키텍처 + ViewResolver 설계. **템플릿/레이아웃 질문은 여기부터.**
- [doc/conventions.md](doc/conventions.md) — PK(`대문자접두어3+"_"+UUIDv7`=varchar(40))·접두어 레지스트리·컨트롤러 네이밍. **식별자/네이밍 질문은 여기부터.**
- [doc/flyway-migration.md](doc/flyway-migration.md) — SQL 마이그레이션 규약(버전 정책·벤더 폴더·작업 절차).
- [design-md/styleguide/SG-krds.md](design-md/styleguide/SG-krds.md) — KRDS × Tailwind v4 토큰·프리셋. **화면 작업의 단일 기준.** 데모: SG-krds.html.
- [wireframe/index.html](wireframe/index.html) — 레이아웃 설계안 A~G(frame001~007) 원전.
- 위 정본 외 **`.md` 임의 생성 금지** — 사용자 명시 요청 시만.

## 빌드 / 검증

```bash
npm run css            # src/krds.css → dist/krds.css (전역 KRDS, minify)
npm run css:watch      # 개발 감시
npm run css:sg         # 스타일가이드 재빌드 (design-md/styleguide)
# ── 구현 단계 진입 후 ──
./mvnw -o compile -DskipTests -Dtailwind.skip=true   # 1차 검증 (매 작업 후)
./mvnw spring-boot:run                                # 로컬 실행 (local 프로파일 8080)
```

- Tailwind 는 CLI 빌드 필수(CDN 금지). 오프라인 자바 검증만 `-Dtailwind.skip=true`.
- **DevTools 금지** — Virtual Threads+HikariCP 조합 Windows JVM 크래시 이력(선행 프로젝트). 재시작은 완전 정지 → Rebuild → Run.

## 버전관리 — git 사용

원격: `https://github.com/kingja51/gopcms5` (main 직접 커밋, 1인 개발).
**커밋·푸시는 사용자가 요청할 때만** 실행한다. 마이그레이션+관련 코드는 한 커밋으로 묶는다.
적용된 Flyway 파일 수정 금지 등 이력 규칙은 flyway-migration.md 를 따른다.

## 아키텍처 핵심

- **3-DB 분리**: primary_db(GOPCMS 기본, `tb_*`) / secondary_db(클라이언트 프로그램, `tn_*`) /
  logging_db(로그·통계, `log_*`/`stat_*`). VIEW 는 공통 `vw_*`. 각자 DataSource·TxManager
  (`primaryTransactionManager` 등)·SqlSessionFactory·MapperConfigurer. **크로스 DB JOIN/FK 금지**
  — ID 값(varchar 40)으로만 참조, 서비스 계층에서 조합. 로그 쓰기는 주 트랜잭션과 격리(REQUIRES_NEW/비동기).
- **3축 템플릿 모델**: layout(구조=뷰 폴더 `templates/layouts/{layout_code}/`) ·
  template(시각 언어=CSS 1장 `/tmpl/css/{template_code}.css`) · theme(색=html 클래스, 파일 없음).
  사이트가 세 축을 선택(`tb_site.template_id/layout_id/theme`, NULL=템플릿 기본).
- **컨텐츠 URL 계약**: `/{siteCode}/index`(랜딩) · `/{siteCode}/sitemap` · `/{siteCode}/{slug}`(컨텐츠).
  고정 라우트 우선 매칭, 예약 slug(index·sitemap·search 등) 등록 차단, siteCode 는 항상 경로 유지 (conventions.md §5).
- **해석은 Resolver 단계**: 컨트롤러는 논리 뷰명(`front/**`)만 반환 →
  `SiteTemplateViewResolver` 가 `layouts/{layoutCode}/**` → `layouts/_default/**` 폴백 재작성.
  `layout:decorate="~{${siteLayout}}"` 동적 지정. layout.html 은 폴백 금지(기동 검증).
- **PK**: `VARCHAR(40)` = 대문자 접두어 3자 + `_` + UUIDv7 (예: `SIT_0189…`). 채번은 앱 유틸
  단일 경로(`Uid.next(UidPrefix.X)`), DB 함수 채번 금지. 새 테이블은 conventions.md §2
  레지스트리 등록이 선행. ID 컬럼은 MariaDB `ascii_bin`.
- 전 테이블 감사컬럼 6종(created_by/ip/at + updated_by/ip/at) + `use_yn`/`delete_yn`(soft-delete).

## 반드시 지킬 규약 — eGov 호환성 (탈락 사유가 되는 항목)

- **필수 4종 유지**: `ptl-mvc`·`fdl-cmmn`·`psl-dataaccess`·`fdl-logging` 동일 버전(5.0.0).
  fdl-logging 의 exclusion(log4j-core/log4j-slf4j2-impl)은 **jar 는 유지하고 브리지만 제외**한
  의도된 구성 — 제거하거나 모듈 자체를 exclusion 하지 말 것.
- **Spring Boot 3.5.x patch 라인만 상향 허용** — 3.6/4.x 업그레이드 금지.
- **Service**: 서비스별 인터페이스 + `*ServiceImpl extends AbstractCmsService`(→`EgovAbstractServiceImpl` 간접 상속).
- **Mapper**: Mapper 인터페이스 + eGov `MapperConfigurer` + **`@EgovMapper`**(`@Mapper` 아님 — 5.0 기준).
- **Controller**: `{도메인}{Api|Usr|Adm}Controller` — Api=`/api/v1/**` JSON, Usr=사용자 화면(템플릿 Resolver 대상), Adm=`/adm/**`(재작성 제외). Mapper 직접 호출 금지 — Service 인터페이스 경유.
- **확장 규칙**: 자체 클래스는 `org.egovframe.rte` 패키지 금지, `Egov` 접두 클래스명 금지.

## 트랜잭션 함정 (선행 프로젝트 실장애 이력 — 그대로 유효)

- 클래스 레벨 `@Transactional(readOnly=true)` 는 메서드에 상속 — **쓰기 메서드는 반드시 writable override**.
- 자기호출 `this.txMethod()` 는 프록시 우회로 `@Transactional` 무시 — 빈 분리 또는 호출측 전파 설정.
- 긴 외부호출(@Async 업로드·폴링)은 `NOT_SUPPORTED`/`REQUIRES_NEW` 로 격리.

## DB / 마이그레이션

- **MariaDB 11.8 이 기본 개발·운영 DB.** PostgreSQL 은 벤더 번역판이 준비된 후에만 기동.
- 스키마·기준 데이터 변경은 **오직 Flyway 마이그레이션** — 구조는
  `db/migration/{primary|secondary|logging}/{vendor}` (+ dev 시드 `db/devdata/{db}` V900+).
  DB 별 Flyway 빈 3개·이력 독립(자동구성 off). 대상 DB 는 테이블 접두어로 결정
  (tb_→primary, tn_→secondary, log_/stat_→logging). 적용된 파일 수정 금지, 수정은 다음
  버전으로 전진. 콘솔 수기 DDL 금지.
- MyBatis SQL 은 **전량 `#{}` 바인딩, `${}` 절대 금지**(SQLi). 벤더 분기 SQL 은 매퍼에서 databaseId 로 처리.

## UI / 프런트엔드

- **KRDS 토큰만 사용**(SG-krds.md): 시맨틱 토큰(`bg-surface`·`text-fg-subtle`·`border-line`)·`.krds-*` 프리셋 우선.
  **raw hex·Tailwind 기본색(`bg-blue-500`)·기본 타이포(`text-xl`) 금지.** 타이포는 `text-{display|heading|body|label}-*`,
  radius 최대 12px(rounded-xlarge), 간격 8px 그리드.
- 리브랜딩·테마는 `--brand-*` 스왑/`theme-*` 클래스 단일 경로(`tb_site.theme` 연동) — CSS 파일 추가 금지. 고대비 `hc` 병행 지원.
- **htmx + 순수 JS 만 — JS 프레임워크(Alpine 등) 금지.** 이벤트 위임(document 1회 + `closest('[data-action]')`),
  `htmx:load` 멱등 초기화(`data-initialized` 가드). 인라인 스크립트 금지 — 외부 `.js` + CSP nonce, self-host(CDN 금지).
- 네이티브 요소 우선: 아코디언 `<details>`, 모달 `<dialog>`.
- htmx 조각 응답은 Usr/Adm 컨트롤러 담당, `*ApiController` 는 순수 JSON 전용.

## 보안 (선행 프로젝트 웹쉘 침해 대응 경험 — 보수적으로)

- 다중 SecurityFilterChain: `/adm/**`(관리자 2FA) / member / default. URL 네임스페이스 = 컨트롤러 접미어 = 보안 경계 1:1.
- **인가는 DB 단일 원천** — `tb_role_url_access` + `DynamicAuthorizationManager`(priority ASC,
  사이트 규칙 우선, **무매칭 DENY**). 새 URL 은 접근 규칙 INSERT 를 같은 커밋에 넣어야 열린다
  (절차·주의는 conventions.md §7). SecurityConfig 에 URL 별 예외를 추가하지 말 것.
- **client_ip 은 `ClientIpResolver` 단일 경로** — `getRemoteAddr()` 직접 호출 금지.
  신뢰 프록시(`GOPCMS_TRUSTED_PROXIES`) 미설정 시 X-Forwarded-For 를 무시하는 것이 의도된 기본값.
- 파일 업로드 다중 방어(확장자+Tika 매직바이트+재인코딩), CSRF, CSP nonce(`SecurityHeadersFilter` —
  인라인 스크립트는 `th:attr="nonce=${cspNonce}"` 없이는 실행 불가), OWASP Sanitizer, 로그인 잠금, Bucket4j.
- 비밀값은 Jasypt `ENC(...)`/환경변수 — 코드·yml 평문 금지, 미주입 fail-fast 는 의도된 동작.
  키 목록은 [.env.example](.env.example) (실값은 `.env` — git ignore, **example 에 실비밀 입력 금지**).
- **PII 암호화**: `@Encrypt`(AES-256-GCM, 저장값 `{AG}` 프리픽스) — 마스터키 `GOPCMS_PII_MASTER_KEY`(base64 32바이트) fail-fast.
  TypeHandler 가 암복호화 담당(서비스는 평문만), 검색 필요 컬럼은 `*_hash`(SHA-256) 병행 (conventions.md §6).

## 참고

- 선행(자산 원전) 프로젝트: `D:\claude\pcms2026-001` — SG 스타일가이드 체계·KRDS 규약의 원전. 충돌 시 이 저장소의 정본 문서가 우선.
- NICE 본인인증 jar 는 JPMS 플래그 필요(`--add-exports/--add-opens java.base/com.sun.crypto.provider=ALL-UNNAMED`) — surefire·spring-boot:run·운영 Tomcat setenv 모두(pom.xml 반영됨, jar 는 `lib/`).
- eGov 호환성 가이드라인 원문: `C:\Users\kingja\Documents\호환성확인_가이드라인_20260622.pdf`.
