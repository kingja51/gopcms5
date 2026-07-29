# 사이트·템플릿·테마·메뉴·컨텐츠 개발 매뉴얼

gopcms5 의 **화면이 결정되는 축**(P1~P4)을 다룬다. 요청 하나가 어떤 사이트로 해석되고,
어떤 레이아웃·CSS·색으로 렌더되며, 메뉴와 컨텐츠가 어떻게 그 위에 얹히는지를 적는다.

- 기준일: 2026-07-30
- **설계 배경·의사결정 근거**는 [template-resolver-design.md](template-resolver-design.md)
  가 정본이다. 이 문서는 그 설계가 **실제로 어떻게 구현되어 있는지**를 다룬다 — 겹치면
  설계서를 먼저 읽고 여기서 코드를 확인한다.
- 그 밖: [conventions.md](conventions.md) §5(URL 계약) · [PLAN.md](../PLAN.md) §P1~P4

> 이 문서는 **구현된 것만** 적는다. 코드와 어긋나면 코드가 정답이다.

---

## 0. 3축 모델 — 한 문장씩

| 축 | 무엇을 정하는가 | 실체 | 테이블 |
|---|---|---|---|
| **layout** | **구조** — 헤더·GNB·본문·푸터의 배치 | 뷰 폴더 `templates/layouts/{layout_code}/` | `tb_layout` |
| **template** | **시각 언어** — 타이포·간격·컴포넌트 | CSS 1장 `/tmpl/css/{template_code}.css` | `tb_template` |
| **theme** | **색** — 브랜드 팔레트 | `<html>` 클래스 문자열 (**파일 없음**) | `tb_theme` |

사이트가 이 셋을 각각 고른다(`tb_site.layout_id / template_id / theme_id`).
**셋이 독립**이라 조합이 곱으로 늘어난다 — 레이아웃 7종 × 템플릿 3종 × 테마 N개.

> 테마에 파일이 없는 것이 핵심이다. 색을 바꾸려고 CSS 파일을 새로 만들면 파일이 무한히
> 늘어난다. `html` 에 클래스 하나를 더하고 그 안에서 `--brand-*` 변수를 갈아 끼우면
> 같은 CSS 한 장이 모든 색을 감당한다(SG-krds.md 리브랜딩 규약).

---

## 1. 요청 해석 파이프라인

```
요청 /{siteCode}/{slug}
  │
  ├─ ① SiteResolveFilter          경로 → SiteContext 바인딩 (ThreadLocal)
  │     · UrlNamespaces 로 건너뛸 경로 판별
  │     · 사이트코드 자리 → 쿼리 파라미터 → 기본 사이트 순으로 해석
  │
  ├─ ② 컨트롤러                    논리 뷰명만 반환 — "front/content"
  │     · 템플릿·레이아웃을 <b>모른다</b>
  │
  ├─ ③ SiteContextModelAdvice     site·siteLayout·menuTree·themeClass·cspNonce 주입
  │
  ├─ ④ SiteTemplateViewResolver   front/** → 물리 경로 3단 폴백
  │     · sites/{siteCode}/… → layouts/{layoutCode}/… → layouts/_default/…
  │
  └─ ⑤ 렌더                        layout:decorate="~{${siteLayout}}"
```

### 1.1 ① 사이트 해석 — `SiteResolveFilter`

`@Order(HIGHEST_PRECEDENCE + 20)` — `AccessLogFilter`(+10) 안쪽.

해석 순서:

1. **경로의 사이트코드 자리** — 자리가 경로마다 다르다([§1.2](#12-url-네임스페이스--단일-원천))
2. **`siteCode` 쿼리 파라미터** — `/login?siteCode=ai` 처럼 사이트 무관 경로용
3. **기본 사이트**(`default_yn='Y'`) 폴백

해석 결과는 `SiteContextHolder`(ThreadLocal) + request attribute 에 담기고,
`finally` 에서 반드시 `clear()` 한다.

> 이 필터는 감사 IP 를 세우지 **않는다**. `/adm/**` 등을 건너뛰기 때문에 여기서 세우면
> 관리자 쓰기의 `updated_ip` 가 비어 버린다 — `AuditorContextFilter` 가 담당한다.

### 1.2 URL 네임스페이스 — 단일 원천

`common/web/UrlNamespaces` 가 첫 세그먼트의 **유일한 기준**이다. 이 목록이 흩어지면
하나만 빠졌을 때 **조용히** 깨진다.

| 구분 | 값 | 의미 |
|---|---|---|
| `SKIP` | `adm` `api` `actuator` `error` `favicon.ico` `css` `js` `fonts` `images` `tmpl` `webjars` `swagger-ui` `v3` `file` | 사이트 개념이 없는 자리 — 해석을 건너뛴다 |
| `PROGRAM` | `bbs` `prg` | 여러 사이트가 같은 화면을 공유 — 사이트코드가 **두 번째** |
| `RESERVED` | 위 둘 + `index` `sitemap` `search` `member` | site_code·slug 로 쓸 수 없다 |

**자리 순서가 반대인 것은 의도다.**

```
컨텐츠 : /{siteCode}/{slug}        사이트마다 slug 가 다른 고유 페이지 → 사이트가 앞
프로그램: /bbs/{siteCode}/{bbsCode}  사이트마다 화면이 같고 데이터 범위만 다름 → 프로그램이 앞
```

`siteCodeSegment()` 가 이 판단을 한 곳에서 한다. `segment()` 는 빈 마디를 건너뛴다 —
`//bbs//ai/` 같은 경로가 다른 판정을 받으면 우회로가 된다.

> **새 프로그램을 열 때 `PROGRAM` 에 추가하는 것을 잊으면** 그 URL 이 사이트코드로
> 해석돼 404 가 된다.

### 1.3 ④ 뷰 해석 — `SiteTemplateViewResolver`

`front/**` 만 재작성한다. `adm/**`·`redirect:`·`forward:` 는 통과.

```
front/index
  ① sites/{siteCode}/index        사이트 전용 — 랜딩처럼 사이트마다 다른 커스텀 HTML
  ② layouts/{layoutCode}/index    레이아웃 오버라이드
  ③ layouts/_default/index        공용 1벌
```

현재 사이트 전용 층이 있는 사이트: `sites/ai`, `sites/nursingcollege`.

> **뷰 캐시는 반드시 꺼야 한다**(`ThymeleafViewConfig.setCache(false)`).
> `AbstractCachingViewResolver` 가 `front/board/list` **원본 이름**으로 View 를 캐시하면
> 첫 요청 사이트의 레이아웃이 전 사이트에 고정된다. Thymeleaf 템플릿 캐시(물리명 키)가
> 성능을 담당하므로 비용은 미미하다.

### 1.4 ③ 모델 주입 — `SiteContextModelAdvice`

| 모델 키 | 값 | 뷰에서 |
|---|---|---|
| `site` | `SiteContext` (**null 가능**) | `${site != null ? site.siteName : ''}` |
| `siteLayout` | `layouts/{layoutCode}/layout` | `layout:decorate="~{${siteLayout}}"` |
| `menuTree` | `List<MenuNode>` | GNB·사이트맵 |
| `themeClass` | `<html>` 클래스 (`''` = 기본) | `th:classappend="${themeClass}"` |
| `currentUri` | 요청 URI | GNB 활성 표시 |
| `cspNonce` | 요청당 난수 | `th:attr="nonce=${cspNonce}"` |

**뷰는 `site` 가 null 일 수 있다는 전제로 쓴다.**

---

## 2. 사이트 (`tb_site`)

### 2.1 주요 컬럼

| 컬럼 | 설명 |
|---|---|
| `site_id` | `SIT_` + UUIDv7 |
| `site_code` | **URL 경로 식별자**. `^[a-z0-9][a-z0-9-]{1,29}$` + 예약어 금지 (둘 다 DB CHECK) |
| `domain` | 커스텀 도메인(소문자). 판별 보조일 뿐 **canonical 은 경로** |
| `parent_site_id` | 다국어 변형·서브사이트 트리 (NULL=대표) |
| `template_id` / `theme_id` / `layout_id` | 3축 선택. **NULL = 기본 폴백** |
| `default_yn` | 기본 사이트 — 미해석 시 폴백. **전체 1개**(앱이 검증) |
| `head_meta` | 사이트별 `<head>` 삽입 HTML — `th:utext` 로 출력 |
| `copyright` | 푸터 문구(HTML 허용) |
| `logo_path` / `favicon_path` | NULL 이면 사이트명 텍스트 / 시스템 기본 |

### 2.2 테마 소속 검증은 복합 FK 가 한다

```sql
CONSTRAINT fk_site_theme FOREIGN KEY (template_id, theme_id)
                         REFERENCES tb_theme (template_id, theme_id)
```

테마는 템플릿에 속한다. **A 템플릿을 쓰면서 B 템플릿의 테마를 고르는 것**을 앱 검증이
아니라 DB 가 막는다 — 앱 검증은 우회 경로가 생기면 뚫리지만 복합 FK 는 뚫리지 않는다.

### 2.3 폴백 상수

```java
SiteServiceImpl.FALLBACK_TEMPLATE = "krds"
SiteServiceImpl.FALLBACK_LAYOUT   = "layout-001"
```

`template_id`·`layout_id` 가 NULL 이거나 참조가 깨져도 화면은 뜬다.
**단 `layout.html` 자체는 폴백하지 않는다**([§3.2](#32-layouthtml-은-폴백하지-않는다)).

### 2.4 캐시

`SiteContext` 는 Caffeine 캐시(`CacheConfig.SITE_CONTEXT`)에 **메뉴 트리째** 실린다.
사이트·메뉴·컨텐츠를 고치면 `@CacheEvict(allEntries = true)` 로 **전체를 비운다** —
사이트 수가 소규모라 부분 무효화의 복잡도를 감수할 이유가 없고, 기본 사이트 키
(`__default__`)도 함께 지워야 하기 때문이다.

> 관리자 화면에서 사이트를 고쳤는데 반영이 안 되면 evict 경로를 먼저 의심한다.

---

## 3. 레이아웃 (`tb_layout`)

### 3.1 구조

```
templates/layouts/
  ├ layout-001/layout.html  … layout-007/layout.html   ← 7종 (wireframe frame001~007)
  ├ layout-adm/layout.html                             ← 관리자 전용
  └ _default/                                          ← 공용 페이지 1벌
      ├ index.html  content.html  sitemap.html
      ├ board/  member/  identity/
```

`layout_code` 가 곧 **폴더명**이다. `tb_layout.wireframe_ref` 가 원전
(`wireframe/index.html` 의 frame001~007)을 가리킨다.

`_default/` 에는 **페이지**가 들어가고 `layout-00N/` 에는 **감싸는 틀**이 들어간다.
레이아웃별로 페이지를 다르게 만들고 싶을 때만 `layouts/{code}/index.html` 처럼
오버라이드를 둔다(현재 오버라이드는 없다).

### 3.2 `layout.html` 은 폴백하지 않는다

`LayoutSmokeRunner` 가 기동 시 **활성 사이트가 참조하는** 레이아웃의 `layout.html` 과
템플릿 CSS(`/tmpl/css/{code}.css`) 존재를 확인하고, 없으면 **기동을 중단한다**.

```
레이아웃/템플릿 자원 누락 — 기동 중단 (layout.html 은 폴백 금지):
  [ai] templates/layouts/layout-009/layout.html
```

폴백을 허용하면 레이아웃 폴더를 잘못 만들어도 다른 레이아웃으로 조용히 뜬다 —
화면이 나오니 아무도 눈치채지 못하고, 나중에 "왜 이 사이트만 다르지" 로 돌아온다.

### 3.3 레이아웃 파일 계약

```html
<html th:classappend="${themeClass}">          ← 테마
<link rel="stylesheet" href="/css/output.css"/>                        ← 전역 KRDS
<link rel="stylesheet" th:href="@{'/tmpl/css/' + ${site.templateCode} + '.css'}"/>  ← 템플릿
<link rel="stylesheet" href="/css/layouts/layout-001.css"/>            ← 레이아웃 전용
…
<script src="/js/vendor/htmx.min.js"></script>
<script src="/js/app.js"></script>
```

CSS 는 **전역 → 템플릿 → 레이아웃** 순으로 쌓인다.

---

## 4. 템플릿 (`tb_template`)

| 컬럼 | 설명 |
|---|---|
| `template_code` | **= CSS 파일명**. `/tmpl/css/{code}.css` |
| `default_layout_id` | 사이트가 `layout_id` 를 안 고르면 이것이 쓰인다 (**NOT NULL**) |
| `design_md` | 시각 언어 원전(Claude Design Md) |

현재 CSS: `krds.css`(기본) · `blueprint-001.css` · `trust-002.css`

템플릿 전환은 `tb_site.template_id` 를 바꾸고 캐시를 비우면 **즉시 반영**된다.
빌드도 재기동도 필요 없다 — CSS 파일 1장만 갈리기 때문이다.

---

## 5. 테마 (`tb_theme`)

| 컬럼 | 설명 |
|---|---|
| `template_id` | **소속 템플릿** — 테마는 템플릿에 종속된다 |
| `theme_code` | `blue` `teal` `indigo` `green` … |
| `css_class` | `<html>` 에 붙는 클래스. **`''` = 템플릿 기본 브랜드** |

UNIQUE 두 개:
- `uk_theme (template_id, theme_code)` — 한 템플릿 안에서 코드 유일
- `uk_theme_tpl (template_id, theme_id)` — [§2.2](#22-테마-소속-검증은-복합-fk-가-한다)
  복합 FK 의 참조 대상

**파일이 없다.** `css_class` 가 `theme-teal` 이면 CSS 안의
`.theme-teal { --brand-50: … }` 블록이 색을 갈아 끼운다. 고대비(`hc`)도 같은 방식이다.

---

## 6. 메뉴 (`tb_menu`)

### 6.1 구조

| 컬럼 | 설명 |
|---|---|
| `parent_menu_id` | NULL = 1뎁스. 자기참조 FK |
| `menu_type` | `CONTENT` `BOARD` `URL` `FOLDER` (CHECK) |
| `link_target_id` | 연결 대상 ID — **접두어로 대상 판별**(`CNT_`=컨텐츠, `BBM_`=게시판) |
| `link_url` | `menu_type=URL` 일 때 직접 링크 |
| `depth` | 트리 깊이 (사이트맵 4뎁스 전개) |
| `auth_required_yn` | 인증 필요 표시 |

### 6.2 트리는 캐시에 함께 실린다

`MenuService.getMenuTree(siteId, siteCode)` 결과가 `SiteContext.menuTree` 로 들어가
**사이트 컨텍스트 캐시에 트리째** 저장된다. 메뉴를 고치면 사이트 캐시가 함께 무효화된다.

`MenuNode` 는 평면 조회 결과를 트리로 조립한 것이고, `href` 는 매퍼가 만든다
(`menu_type` + `link_target_id`/`link_url` + 컨텐츠 `slug` 조인).

### 6.3 경로 탐색

| 메서드 | 용도 |
|---|---|
| `findPathByMenuId(tree, menuId)` | 컨텐츠 상세의 breadcrumb |
| `findPathByHref(tree, href)` | URL 로 현재 메뉴 위치 찾기 |

---

## 7. 컨텐츠 (`tb_content`)

### 7.1 URL 계약 — `ContentUsrController`

| URL | 용도 |
|---|---|
| `GET /` | 기본 사이트 랜딩으로 리다이렉트 |
| `GET /{siteCode}/index` | 랜딩 |
| `GET /{siteCode}/sitemap` | 사이트맵 |
| `GET /{siteCode}/{slug}` | 컨텐츠 상세 (**캐치올 — 마지막 선언**) |

경로 변수에 정규식 제약이 걸려 있다:

```java
@GetMapping("/{siteCode:[a-z0-9-]{1,30}}/{slug:[a-z0-9-]{1,200}}")
```

> 제약이 없으면 **`/css/output.css` 가 `{siteCode}/{slug}` 에 삼켜진다**(실측으로 잡은
> 이슈). 점(`.`)이 들어간 정적 자원 경로가 캐치올에 매칭되던 문제다.

고정 라우트(`index`·`sitemap`)는 리터럴이라 캐치올보다 **우선 매칭**된다
(Spring MVC 패턴 특이성 — 선언 순서는 가독성용일 뿐이다).

### 7.2 경로 siteCode 와 컨텍스트를 대조한다

```java
if (site == null || !site.getSiteCode().equals(siteCode)) → 404
```

미등록 siteCode 는 필터가 **기본 사이트로 폴백**한다. 그대로 두면 잘못된 URL 이 기본
사이트 컨텐츠로 위장 응답된다 — canonical 원칙에 어긋나므로 404 로 확정한다.

### 7.3 slug 규칙

등록측(`ContentServiceImpl`)과 조회측(`ContentUsrController`)이 **같은 목록**
(`UrlNamespaces.RESERVED`)을 본다. 갈라지면 "등록은 되는데 열리지는 않는" 페이지가 생긴다.

| 검증 | 내용 |
|---|---|
| 패턴 | 소문자·숫자·하이픈 1~200자 |
| 예약어 | `UrlNamespaces.RESERVED` — `index` `sitemap` `search` `member` `bbs` `prg` `adm` … |
| 유일성 | `uk_content_slug (site_id, slug)` — 사이트 안에서 유일 |

### 7.4 상태와 이력

`status`: `DRAFT` → `REVIEW` → `APPROVED` → `PUBLISHED` → `UNPUBLISHED` (CHECK)

발행 제어: `published_at` · `publish_scheduled_at`(예약) · `unpublish_at`(만료)

`tb_content_history` — `version_no` 스냅샷(제목·본문·요약 + `change_note`).
`uk_content_hst (content_id, version_no)` 로 중복 버전을 막는다.

### 7.5 그 밖의 컬럼

| 컬럼 | 설명 |
|---|---|
| `body` | WYSIWYG 본문 — **sanitized 상태로 저장**(OWASP Sanitizer) |
| `original_content` | Markdown 원본 (`body` 는 렌더된 HTML) |
| `body_hash` | 디스크 HTML 원문 SHA-256 — 동기화 변경 감지용. **컬럼만 있고 쓰는 코드가 없다**([§10](#10-알려진-제약주의)) |
| `meta_keywords` / `meta_description` | SEO |
| `view_count` | 조회수 |

---

## 8. 관리자 화면

| URL | 대상 | 비고 |
|---|---|---|
| `/adm/site` | 사이트 | 3축 선택 · head_meta · copyright |
| `/adm/template` | 템플릿 | |
| `/adm/theme` | 테마 | 템플릿 소속 |
| `/adm/layout` | 레이아웃 | |
| `/adm/menu` | 메뉴 | 트리 편집 |
| `/adm/content` | 컨텐츠 | slug·상태·이력 |
| `/adm/index` | 대시보드 | `DashboardAdmController` |

관리자 화면은 `layouts/layout-adm/layout.html` 을 쓰고, **뷰 재작성 대상이 아니다**
(`adm/**` 는 Resolver 를 통과한다).

---

## 9. 확장 체크리스트

**새 사이트를 추가할 때**
- [ ] `site_code` 가 `UrlNamespaces.RESERVED` 와 겹치지 않는가 (DB CHECK 가 막는다)
- [ ] `template_id`·`layout_id`·`theme_id` 를 고르거나 NULL(폴백)로 둔다
- [ ] 테마는 **선택한 템플릿에 속한 것**만 가능(복합 FK)
- [ ] 사이트 전용 랜딩이 필요하면 `templates/sites/{siteCode}/index.html`
- [ ] 캐시 evict 확인

**새 레이아웃을 추가할 때**
- [ ] `templates/layouts/{code}/layout.html` **필수** — 없으면 기동이 멈춘다
- [ ] `static/css/layouts/{code}.css`
- [ ] `tb_layout` INSERT (마이그레이션)
- [ ] `layout.html` 계약 확인 — `themeClass`·템플릿 CSS·`cspNonce`

**새 템플릿을 추가할 때**
- [ ] `static/tmpl/css/{code}.css` **필수** — 없으면 기동이 멈춘다
- [ ] `tb_template` INSERT (`default_layout_id` **NOT NULL**)
- [ ] 테마를 함께 넣는다(`tb_theme.template_id`)

**새 사용자 프로그램 네임스페이스를 열 때**
- [ ] `UrlNamespaces.PROGRAM` 에 추가 — **잊으면 404**
- [ ] 사이트코드가 두 번째 세그먼트라는 전제로 라우팅
- [ ] URL 접근 규칙 INSERT(무매칭 DENY)

**새 front 화면을 만들 때**
- [ ] 컨트롤러는 `front/**` 논리 뷰명만 반환 — 템플릿을 알면 안 된다
- [ ] 파일은 `layouts/_default/**` 에 (레이아웃별 차이가 있을 때만 오버라이드)
- [ ] 첫 줄 `layout:decorate="~{${siteLayout}}"`
- [ ] `site` 가 null 일 수 있다는 전제로 작성

---

## 10. 알려진 제약·주의

모두 실측으로 확인했다(2026-07-30).

| 항목 | 현재 상태 |
|---|---|
| **도메인 기반 사이트 판별** | **미구현.** `tb_site.domain` 컬럼과 `uk_site_domain` UNIQUE 는 있으나 Host 헤더로 사이트를 고르는 코드가 없다(DTO 필드·저장뿐). 해석은 경로·쿼리·기본 사이트 3단이 전부다 |
| **`body_hash` 동기화** | **완전 미사용.** 컬럼 주석은 "디스크 HTML 원문 SHA-256 · 동기화 변경 감지 키" 를 전제하지만 Java·XML 어디에도 참조가 없다. 동기화 기능 자체가 없다 |
| **템플릿 미리보기** | **미구현.** 설계서 §6.2 의 "운영 사이트 무영향 미리보기" 는 아직 코드가 없다 |
| `parent_site_id` | 저장·조회는 되지만(관리 화면에서 지정 가능) **다국어 변형·서브사이트 트리를 쓰는 로직은 없다** |
| 레이아웃 오버라이드 | 구조는 동작하나 실제 오버라이드 파일은 현재 0건(전부 `_default` 공용) |
| 캐시 무효화 | 전체 evict — 사이트 수가 늘면 부분 무효화 검토 필요 |

---

## 11. 함정 요약

| 함정 | 증상 | 대응 |
|---|---|---|
| **뷰 캐시 켜짐** | 첫 요청 사이트의 레이아웃이 **전 사이트에 고정** | `setCache(false)` |
| `layout.html` 누락 | 기동 중단 | 의도된 동작 — 폴백 금지 |
| 캐치올 경로 변수 무제약 | `/css/output.css` 가 컨텐츠로 해석 | 정규식 제약 |
| `PROGRAM` 등록 누락 | 프로그램 URL 이 404 | `UrlNamespaces.PROGRAM` |
| 예약어 목록 분산 | 등록은 되는데 안 열리는 페이지 | `UrlNamespaces` 단일 원천 |
| 미등록 siteCode | 기본 사이트 내용이 위장 응답 | 경로·컨텍스트 대조 후 404 |
| 캐시 evict 누락 | 관리자가 고쳐도 반영 안 됨 | `@CacheEvict(allEntries=true)` |
| 다른 템플릿의 테마 선택 | — | 복합 FK 가 막는다 |
| `th:if` + `th:replace` 동일 태그 | `th:replace`(100)가 먼저 실행 | 바깥 `th:block` 으로 감싼다 |
| Thymeleaf 템플릿 캐시 | 뷰 수정이 반영 안 됨 | 재기동(`spring.thymeleaf.cache`) |
