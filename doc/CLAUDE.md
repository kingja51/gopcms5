# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 따라야 할 지침입니다.

## 프로젝트

PCMS 2026-001 — eGovFrame 5.0 호환 멀티사이트 웹 CMS. Java 21(Virtual Threads) / Spring Boot 3.5.9 / MyBatis 전용(JPA 금지) / Thymeleaf+htmx+Tailwind v4 CLI / Spring Security 6. `war` 패키징(외부 Tomcat 10.1.x + 임베디드 로컬 이중 진입점). DB는 MariaDB(운영) 기준 3개 분리(primary/secondary/logging), MySQL·PostgreSQL 매퍼 변형 지원.

**이전 프로젝트 `D:\claude\gopcms5`(15개 도메인 구현 완료)에서 자산을 이식 중.** 백엔드는 재설계 없이 이식, UI 계층만 KRDS 디자인 시스템으로 재구축한다.

## 정본 문서 (역할 분담)

- [구축가이드.md](구축가이드.md) — 아키텍처·규약·이식 로드맵·보안. **규약 질문은 여기부터.**
- [PLAN.md](PLAN.md) — 페이즈별 작업 목록·진행 추적(체크박스)·결정 대기 항목. **작업 시작 전 현재 페이즈 확인, 완료 시 체크박스 갱신.**
- [Design.md](Design.md) — KRDS × Tailwind v4 디자인 토큰·컴포넌트. **화면 작업의 단일 기준.**
- [콘텐츠가이드.md](콘텐츠가이드.md) — 사이트/템플릿/레이아웃/조각 정의·다국어 사이트 레이아웃 전략(차이 처리 4계단)·siteCode 해석 원칙("URL이 진실, 세션은 편의"). **레이아웃·콘텐츠 구성 질문은 여기부터.**
- [학과추가가이드.md](학과추가가이드.md) — 신규 학과(사이트) 복제 레시피(시드·쉘 컨트롤러·템플릿 4종). 교수진/직원/연구실/수업계획서는 제네릭 `/prg` 라 복사 불필요. **새 학과 추가 질문은 여기부터.**
- 문서 간 충돌 시 **이전 프로젝트 실측 코드·DDL 우선** (구축가이드 §0-1 충돌표). 15단계 프롬프트 문서(외부)는 그린필드 초안이라 실측과 다른 항목이 있음.

## 빌드 / 검증

```bash
./mvnw -o compile -DskipTests -Dtailwind.skip=true   # 1차 검증 (매 작업 후 실행)
./mvnw test -Dtest=ArchitectureTest                    # ArchUnit 규약 게이트 (P1 이후)
./mvnw -o package -DskipTests                          # war 패키징 (npm run css 자동 포함)
./mvnw spring-boot:run                                 # 로컬 실행 (local 프로파일 8080)
npm run css:watch                                      # 개발 중 CSS 반복 빌드
```

- **컴파일 통과가 1차 검증 기준.** 이전 프로젝트에는 테스트가 없으므로(0개) ArchUnit 게이트를 P1에서 신설한다.
- 비밀값(환경변수) 미주입 시 fail-fast 부팅 실패는 **의도된 동작** — `.env.example` 참고.
- Tailwind는 CLI 빌드 필수(CDN 금지). 오프라인 자바 검증만 `-Dtailwind.skip=true`.

## 버전관리 없음 (중요)

**git 미사용** (1인 개발 방침). `git init`/커밋/브랜치를 제안하거나 실행하지 말 것.
롤백 지점은 zip 백업: 페이즈 완료 시 + 대량 변경(전면 치환·대량 이식) **직전**에 `D:\claude\backup\pcms2026-001_P{n}_{yyyyMMdd}.zip` (`target`·`node_modules` 제외). 되돌릴 수단이 백업뿐이므로 파괴적 변경 전 백업 여부를 반드시 확인.

## 아키텍처 핵심

- **3-DB 분리**: primary(`tb_*` 핵심) / secondary(개별프로그램·외부API) / logging(`log_*`). 각자 DataSource·TxManager·SqlSessionFactory. TM 빈은 각 Config 상수(`primaryTransactionManager` 등).
- **패키지**: `com.gonet.{config, common, primary, secondary, logging, scheduler}`. 도메인은 `primary/<domain>/{controller,service,mapper,dto}` 수직 슬라이스.
- **PK**: UUID v7 `varchar(36)`(`UuidV7Generator`). 전 테이블 감사컬럼 6종(created_by/ip/at + updated_by/ip/at), soft-delete는 `SoftDeletable` 마커 + `delete_yn`.

## 반드시 지킬 규약

- **Service**: 인터페이스 + `EgovAbstractServiceImpl` 상속. 클래스 레벨 `@Transactional(readOnly=true, transactionManager=…)` 기본, **쓰기 메서드는 반드시 writable override**.
- **Mapper**: `@Mapper` 인터페이스 + 드라이버별 XML(`*_maria.xml`/`*_mysql.xml`/`*_postgres.xml`) — 수정 시 **3중 파일 동기 수정**. namespace ↔ FQN 1:1. **전량 `#{}` 바인딩, `${}` 절대 금지**(SQLi).
- **Controller 접미사**: `ApiController`(REST `/api/**`) / `UsrController`(사용자) / `MngController`(관리자). Mapper 직접 호출 금지 — Service 경유. CUD는 try-catch + log + `HX-Redirect`.
- **공통 자원 재사용 우선**: `PageRequest`/`ApiResponse`/`MaskUtils`/`AuditLogger` 등 `common/` 활용, 도메인 중복 구현 금지. Lombok 전면 사용(`lombok.config`의 copyableAnnotations 제거 금지).
- **`application.yml` 주석 = 운영 정책 문서** — 설정 변경 전 해당 키 주석 확인, 변경 시 주석도 갱신.
- **CLAUDE/README/구축가이드/PLAN/Design/콘텐츠가이드 외 `.md` 임의 생성 금지** — 사용자 명시 요청 시만.

## 트랜잭션 함정 (실제 장애 이력)

- 클래스 레벨 `readOnly=true`는 메서드에 **상속**됨 — 쓰기 override 누락 시 "Connection is read-only" 실패 또는 조용한 롤백.
- **자기호출** `this.txMethod()`는 프록시 우회로 `@Transactional` **무시** — 별도 빈 분리 또는 호출측 전파 설정.
- 긴 외부호출(@Async 업로드·폴링)은 `NOT_SUPPORTED`/`REQUIRES_NEW`로 격리.
- 애노테이션 변경은 hot-swap 불가 — 완전 정지 → Rebuild → Run. **DevTools 금지**(Virtual Threads+HikariCP 조합 Windows JVM 크래시 이력).

## UI / 프런트엔드 (이 프로젝트의 신규 기준)

- **KRDS 디자인 시스템만 사용** ([Design.md](Design.md)): 시맨틱 토큰(`bg-surface`·`text-fg-subtle`·`border-line`)·`.krds-*` 프리셋 우선. **raw hex·Tailwind 기본색(`bg-blue-500`)·기본 타이포(`text-xl`) 금지.** 타이포는 `text-{display|heading|body|label}-*`, 굵기 400/500/700만, radius 최대 12px.
- 테마: `<html>`의 `theme-*` 클래스 / `--brand-*` 스왑 포인트 — `tb_site` 테마 설정과 연동. 고대비 `hc` 모드 지원.
- **htmx + 순수 자바스크립트만 — Alpine 등 JS 프레임워크 금지** (확정 결정). 이벤트 위임(document 1회 등록 + `closest('[data-action]')`) 기본, 요소별 초기화는 `htmx:load`에서 멱등 처리(`data-initialized` 가드). 인라인 스크립트/핸들러 금지, 외부 `.js` + CSP nonce, self-host(CDN 금지). 뒤로가기 민감 화면은 `hx-history="false"`.
- 네이티브 요소 우선: 아코디언 `<details>/<summary>`, 모달 `<dialog>`.

## 보안 (웹쉘 침해 대응 경험 반영 — 보수적으로)

- 다중 SecurityFilterChain: admin(Order 10, IP 화이트리스트+2FA) / member(20) / default(100). 세션 `PCMS_SID`, `changeSessionId()`, `maximumSessions(1)`.
- 인가는 DB 기반 RBAC — `tb_role_url_access` + `DynamicAuthorizationManager`(priority ASC, **무매칭 DENY**). 새 URL 추가 시 접근 규칙 등록 필수(빠뜨리면 화면이 안 열림).
- 회원 인가: `tb_member_role` 미사용 — `AUTHENTICATED` + `user_type=MEMBER`. 통합 로그인 `v_user_login` VIEW.
- 파일 업로드 6중 방어(확장자/Tika 매직바이트/격리/재인코딩/FIM/ClamAV), CSRF(XSRF-TOKEN 쿠키), CSP nonce(+HTML `no-store`), OWASP Sanitizer, 로그인 잠금(5회/30분), Bucket4j.
- PII: `@Encrypt`(AES-256-GCM, `{AG}` 프리픽스) + `MaskUtils` 마스킹 + `log_privacy` 기록. 마스터키 `PCMS_PII_MASTER_KEY` fail-fast.

## DDL / 마이그레이션

**Flyway 사용.** 앱 계정 DDL 권한 없음 — 스키마 변경은 `sql/{mariadb,mysql,postgres}/` 드라이버별 파일 갱신 후 DBA 집행(로컬은 직접 실행). 코드에서 DDL 실행/스키마 변경 가정 금지. 신규 테이블은 UUID v7 PK + 감사컬럼 6종 + 3벤더 DDL 동시 작성.

## 사이트 데모 5종 세트

새 사이트 데모는 **레이아웃 + 콘텐츠 + 컨트롤러 + 보안(tb_role_url_access) + 시드** 5종을 한 세트로 생성(구축가이드 §7). 참조 표준: 이전 프로젝트 `templates/front/layouts/{AIRBNB,CLAY}/`. 레이아웃만 만들고 시드·URL 접근 규칙을 빠뜨리는 것이 가장 흔한 실수.

## 배치 / 스케줄러

`com.gonet.scheduler`, ShedLock(logging DB `shedlock` 테이블, `defaultLockAtMostFor=PT30M`). 회원 생명주기: 활성 → 휴면(01:00) → 탈퇴 퍼지(04:45). 로그 보존 03:30, soft-delete 정리 04:30, 파일 퍼지 04:00. 파기 이력은 `tb_pii_purge_log`.

## 참고

- 이전 프로젝트 원본: `D:\claude\gopcms5` (규약 정본: 해당 저장소의 `CLAUDE.md`·`기술설계서.md`). 이식 시 실측 코드가 문서보다 우선.
- NICE 본인인증 jar는 JPMS 플래그 필요(`--add-exports/--add-opens java.base/com.sun.crypto.provider=ALL-UNNAMED`) — surefire·spring-boot:run·운영 Tomcat setenv 모두.
- 엔드포인트 테스트: `http/` IntelliJ HTTP Client 세트 — `01-auth.http`에서 CSRF 발급→로그인→세션 쿠키.
