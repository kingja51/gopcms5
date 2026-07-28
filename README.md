# gopcms5

> **전자정부 표준프레임워크(eGovFrame) 5.0 호환 · KRDS UI 기반 멀티사이트 CMS**
> 대학교 · 연구소 · 행정기관 홈페이지를 하나의 시스템에서 사이트 단위로 운영한다.

1인 개발 프로젝트 · 현재 **설계 단계** (와이어프레임 → 스타일가이드 → 아키텍처 설계 완료, 구현 착수 전)

---

## 1. 기술 스택

| 계층 | 선택 | 비고 |
|---|---|---|
| Runtime | Java 21 LTS (Loom virtual threads) | `spring.threads.virtual.enabled=true` |
| Framework | Spring Boot 3.5.x (war, 외부 Tomcat 10.1.x) | Spring Framework 6.2.x |
| 표준프레임워크 | **eGovFrame RTE 5.0.0** | 호환성 필수 4종 + idgnr, [§5](#5-egovframe-호환성-규칙) |
| Persistence | MyBatis (mybatis-spring-boot 3.0.x) | `@EgovMapper` + eGov MapperConfigurer |
| DB | **MariaDB 11.8 (기본)** / PostgreSQL 병행 | 3-DB 분리: primary(`tb_*`)·secondary(`tn_*`)·logging(`log_*`/`stat_*`), VIEW `vw_*` |
| Migration | Flyway (`db/migration/{db}/{vendor}`) | DB별 이력 독립 — [doc/flyway-migration.md](doc/flyway-migration.md) |
| View | Thymeleaf + layout-dialect | 네이티브 `<dialog>`/`<details>` 규약 |
| Frontend | **htmx + 순수 JS** (SPA 없음) | CSP nonce, 인라인 스크립트 금지 |
| CSS | **Tailwind CSS v4 CLI + KRDS 디자인 토큰** | [design-md/styleguide/SG-krds.md](design-md/styleguide/SG-krds.md) |
| 빌드 | Maven (exec-maven-plugin → `npm run css`) | [pom.xml](pom.xml) · JDK 21 필요 |

## 2. 주요 기능 (9개 모듈)

**템플릿관리 · 사이트관리 · 메뉴관리 · 컨텐츠관리 · 게시판관리 · 관리자회원 · 사용자회원 · 부서관리 · 직원관리**

모든 데이터는 사이트(`site_code`) 기준으로 귀속되며, 사이트는 템플릿(레이아웃)과
테마(색)를 독립적으로 선택한다.

## 3. 핵심 아키텍처 — 사이트별 템플릿 Resolver

컨트롤러는 템플릿을 모른다. 논리 뷰명(`front/**`)만 반환하면 **ViewResolver 단계**에서
사이트의 선택 템플릿으로 물리 경로를 재작성한다. → 상세: [doc/template-resolver-design.md](doc/template-resolver-design.md)

```
요청 → SiteResolveFilter (Host/path → SiteContext, Caffeine)
     → Controller: return "front/board/list"           (템플릿 무지)
     → SiteTemplateViewResolver:                        ★ 해석 지점 ★
         tpl/{templateCode}/board/list (존재 시) → tpl/_default/board/list (폴백)
     → Thymeleaf: layout:decorate="~{${siteLayout}}"    (사이트 레이아웃이 감쌈)
```

- 템플릿 = 레이아웃 1벌 + 차별화 화면 오버라이드 + 구조 CSS 1장. 데이터와 완전 분리.
- 전환 = `tb_site.template_code` 변경 + 캐시 evict — **재기동·데이터 이관 없음**.
- 미리보기 = 관리자 한정 `?tmpl=` 세션 sticky (운영 사용자 무영향).

### 레이아웃 7종 (구조 — 와이어프레임 A~G안, V2 시드)

| 코드 | 원전 | 구조 |
|---|---|---|
| `layout-001` | [frame001](wireframe/frame001/index.html) | 표준 GNB 밴드형 (기준안) |
| `layout-002` | [frame002](wireframe/frame002/index.html) | 콤팩트 헤더 + 전체메뉴(사이트맵 링크) + LNB 2단 |
| `layout-003` | [frame003](wireframe/frame003/index.html) | 좌측 고정 다크 사이드바 GNB |
| `layout-004` | [frame004](wireframe/frame004/index.html) | 원페이지 풀블리드 매거진 |
| `layout-005` | [frame005](wireframe/frame005/index.html) | 포털형 벤토 대시보드 |
| `layout-006` | [frame006](wireframe/frame006/index.html) | 모바일 퍼스트 앱형 (하단 탭바) |
| `layout-007` | [frame007](wireframe/frame007/index.html) | 공공기관 정석 3단 헤더 + 좌 LNB |

7종 모두 IA·라우트·menuTree·데이터 구조가 동일하며 프레젠테이션 계층만 다르다.
전체 목차: [wireframe/index.html](wireframe/index.html)

### 템플릿 8종 (시각 언어 — CSS 1장, V2·V3 시드)

| 코드 | 원전 | 계열 | 테마 | 기본 레이아웃 |
|---|---|---|---|---|
| `krds` | SG-krds (순정) | 범용 기본 | blue·teal·indigo·green | layout-001 |
| `blueprint-001` | DESIGN-ibm | 연구소·공대 | blue·mono·teal·green | layout-001 |
| `trust-002` | DESIGN-stripe | 행정·재정 | purple·navy·teal | layout-007 |
| `gallery-003` | DESIGN-apple | 대학 홍보·입학처 | blue·graphite | layout-004 |
| `paper-004` | DESIGN-claude | 인문대·도서관·교양 | terracotta·olive·sepia | layout-002 |
| `vista-005` | DESIGN-tesla | 대학 캠퍼스 홍보 | blue·mono | layout-004 |
| `festival-006` | DESIGN-renault | 모집·행사 캠페인 | noir·blue·violet (옐로는 point 전용) | layout-005 |
| `midnight-007` | DESIGN-linear.app | IT 대학원·연구소 서브 | violet·blue | layout-003 |

템플릿 코드는 브랜드명 대신 무드명+순번. 색상 램프는 DB 가 아닌 템플릿 CSS
(`/tmpl/css/{code}.css`) 구현 시 원전 design-md 팔레트로 작성한다(brand-50 대비 4.5:1 검증).

## 4. 디자인 시스템 — KRDS × Tailwind v4

범정부 디자인시스템([KRDS](https://github.com/KRDS-uiux/krds-uiux)) 토큰을 Tailwind v4
CLI 로 이식. 색상 램프(브랜드/포인트 11단계) · 타이포 스케일 · radius · 엘리베이션 ·
`krds-*` 컴포넌트 프리셋 30여 종을 단일 입력 CSS([src/krds.css](src/krds.css))로 정의.

- **스타일가이드(브라우저로 열기)**: [design-md/styleguide/SG-krds.html](design-md/styleguide/SG-krds.html)
- **레시피 문서**: [design-md/styleguide/SG-krds.md](design-md/styleguide/SG-krds.md)
- 리브랜딩 = `:root` 의 `--brand-*` 11단계 스왑만으로 완결 (재빌드 불필요)
- 테마: `theme-teal / theme-indigo / theme-green` + 고대비 `hc` — html 클래스 전환
- 시각 언어 후보(템플릿 무드 참고): [design-md/DESIGN-*.md](design-md/) 12종

```bash
# CSS 빌드 (Node 필요 — npm install 1회)
npm run css        # src/krds.css → dist/krds.css (minify)
npm run css:watch  # 개발 감시
npm run css:sg     # 스타일가이드 페이지 빌드
```

## 5. eGovFrame 호환성 규칙

호환성확인 가이드라인(2026-06-22) 기준 — 상세 근거는 [pom.xml](pom.xml) 주석 참조.

| 규칙 | 적용 |
|---|---|
| 필수 4종 동일 버전 | `ptl-mvc` `fdl-cmmn` `psl-dataaccess` `fdl-logging` = 5.0.0 (+선택 `fdl-idgnr`) |
| fdl-logging 충돌 | jar 는 포함(해시 무결), transitive `log4j-core`/`log4j-slf4j2-impl` 만 제외 → Logback 일원화 |
| Spring 버전 | Boot 3.5.x patch 라인만 상향 허용 — **3.6/4.x 업그레이드 금지** |
| MVC 규칙 | Controller 는 서비스 **인터페이스**만 주입, DAO/Mapper 직접 호출 금지 |
| 서비스 규칙 | `*ServiceImpl extends EgovAbstractServiceImpl` (공통 추상 클래스 경유 간접 상속) |
| 데이터 액세스 | Mapper 인터페이스 + eGov `MapperConfigurer` + `@EgovMapper` |
| 확장 규칙 | 자체 클래스는 `org.egovframe.rte` 패키지 금지 · `Egov` 접두 클래스명 금지 |

## 6. 저장소 구조

```
gopcms5/
├─ pom.xml                     Maven 빌드 (eGov 호환 의존성 — P0 승격 완료)
├─ src/main/java/com/gonet/    {common·config·primary·secondary·logging·scheduler}
├─ doc/                        설계 문서
│  ├─ template-resolver-design.md   템플릿 Resolver 설계서
│  ├─ conventions.md           식별자(PK)·네이밍 규약 (접두어 레지스트리)
│  └─ flyway-migration.md      Flyway SQL 마이그레이션 규약
├─ src/main/resources/db/      Flyway 마이그레이션 (migration/{db}/{vendor} + devdata/{db})
├─ design-md/                  디자인 문서
│  ├─ DESIGN-*.md              시각 언어 후보 12종
│  └─ styleguide/              KRDS 스타일가이드 (SG-krds.*)
├─ wireframe/                  레이아웃 설계안 A~G (frame001~007)
├─ src/krds.css                KRDS 디자인 토큰 (Tailwind v4 CLI 입력)
└─ package.json                CSS 빌드 스크립트
```

## 7. 로드맵

- [x] eGov 5.0 호환 의존성 설계 (pom)
- [x] KRDS × Tailwind v4 스타일가이드
- [x] 와이어프레임 7종 + 템플릿 Resolver 설계
- [ ] Maven 프로젝트 스캐폴딩 + `_default` 템플릿 + `TPL-A-BAND` 구현
- [ ] Resolver 파이프라인 + 템플릿 프리뷰 구현 (`TPL-C-SIDE`·`TPL-F-APP` 로 계약 검증)
- [ ] 9개 관리 모듈 구현 → 나머지 템플릿 양산
- [ ] eGovFrame 호환성 확인 신청

## License

Private project — © 2026 kingja51. All rights reserved.
