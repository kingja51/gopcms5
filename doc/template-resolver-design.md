# gopcms5 — 사이트 템플릿 Resolver 설계서

| 항목 | 값 |
|---|---|
| 작성일 | 2026-07-28 |
| 전제 | Spring Boot 3.5.x · Java 21 · Thymeleaf + layout-dialect · htmx · Tailwind v4(KRDS, [SG-krds.md](../design-md/styleguide/SG-krds.md)) · eGov RTE 5.0.0 |
| 원전 | [wireframe/](../wireframe/) 7종(A~G안) — IA·라우트·데이터 동일, 프레젠테이션 계층만 상이 |
| 핵심 요구 | ① 사이트 기준으로 템플릿을 선택 ② 선택은 **Resolver 단계**에서 해석 ③ 선택된 레이아웃이 화면 전체에 적용 |

---

## 1. 개념 모델

```
템플릿(tb_template)  1 ──── N  사이트(tb_site)  1 ──── N  메뉴/컨텐츠/게시판…
   frame001~007                site_code(ai·med·me…)      (템플릿과 무관 — 데이터 계층)
```

- **템플릿 = 와이어프레임 1개 안(frame00x) = 레이아웃 1벌 + 화면 오버라이드 + CSS 1장.**
  데이터(IA·menuTree·컨텐츠)는 템플릿과 완전히 분리 — 와이어프레임 전제("달라지는 것은
  프레젠테이션 계층뿐")를 그대로 시스템 불변식으로 삼는다.
- 사이트는 템플릿 하나를 선택(`tb_site.template_code`)하고, 테마(`tb_site.theme` —
  KRDS `theme-*`/`hc` 클래스)를 병행 선택한다. **템플릿=구조, 테마=색** 으로 축을 분리.
- 템플릿 전환은 데이터 변경 없이 `template_code` 값 변경만으로 즉시 이루어져야 한다(§6).

### 1.1 와이어프레임 → 템플릿 코드 매핑

| 템플릿 코드 | 원전 | 구조 요약 |
|---|---|---|
| `TPL-A-BAND` | frame001 | 표준 GNB 밴드형(기준안) — 호버 드롭다운, 본문 1단 와이드 |
| `TPL-B-MEGA` | frame002 | 콤팩트 헤더 + 메가메뉴 오버레이 + 좌 LNB 2단 |
| `TPL-C-SIDE` | frame003 | 좌측 고정 다크 사이드바 GNB(LNB 겸용), 본문 풀폭 |
| `TPL-D-MAGAZINE` | frame004 | 원페이지 풀블리드 매거진 — 오버레이 헤더 + 풀스크린 메뉴 |
| `TPL-E-BENTO` | frame005 | 포털형 벤토 대시보드 — 위젯 카드, htmx 개별 로드 |
| `TPL-F-APP` | frame006 | 모바일 퍼스트 앱형 — 하단 탭바 5개, 데스크톱 협폭 |
| `TPL-G-GOV` | frame007 | 공공기관 정석 3단 헤더 + 좌 LNB 3뎁스(접근성 보수 기준) |

### 1.2 화면 유형 계약 (전 템플릿 공통 — 와이어프레임 8종 그대로)

`layout`(프레임) · `home` · `content` · `program` · `board/**` · `member/**` ·
`search`/`sitemap` · `common/**`(학사일정·알림함·계정찾기·설문·민원·파일뷰어·오류)

메뉴 뎁스 규칙(전 안 공통): GNB/LNB 는 1~3뎁스, 사이트맵만 1~4뎁스 — 동일 menuTree 를
렌더 뎁스만 달리해 재사용. 4뎁스는 3뎁스 페이지 내 탭/필터.

---

## 2. 해석(Resolution) 아키텍처 — 요청 흐름

템플릿 결정을 **컨트롤러가 모르게** 하는 것이 설계의 축이다. 컨트롤러는 논리 뷰명만
반환하고, 물리 템플릿 경로는 ViewResolver 단계에서 결정한다.

```
요청 http://ai.example.ac.kr/…  (또는 /{sc}/…)
  │
  ① SiteResolveFilter            Host(서브도메인) → 실패 시 path {sc} → tb_site 조회(Caffeine)
  │                              → SiteContext{siteCode, templateCode, theme, menuTree…}
  │                              → ThreadLocal(SiteContextHolder) + request attribute 바인딩
  ▼
  ② Controller (eGov MVC 규칙)   비즈니스는 서비스 인터페이스 호출, return "front/board/list"
  │                              ※ 템플릿 코드를 전혀 알지 못함 — 논리 뷰명 계약만 사용
  ▼
  ③ SiteTemplateViewResolver     ★ Resolver 단계 — 요구사항의 처리 지점 ★
  │    "front/**" 뷰명만 대상:
  │      front/board/list → tpl/{templateCode}/board/list    (존재 시)
  │                       → tpl/_default/board/list          (폴백)
  │    "adm/**"(관리자)·redirect:·forward: 는 재작성 없이 통과
  │    존재 여부는 Caffeine 캐시(키: templateCode+뷰명, 운영 TTL 장기·개발 무캐시)
  ▼
  ④ Thymeleaf 렌더               페이지 첫 줄: layout:decorate="~{${siteLayout}}"
  │                              siteLayout = "tpl/{templateCode}/layout"  (ModelAdvice 주입)
  │                              → 어떤 페이지가 어느 폴더에서 오든 사이트의 레이아웃이 감쌈
  ▼
  ⑤ 응답                         <html class="theme-… [hc]"> — tb_site.theme 단일 경로
```

### 2.1 설계 결정과 근거

| 결정 | 근거 |
|---|---|
| **ViewResolver 재작성** (TemplateResolver 훅이 아닌) | Thymeleaf 캐시는 템플릿 이름이 키다. 같은 이름 "home"을 사이트마다 다른 리소스로 해석하면 캐시가 오염된다. 뷰명 자체를 `tpl/{code}/…` 물리명으로 재작성하면 캐시 키가 자연히 분리되어 안전하다. |
| **2단 폴백** (`tpl/{code}` → `tpl/_default`) | 템플릿은 layout + 차별화 화면만 갖고, 나머지는 공용 1벌만 유지. 신규 템플릿 추가 = layout.html + CSS 1장 + 필요한 오버라이드만 작성. |
| **레이아웃은 폴백 없음** (템플릿 필수 파일) | 레이아웃이 곧 템플릿의 정체성(7안의 차이 지점). `_default` 레이아웃 폴백을 허용하면 "선택한 템플릿이 적용되지 않았는데 화면은 뜨는" 침묵 실패가 생긴다 — 기동 시 검증(§5 ArchUnit/스모크)으로 강제. |
| **`layout:decorate="~{${siteLayout}}"` 동적 지정** | 공용(_default) 페이지도 사이트별 레이아웃으로 감싸져야 하므로 정적 경로 불가. layout-dialect 는 decorate 식 평가를 지원. |
| **ThreadLocal + request attribute 이중 바인딩** | 렌더 파이프라인(Resolver·Advice·프래그먼트)은 ThreadLocal, htmx 부분요청·비동기 경계는 request attribute 로 안전망. Virtual thread(풀 없음·요청당 1스레드) 전제라 누수 위험이 낮지만 filter finally 에서 반드시 clear. |

### 2.2 컴포넌트 설계 (구현 스켈레톤 — 설계 참조용)

```java
/* ① 사이트 컨텍스트 — 불변 record, Caffeine "siteContext" 캐시(키 siteCode) */
public record SiteContext(
        Long siteId, String siteCode, String siteName,
        String templateCode,      // tb_site.template_code → tpl/{templateCode}/**
        String theme,             // "", "theme-indigo", "theme-teal hc" …
        List<MenuNode> menuTree   // tb_menu depth 1~4 (GNB 1~3뎁스 / 사이트맵 4뎁스 렌더)
) {}

/* ② 필터 — Host 서브도메인 우선, /{sc} path 폴백. 미해석 시 기본 사이트 or 404 */
public class SiteResolveFilter extends OncePerRequestFilter {
    // resolve() → SiteContextHolder.set(ctx); try { chain } finally { clear() }
    // 프리뷰: 세션 PREVIEW_TMPL 존재 시(관리자 한정) templateCode 오버라이드 (§6.2)
}

/* ③ ★ Resolver 단계 — 요구사항 처리 지점 ★ */
public class SiteTemplateViewResolver extends ThymeleafViewResolver {
    @Override protected View createView(String viewName, Locale locale) {
        if (!viewName.startsWith("front/")) return super.createView(viewName, locale);
        String ctx  = SiteContextHolder.get().templateCode();       // 예: TPL-C-SIDE
        String rest = viewName.substring("front/".length());        // 예: board/list
        String phys = exists(ctx, rest)                             // Caffeine "viewExists"
                    ? "tpl/%s/%s".formatted(ctx, rest)
                    : "tpl/_default/" + rest;
        return super.createView(phys, locale);
    }
    // exists(): classpath:/templates/tpl/{code}/{rest}.html 리소스 존재 검사(캐시)
}

/* ④ 모델 어드바이스 — 모든 front 컨트롤러에 공통 모델 주입 */
@ControllerAdvice(basePackages = "com.gonet.gopcms.front")
public class SiteContextModelAdvice {
    // siteLayout = "tpl/" + templateCode + "/layout"   ← layout:decorate="~{${siteLayout}}"
    // site(SiteContext), menuTree, themeClass, currentUri(활성 메뉴 prefix 매칭)
}
```

등록: `ThymeleafViewResolver` 자리에 `SiteTemplateViewResolver` 를 교체 빈으로 구성
(order 동일). 관리자(`adm/**`)·오류 뷰는 재작성 대상 제외라 기존 동작 그대로.

---

## 3. 템플릿 디렉토리 규약

```
src/main/resources/templates/
├─ tpl/
│  ├─ _default/                    ← 공용 1벌 (폴백 종점, 항상 완비)
│  │  ├─ home.html  content.html  program.html
│  │  ├─ board/ {list,view,write,skin-*}.html
│  │  ├─ member/ {login,join-*,find,mypage}.html
│  │  ├─ search.html  sitemap.html
│  │  └─ common/ {schedule,notification,survey,minwon,file-viewer,error}.html
│  ├─ TPL-A-BAND/                  ← frame001 (기준안)
│  │  ├─ layout.html               ★ 필수 — 템플릿 정체성 (폴백 금지)
│  │  └─ home.html                 (차별화 화면만 오버라이드)
│  ├─ TPL-C-SIDE/
│  │  ├─ layout.html               (사이드바 프레임 — 와이어프레임 C안)
│  │  ├─ home.html  board/list.html(2-pane)
│  │  └─ …
│  └─ …(7종)
├─ fragments/                      ← 템플릿 무관 공용 조각
│  │   (breadcrumb, site-footer, bbs-like-report, popup-overlay, file-viewer-popup…)
└─ adm/**                          ← 관리자 화면 — 템플릿 스위칭 대상 아님

src/main/resources/static/
├─ css/output.css                  ← KRDS 전역(토큰+유틸+krds-* 프리셋) — src/krds.css 산출
└─ tmpl/css/{templateCode}.css     ← 템플릿별 구조 CSS 1장 (레이아웃 grid·크롬·시그니처)
```

**로드 순서(레이아웃 `<head>`)**: ① `/css/output.css` ② `/tmpl/css/{templateCode}.css`
③ `<html class="${themeClass}">` — SG 체계(전역 토큰 → 템플릿 램프 → 테마 클래스) 그대로.

**레이아웃 계약(7종 공통 의무 슬롯)** — 와이어프레임 A안 해부도 기준:
스킵링크 · 마스트헤드 · GNB(menuTree 1~3뎁스) · SUB_HERO 슬롯 · breadcrumb ·
`layout:fragment="content"` · 좋아요/신고 밴드 · 푸터 · `layout:fragment="scripts"` 슬롯.
구조(밴드형/사이드바형/탭바형)는 자유, **슬롯 유무와 데이터 계약은 고정** — 이 계약 덕에
_default 페이지가 어느 레이아웃에 끼워져도 동작한다.

---

## 4. DB 설계 (템플릿 축 핵심)

```sql
-- 템플릿관리
CREATE TABLE tb_template (
  template_code  VARCHAR(30) PRIMARY KEY,     -- TPL-A-BAND …
  template_name  VARCHAR(100) NOT NULL,       -- "표준 GNB 밴드형"
  wireframe_ref  VARCHAR(30),                 -- frame001 (원전 추적)
  thumbnail_path VARCHAR(255),                -- 관리자 선택 UI 미리보기 이미지
  css_path       VARCHAR(255) NOT NULL,       -- /tmpl/css/TPL-A-BAND.css
  descript       VARCHAR(500),
  use_yn         CHAR(1) DEFAULT 'Y',
  sort_no        INT,
  reg_dt, reg_id, mod_dt, mod_id
);

-- 사이트관리 (템플릿 선택 지점)
CREATE TABLE tb_site (
  site_id        BIGINT PRIMARY KEY,          -- fdl.idgnr 채번
  site_code      VARCHAR(20) UNIQUE NOT NULL, -- ai · med · me … (서브도메인/path 겸용)
  site_name      VARCHAR(100) NOT NULL,
  domain         VARCHAR(100),                -- ai.example.ac.kr (Host 매칭용, null 허용)
  template_code  VARCHAR(30) NOT NULL REFERENCES tb_template,  -- ★ 선택된 템플릿
  theme          VARCHAR(50) DEFAULT '',      -- '' | theme-indigo | theme-teal … [+ hc]
  use_yn         CHAR(1) DEFAULT 'Y',
  reg_dt, reg_id, mod_dt, mod_id
);
```

나머지 7개 기능 테이블(tb_menu · tb_content · tb_bbs_master/article · tb_admin ·
tb_member · tb_dept · tb_staff)은 전부 `site_id`(또는 공용+site 매핑)만 참조하고
**template_code 를 참조하지 않는다** — 템플릿 전환 시 데이터 무변경 불변식의 물리적 근거.

---

## 5. CMS 9개 기능 모듈 맵 — 템플릿 축과의 관계

| 기능 | 패키지(안) | 템플릿 축과의 관계 |
|---|---|---|
| 템플릿관리 | `adm.template` | tb_template CRUD + 썸네일 + **미리보기(§6.2)**. 화면 파일 자체는 배포 산출물(등록·수정은 개발 배포로만) |
| 사이트관리 | `adm.site` | 템플릿 선택 드롭다운(+썸네일) · 테마 선택 → 저장 시 SiteContext 캐시 evict(§6.1) |
| 메뉴관리 | `adm.menu` | menuTree 편집(depth 1~4) — 렌더는 템플릿 몫, 데이터는 무관 |
| 컨텐츠관리 | `adm.content` | slug 기반 페이지 — `front/content` 뷰 계약으로 전 템플릿 공통 |
| 게시판관리 | `adm.bbs` | 스킨(NOTICE·GALLERY 등)은 _default 하위 board/skin-* — 템플릿이 목록 형태(테이블/카드/2-pane)만 오버라이드 |
| 관리자 회원 | `adm.account` | `adm/**` 화면 — 템플릿 스위칭 제외 대상 |
| 사용자 회원 | `front.member` | member/** 뷰 계약 — 전 템플릿 공통(_default), 필요 시 오버라이드 |
| 부서 관리 | `adm.dept` | 조직 데이터 — program(교수진·직원) 화면의 데이터 소스 |
| 직원관리 | `adm.staff` | tb_staff — prg/{staff}/{sc} 조각의 데이터 소스 |

eGov 호환 각주: 전 모듈 Controller → **서비스 인터페이스** → `*ServiceImpl extends
EgovAbstractServiceImpl`(공통 추상 `AbstractCmsService` 경유 권장) → `@EgovMapper` +
eGov `MapperConfigurer`([호환성 분석](../doc/pom.xml) 및 대화 기록 기준). Resolver·Filter 는
아키텍처 점검 대상(Controller/Service/DAO) 밖이라 자유 설계 영역.

---

## 6. 운영 시나리오

### 6.1 템플릿 전환(즉시 반영)
사이트관리 저장 → `tb_site.template_code` UPDATE → `siteContext` 캐시 evict(siteCode)
→ 다음 요청부터 Resolver 가 새 코드로 재작성. 뷰 존재 캐시(`viewExists`)는 코드+뷰명
키라 무효화 불필요. Thymeleaf 캐시도 물리명 분리라 무효화 불필요. **재기동 없음.**

### 6.2 템플릿 미리보기(운영 사이트 무영향)
관리자 권한 + `?tmpl=TPL-C-SIDE` → 세션 sticky(PREVIEW_TMPL) → SiteResolveFilter 가
해당 세션에만 templateCode 오버라이드 → `?tmpl=off` 해제. 일반 사용자는 영향 없음.
템플릿관리 화면의 [미리보기] 버튼 = 이 쿼리를 붙인 새 창.

### 6.3 기동 시 검증(침묵 실패 방지)
ApplicationRunner 스모크: use_yn='Y' 인 모든 tb_template 에 대해
`templates/tpl/{code}/layout.html` + `static/tmpl/css/{code}.css` 존재 검사 — 실패 시
기동 중단. `tpl/_default` 완비(화면 유형 전체) 여부는 테스트로 고정.

### 6.4 개발 순서 권고
① `_default` 완비 + `TPL-A-BAND`(기준안) 레이아웃 → ② Resolver·프리뷰 파이프라인 검증
→ ③ 구조가 가장 다른 `TPL-C-SIDE`(사이드바)·`TPL-F-APP`(탭바)로 레이아웃 계약 검증
→ ④ 나머지 4종 양산. 계약이 C·F안에서 버티면 전 템플릿에서 버틴다.

---

## 7. 결정 요약 (한 줄씩)

1. 템플릿 선택 = `tb_site.template_code` 한 컬럼 — 데이터와 프레젠테이션 완전 분리.
2. 해석 지점 = **ViewResolver**(`SiteTemplateViewResolver`) — 캐시 안전한 뷰명 재작성.
3. 폴백 = `tpl/{code}` → `tpl/_default` 2단, 단 layout 은 폴백 금지(기동 검증).
4. 레이아웃 적용 = `layout:decorate="~{${siteLayout}}"` 동적 지정 + 공통 슬롯 계약.
5. CSS = 전역 KRDS output.css + 템플릿 구조 CSS 1장 + 테마 html 클래스 — 3층 분리.
6. 전환·미리보기 = 캐시 evict / 세션 sticky — 재기동·데이터 이관 없음.
