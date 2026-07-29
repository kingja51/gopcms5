# 게시판·게시글 개발 매뉴얼

gopcms5 게시판 도메인(P9)의 기능·기술 매뉴얼이다. 게시판 정책·게시글·댓글·좋아요·신고·
위지윅 에디터를 다룬다. **게시판 코드를 고치기 전에 해당 절을 먼저 읽는다.**

- 기준일: 2026-07-30
- 관련 정본: [PLAN.md](../PLAN.md) §P9 · [conventions.md](conventions.md) §5(URL) ·
  [site-domain.md](site-domain.md)(뷰 해석) · [file-domain.md](file-domain.md)(첨부)

> 이 문서는 **구현된 것만** 적는다. 코드와 어긋나면 코드가 정답이다.

---

## 0. 한눈에 보기

### 0.1 구성 요소

```
tb_bbs_master     게시판 = 정책 묶음 (유형·권한·댓글·첨부·익명·HTML 허용…)
  ├ tb_bbs_category   게시판 내 분류
  └ tb_bbs_article    게시글
       └ tb_bbs_comment   댓글 (대댓글 1단)

tb_bbs_like    좋아요 ┐ 게시글·댓글·컨텐츠 <b>통합</b> (target_type + target_id)
tb_bbs_report  신고   ┘
```

### 0.2 URL

| URL | 담당 | 비고 |
|---|---|---|
| `GET /bbs/{siteCode}/{bbsCode}` | 목록 | **사이트코드가 두 번째** |
| `GET /bbs/{siteCode}/{bbsCode}/{articleId}` | 상세 | |
| `GET /bbs/{siteCode}/{bbsCode}/write` | 작성·수정 폼 | |
| `POST …/save` `…/delete` | 저장·삭제 | |
| `POST …/{articleId}/comment` `…/comment/delete` | 댓글 | |
| `POST /api/v1/board/like` | 좋아요 토글 | JSON · 인증 필수 |
| `POST /api/v1/board/report` | 신고 접수 | JSON · 인증 필수 |
| `/adm/board` | 게시판 관리 | |
| `/adm/board/{bbsMasterId}/article` | 게시글 관리 | |
| `/adm/board-report` | 신고 처리 | |

**게시판은 사용자 프로그램**이라 사이트코드가 두 번째 세그먼트다
(`UrlNamespaces.PROGRAM`). 컨텐츠(`/{siteCode}/{slug}`)와 자리가 반대인 이유는
[site-domain.md §1.2](site-domain.md#12-url-네임스페이스--단일-원천) 참조.

URL 접근 규칙은 `/bbs/**` **PERMIT_ALL**(priority 200)이다 — 게시판별 읽기·쓰기 권한은
URL 이 아니라 `tb_bbs_master` 가 판정하기 때문이다.

---

## 1. 게시판 마스터 (`tb_bbs_master`)

게시판은 **정책 묶음**이다. 화면 코드는 한 벌이고, 게시판마다 다른 것은 이 행의 값이다.

### 1.1 정책 컬럼

| 컬럼 | 기본 | 의미 |
|---|---|---|
| `bbs_code` | — | **URL 에 노출**. `uk_bbs_code (site_id, bbs_code)` |
| `bbs_type` | — | `NOTICE` `BODO` `FREE` `FAQ` `QNA` `GALLERY` `FILE` `YOUTUBE` |
| `read_auth` | `ALL` | `ALL` `MEMBER` `EMPLOYEE` `ADMIN` |
| `write_auth` | `MEMBER` | `GUEST` `MEMBER` `EMPLOYEE` `ADMIN` |
| `download_auth` | `ROLE_MEMBER` | 첨부 다운로드 최소 권한(7단계 — file-domain.md §4.2) |
| `comment_yn` | `Y` | 댓글 사용 |
| `file_yn` / `file_count_max` / `file_size_max` | `Y` / 5 / 10MB | 첨부 정책 |
| `anonymous_yn` | `N` | 익명 게시판 |
| `notice_top_yn` | `Y` | 공지 상단 고정 사용 |
| **`html_yn`** | `N` | **`Y`=sanitize 후 `utext` / `N`=평문** |
| `captcha_yn` | `N` | 작성 시 CAPTCHA |
| `grouped_board_ids` | NULL | **통합 게시판 모드** — 묶을 게시판 ID CSV |

> `EMPLOYEE` 는 CHECK 제약에 남아 있지만 **쓰지 않기로 확정**했다(직원 역할 미도입).
> 앱의 선택지(`BoardAuth.READ` = `ALL`·`MEMBER`·`ADMIN`,
> `BoardAuth.WRITE` = `MEMBER`·`ADMIN`)에서 제외돼 있다. DB 가 허용한다고 앱이 반드시
> 써야 하는 것은 아니다.

### 1.2 타입별 화면은 조각으로 가른다

뷰명을 갈라 8벌을 만들지 **않는다**. 공용 뷰 한 벌이 타입에 맞는 조각을 고른다.

```
layouts/_default/board/list.html      ← 공용 1벌
  └ fragments/board/list-table.html     기본
    fragments/board/list-faq.html       FAQ (<details> 아코디언)
    fragments/board/list-gallery.html   갤러리 (썸네일 격자)
```

**이유**: 사이트/레이아웃 오버라이드(Resolver 3단 폴백)와 타입 분기가 **곱해지면**
조합이 폭발한다. 타입 하나를 더할 때마다 폴백 3벌을 함께 만들어야 한다.

### 1.3 통합 게시판 (합본)

`grouped_board_ids` 에 게시판 ID 를 CSV 로 넣으면 여러 게시판의 글을 한 목록에 모은다.

**합본은 읽기 전용이다.** 글을 쓰면 어느 게시판에 속하는지 알 수 없다.
`BoardArticleServiceImpl` 이 합본에 대한 쓰기를 거부한다 — 원전은 UI 에서 버튼만 감추고
서비스 가드를 미뤄서, URL 을 직접 부르면 합본으로 글을 쓸 수 있었다(원전 §14-8 자인).
`BoardAggregatorTest` 가 이 규칙을 고정한다.

> **실측으로 잡은 결함**: 합본이 대상 게시판을 모을 때 **읽기 권한을 확인하지 않아**
> 비로그인 사용자에게 회원 전용 글의 **제목이 목록에 노출**됐다. 상세는 막혀 있었지만
> 목록이 새고 있었다. 지금은 `isReadable(master)` 로 거른다.
>
> 대상이 하나도 없으면 **빈 목록**이 되어야 한다. 빈 `IN` 절은 SQL 오류거나 조건 없는
> 전체 조회가 되므로 sentinel(`List.of("-")`)을 넣는다.

---

## 2. 게시글 (`tb_bbs_article`)

### 2.1 주요 컬럼

| 컬럼 | 설명 |
|---|---|
| `article_id` | `BBA_` + UUIDv7 |
| `writer_user_id` / `writer_user_type` | 비로그인이면 NULL |
| `writer_name` | 표시명 — **NOT NULL**. 탈퇴 시 익명 표기로 바뀐다(member-domain.md §7.2) |
| `writer_password` | 비로그인 글 수정·삭제용 (**BCrypt**) |
| `content` | 본문. `html_yn=N` 이면 평문 |
| `file_group_id` | 첨부 묶음 — 첨부 없으면 NULL(원안의 NOT NULL 을 완화) |
| `notice_yn` | 공지 상단 고정 |
| `secret_yn` | 비밀글 — 작성자·관리자만 |
| `press_name` / `link_url` / `published_at` | 보도자료·유튜브 게시판용 |
| `view_count` `like_count` `report_count` `comment_count` | **집계 캐시** |
| `status` | `PUBLISHED` `HIDDEN` `REPORTED` `DELETED` |

사용자 목록은 항상 `status='PUBLISHED'` 로 고정한다 — 숨김·신고보류는 보이지 않는다.

### 2.2 PK 선발급의 함정

첨부 파일 picker 가 `entityId` 를 필요로 하므로 **폼을 열 때 글 ID 를 미리 발급**한다.
그래서 "PK 가 비어 있으면 신규" 라는 통상적 판단이 **성립하지 않는다.**

`BoardArticleServiceImpl.resolveNew()` 가 세 가지를 함께 본다:

1. DB 에 그 ID 의 행이 실제로 있는가
2. PK 형식이 맞는가 (`BBA_` + UUID)
3. **그 글이 이 게시판 소속인가**

> 이 판단을 빠뜨렸을 때 **글이 0행 저장되는** 증상이 났다(UPDATE 가 0건). 세 번째 조건이
> 없으면 남의 게시판 글 ID 를 보내 덮어쓸 수 있다.

### 2.3 조회수 — `ArticleViewCounter`

같은 브라우저가 **30분 안에 다시 열면 세지 않는다.**

- **세션이 아니라 쿠키**(`GOPCMS_BBS_VIEW`)를 쓴다. 비로그인 열람이 대부분인데 세션을
  만들면 조회 한 번에 세션이 하나씩 생긴다
- 쿠키 4KB 한계 때문에 최근 **80건**만 유지(41자 × 80 ≈ 3.2KB), 넘치면 오래된 것부터 버린다
- 구분자로 감싸 비교한다 — 부분 문자열이 우연히 맞는 것을 막는다

**정확한 집계 수단이 아니다**(쿠키를 지우면 다시 센다). 목적은 새로고침으로 조회수가
계속 오르는 것을 막는 것이고, 통계는 접근 로그가 담당한다.

### 2.4 본문 정화

`html_yn='Y'` 인 게시판만 HTML 을 허용하며, 저장 시 **OWASP Java HTML Sanitizer** 를 통과한다.

> **실측 결함**: `Sanitizers.TABLES` 는 표 **요소**만 열고 `scope` 같은 **속성**은 떨어뜨린다.
> 그래서 표 접근성 속성이 저장할 때마다 사라졌다. 같은 빌더에 표 요소를 명시해 해결했다.
> 단위 테스트가 잡아낸 사례다.

에디터를 바꿔도(`tiptap` ↔ `namo`) 정화 규칙은 한 벌이다. **다만 CrossEditor 는
HTML4.01/XHTML 계열 마크업을 뱉으므로**, allowlist 를 두 출력의 합집합으로 잡지 않으면
기존 글이 저장할 때마다 깎인다(PLAN 리스크 표).

---

## 3. 댓글 (`tb_bbs_comment`)

| 컬럼 | 설명 |
|---|---|
| `parent_comment_id` | 대댓글 (자기참조 FK) |
| `depth` | 들여쓰기 깊이 |
| `secret_yn` | 비밀 댓글 — 작성자·**글쓴이**·관리자만 |
| `content` | **평문** (댓글은 HTML 을 허용하지 않는다) |
| `status` | `PUBLISHED` `HIDDEN` `REPORTED` `DELETED` |

`comment_count` 는 `tb_bbs_article` 의 집계 캐시다.

> **실측 결함**: 재집계 SQL 이 숨김 댓글까지 세어 화면 수치와 실제가 어긋났다.
> `status='PUBLISHED'` 조건을 넣어 해결했다.

`BoardCommentThreadTest` 가 스레드 구성 규칙을 고정한다.

---

## 4. 좋아요·신고 — 통합 테이블

`tb_bbs_like` / `tb_bbs_report` 는 **게시글·댓글·컨텐츠를 함께** 다룬다
(`target_type` + `target_id` 다형 참조, FK 없음).

`ReactionTarget.ALL` = `ARTICLE` `COMMENT` `CONTENT`

### 4.1 둘 다 익명 불가

| | UNIQUE | 이유 |
|---|---|---|
| 좋아요 | `uk_like_target_user (target_type, target_id, user_id)` | 익명이면 **중복 방지가 불가능** |
| 신고 | `uk_report_target_reporter (target_type, target_id, reporter_user_id)` | 익명이면 **남용 추적이 불가능** |

URL 규칙도 `AUTHENTICATED`(priority 72·73)다.

좋아요 취소는 행 삭제가 아니라 `delete_yn='Y'` 다 — 언제 눌렀다 취소했는지가 이력이다.

### 4.2 신고 처리

| 컬럼 | 값 |
|---|---|
| `reason_code` | `SPAM` `OFFENSIVE` `ILLEGAL` `COPYRIGHT` `PRIVACY` `OTHER` |
| `status` | `PENDING` → `REVIEWED` / `REJECTED` |
| `reviewed_by` `reviewed_at` `review_note` | 처리 기록 |

**임계치를 넘으면 대상이 자동으로 `REPORTED` 상태**가 되어 사용자 목록에서 빠진다.
임계값은 `gopcms.board.report-threshold`(기본 **5**).
`BoardReportThresholdTest` 가 이 규칙을 고정한다.

관리자 처리는 `/adm/board-report`.

### 4.3 프런트 — `reaction-band`

`fragments/reaction-band.html` + `static/js/board-reaction.js`

- **CSRF**: 밴드가 **자체 hidden `_csrf`** 를 들고 있고 `X-CSRF-TOKEN` 헤더로 보낸다.
  레이아웃마다 메타 태그를 심는 방식은 하나만 빠뜨려도 조용히 403 이 된다
- 401/403 은 실패가 아니라 "로그인하면 된다" 는 안내로 처리한다

---

## 5. 위지윅 에디터 (P9-2b)

### 5.1 3종 교체 구조

```yaml
gopcms.editor.provider: tiptap    # tiptap | namo | ckeditor5
```

provider 를 바꿔도 **저장·정화·업로드·권한은 한 벌 그대로**다. 갈리는 것은 셋뿐:
자산 목록 · 프래그먼트 이름 · 어댑터 JS.

```
templates/fragments/editor.html                 ← 공개 계약(호출부는 이것만 안다)
  └ fragments/editor/editor-tiptap.html
    fragments/editor/editor-namo.html
    fragments/editor/editor-ckeditor5.html
config/editor/{EditorProvider,EditorProperties,EditorModelAdvice,EditorAssetSmokeRunner}
```

`EditorAssetSmokeRunner` 가 선택된 provider 의 자산 존재를 기동 시 확인하고 없으면
**기동을 멈춘다** — 폼을 열고 나서야 "에디터가 안 뜬다" 를 알게 되면 늦다.

### 5.2 Tiptap 은 `unsafe-eval` 이 필요 없다

CSP 재검토 결과 확인된 사실이다. 번들은 esbuild 로 만들어 **self-host** 한다
(`src/editor/tiptap.js` → Maven `generate-resources` 단계에서 `npm run editor`).
산출물은 gitignore, 소스만 커밋한다.

> **CrossEditor 도입 시**: 자리(프래그먼트·자산 목록·기동 검증)는 준비돼 있다.
> **`unsafe-eval` 은 열지 않는다**(사용자 확정 2026-07-29). iframe 이라 `frame-src` 를
> 요구하면 그때 판단한다. 라이선스가 1도메인 단위 상용이라 벤더 번들은 저장소에
> 커밋하지 않는다.

### 5.3 빈 본문 판정

```js
textarea.value = (html === '<p></p>' || html === '<p><br></p>') ? '' : html;
```

> **실측 결함**: 처음에 `editor.isEmpty` 로 판정했더니 **표만 있는 본문이 빈 값으로
> 버려졌다**. Tiptap 은 텍스트 노드가 없으면 표가 있어도 `isEmpty=true` 다.

### 5.4 에디터가 나오는 조건

본문 HTML 을 허용하는 게시판(`html_yn='Y'`)에서만 에디터가 뜬다.

> **실측 결함**: `th:if` 와 `th:replace` 를 **같은 태그**에 걸었더니 평문 게시판에도
> 에디터가 나왔다. Thymeleaf 는 `th:replace`(우선순위 100)를 `th:if`(300)보다 **먼저**
> 처리한다. 바깥 `th:block th:if` 로 감싸 해결했다.

---

## 6. 검색 (P9-7)

**LIKE 검색**이다. 색인 테이블·FULLTEXT·Nori 형태소 분석 모두 도입하지 않았다(확정).

`PageRequest.getKeywordLike()` 가 와일드카드를 이스케이프한다:

- `%` 를 그대로 넘기면 검색이 아니라 전체 조회가 되고, `_` 는 한 글자 아무거나와 맞는다
- 이스케이프 문자로 역슬래시가 아니라 **`|`** 를 쓴다 — `ESCAPE '\\'` 는 MariaDB 와
  PostgreSQL 이 문자열 리터럴을 다르게 해석해(`standard_conforming_strings`) 벤더마다
  값이 달라진다. 매퍼는 반드시 `ESCAPE '|'` 와 짝지어 쓴다
- 화면 표시는 `getKeyword()` 를 쓴다 — `getKeywordLike()` 를 입력창에 되돌리면
  `|%` 같은 흔적이 보인다

---

## 7. 첨부

`file_group_id` 로 [file-domain.md](file-domain.md) 의 파일 묶음을 참조한다.

| 게시판 정책 | 쓰이는 곳 |
|---|---|
| `file_yn` | 첨부 사용 여부 |
| `file_count_max` | picker 프래그먼트의 `maxFiles` — **화면 전용**([§10](#10-알려진-제약주의)) |
| `file_size_max` | 게시판별 크기 상한 |
| `download_auth` | `tb_file_group.download_auth` 로 전달 |

폼에는 `fragments/file-picker :: picker(...)` 를 넣는다. **글 ID 를 먼저 발급하는 이유**가
여기 있다([§2.2](#22-pk-선발급의-함정)).

---

## 8. 관리자 화면

| URL | 화면 | 내용 |
|---|---|---|
| `/adm/board` | `adm/board/list.html` | 게시판 목록 |
| `/adm/board/form` | `adm/board/form.html` | 게시판 정책 + **카테고리 인라인 편집** |
| `/adm/board/{id}/article` | `adm/board/article/list.html` | 게시글 목록 |
| `/adm/board/{id}/article/form` | `adm/board/article/form.html` | 게시글 편집 + 댓글 조정 |
| `/adm/board-report` | `adm/board/report/list.html` | 신고 처리 |

게시판 삭제는 **글이 있으면 거부**한다(FK 가 막기 전에 안내 문구로).

---

## 9. 확장 체크리스트

**새 게시판 유형(`bbs_type`)을 더할 때**
- [ ] `tb_bbs_master.chk_bbs_master_type` CHECK 확장 (마이그레이션)
- [ ] `BbsType` 에 상수 + `ALL` 목록 추가
- [ ] 목록 조각이 필요하면 `fragments/board/list-*.html` 추가 후 공용 `list.html` 이
      고르게 한다 — **뷰명을 갈라 새 벌을 만들지 말 것**

**새 신고 사유를 더할 때**
- [ ] `chk_report_reason` CHECK 확장 + `ReportReason.ALL`

**새 반응 대상(`target_type`)을 더할 때**
- [ ] `chk_like_target_type` · `chk_report_target_type` CHECK 확장
- [ ] `ReactionTarget.ALL`
- [ ] 집계 캐시 컬럼(`like_count`·`report_count`)이 그 대상 테이블에 있는지 확인

**새 게시판 화면을 만들 때**
- [ ] 컨트롤러는 `front/board/**` 논리 뷰명만 반환
- [ ] 파일은 `layouts/_default/board/` 에
- [ ] 사용자 목록은 `status='PUBLISHED'` 고정
- [ ] 합본이면 대상마다 **읽기 권한 확인** + 빈 목록 sentinel

**에디터 provider 를 바꿀 때**
- [ ] 자산을 `static/js/vendor/…` 에 배치(CDN 금지)
- [ ] `gopcms.editor.provider` 변경
- [ ] 기동 시 `EditorAssetSmokeRunner` 통과 확인
- [ ] **정화 allowlist 를 두 출력의 합집합으로** — 안 그러면 기존 글이 깎인다
- [ ] `unsafe-eval` 을 요구하면 도입하지 않는다

---

## 10. 알려진 제약·주의

| 항목 | 현재 상태 |
|---|---|
| `EMPLOYEE` 권한 | CHECK 에는 있으나 **미사용 확정**. 앱 선택지에서 제외 |
| 익명 좋아요·신고 | **미지원** — 중복 방지·남용 추적이 불가능하다 |
| 검색 | LIKE 전용. 형태소 분석·색인 없음(확정) |
| 조회수 | 쿠키 기반이라 정확한 집계가 아니다 |
| 대댓글 | `depth` 컬럼은 있으나 실질 1단 운용 |
| CrossEditor | 자리만 준비. 번들·라이선스 미확보 |
| **첨부 개수 상한 이원화** | `tb_bbs_master.file_count_max` 는 picker 의 `maxFiles` 로 **화면에만** 전달된다(실측). 서버(`FileServiceImpl`)는 전역 `gopcms.file.max-files-per-group`(기본 20)만 센다. 따라서 ① 게시판 값을 20보다 크게 잡으면 화면은 받아 주고 서버가 거부하며, ② 게시판 값을 작게 잡아도 API 직접 호출로는 20까지 올릴 수 있다 |

---

## 11. 함정 요약

이 도메인에서 **실측으로 잡은 것들**이다.

| 함정 | 증상 | 대응 |
|---|---|---|
| PK 선발급 | **글이 0행 저장**(UPDATE 0건) | DB 존재 + PK 형식 + 게시판 소속 3중 판정 |
| 합본 권한 미확인 | 회원 전용 글 **제목이 목록에 노출** | 대상마다 `isReadable` |
| 빈 `IN` 절 | 조건 없는 전체 조회 | sentinel `List.of("-")` |
| `Sanitizers.TABLES` | 표의 `scope` 속성이 사라짐 | 같은 빌더에 표 요소 명시 |
| `comment_count` 재집계 | 숨김 댓글까지 셈 | `status='PUBLISHED'` |
| `editor.isEmpty` | **표만 있는 본문이 버려짐** | 빈 문단 문자열로 판정 |
| `th:if` + `th:replace` 동일 태그 | 평문 게시판에 에디터 노출 | 바깥 `th:block` 으로 감싼다 |
| `ESCAPE '\\'` | 벤더마다 검색 결과가 다름 | `ESCAPE '|'` |
| 합본에 글쓰기 | 소속 게시판 불명 | 서비스가 거부(UI 만으로 부족) |
