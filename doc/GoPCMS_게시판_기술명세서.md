# GoPCMS 게시판(Board) 기술명세서

- 작성일: 2026-04-27
- **최종 개정: 2026-05-02** — **B7 통합 게시판(전체글 보기)** + **canManage 가드** (수정/삭제/모더레이션 분리) + **버튼 일관성 통일** (등록/수정/삭제/취소/목록)
- 직전 개정: 2026-05-01 — **7단계 download_auth 정책** + **OWNER_PRIVACY 정책** + **공지글 ANONYMOUS 정책** + **VARCHAR(40) ID + 도메인 prefix**
- 대상 스프린트: **S4 게시판 스프린트 B1 ~ B7**
- 참조 문서: [GoPCMS_설계서_및_개발계획서.md](GoPCMS_설계서_및_개발계획서.md), [GoPCMS_테이블설계서_MariaDB11.7.md](GoPCMS_테이블설계서_MariaDB11.7.md), [GoPCMS_파일_기술명세서.md](GoPCMS_파일_기술명세서.md), [GoPCMS_회원_기술명세서.md](GoPCMS_회원_기술명세서.md)

---

## 1. 개요

### 1.1 배경
- 멀티사이트 CMS 의 핵심 도메인 — 사이트별 N개 게시판 운영, 게시판 안에서 카테고리·글·댓글 관리
- 7타입 (NOTICE / BODO / FREE / FAQ / QNA / GALLERY / FILE) 별 사용자 화면 분기 렌더
- v1 스코프: **결재 워크플로 제외**, **비로그인(GUEST) 작성 영구 제외** (2026-04-26)
- 첨부파일은 [파일 기술명세서](GoPCMS_파일_기술명세서.md) 의 file-picker fragment + 6중 방어 스택 재사용

### 1.2 설계 원칙

| 원칙 | 구현 |
|---|---|
| **사이트 격리** | `tb_bbs_master.site_id` FK + URL `/bbs/{siteCode}/{bbsCode}` 라우팅 |
| **5단 모델** | 마스터 → 카테고리 → 글 → 댓글 → 좋아요/신고 |
| **Controller 접미사 규약** | `MngController`(관리자), `UsrController`(사용자), `ApiController`(REST 좋아요/신고만) |
| **eGov 호환** | Service 인터페이스 + `EgovAbstractServiceImpl` 상속 Impl |
| **공통 자원 재사용** | `PageRequest` / `PageResponse` / `AuditLogger` / `UuidV7Generator.generate(prefix)` / `ExcelResponseWriter` / `AuditInterceptor` / `file-picker` |
| **GUEST 미지원** | `write_auth='GUEST'` 옵션 form/Service/Pattern 모두 제거. DDL 컬럼만 보존 |
| **첨부 권한 위임** | `tb_file_group.download_auth` 기반 — 마스터 `download_auth` 와 자동 cascade. 공지글은 ANONYMOUS 강제 |
| **6감사컬럼 자동 주입** | `AuditInterceptor` 가 모든 INSERT/UPDATE 6컬럼 채움 |
| **CUD 후 감사 이벤트** | `BBS_MASTER_*`, `BBS_ARTICLE_*`, `BBS_COMMENT_*`, `BBS_CATEGORY_*`, `BBS_LIKE_*`, `BBS_REPORT_*` 5경로 |

### 1.3 완성 상태 (2026-05-02)

- ✅ **B1**: 게시판 마스터 CRUD + 사용중지/재사용 + 엑셀
- ✅ **B2.1**: 게시글 CRUD + 비밀글/공지/뷰카운트 30분 쿠키 dedup
- ✅ **B2.2**: 첨부파일 file-picker 연동 + `tb_bbs_article.file_group_id` 자동 동기화
- ✅ **B3**: 댓글 + 대댓글(depth=2) + 관리자 모더레이션 + `comment_count` 자동 재계산 + GUEST 항구 제외
- ✅ **B4**: 카테고리(`tb_bbs_category`) + BODO 타입 + 7타입별 화면 + 비밀댓글
- ✅ **B5**: 좋아요(`tb_bbs_like`) + 신고(`tb_bbs_report`) + 자동 REPORTED 임계 전환
- ✅ **B6**: 통합 검색 색인(`tb_search_index`) + FULLTEXT(ngram) + 사용자 검색 화면 + 백필 admin
- ✅ **download_auth 7단계 정책** + OWNER_PRIVACY + 공지글 ANONYMOUS 정책 (2026-05-01)
- ✅ **VARCHAR(40) ID + 도메인 prefix** (2026-05-01) — `BBM_<UUID>` 마스터, `ART_<UUID>` 글, `CMT_<UUID>` 댓글
- ✅ **B7 통합 게시판(전체글 보기)** (2026-05-02) — `tb_bbs_master.grouped_board_ids` 신설. 마스터 1행으로 N개 게시판 글을 합쳐 보여주는 read-only 뷰. 작성/수정/삭제는 원본 게시판에서만
- ✅ **canManage 가드** (2026-05-02) — `master.bbsMasterId == article.bbsMasterId` 일 때만 수정/삭제/모더레이션 노출. 통합 게시판 URL 진입 시 액션 버튼 자동 비노출
- ✅ **버튼 일관성** (2026-05-02) — 사용자 7타입 list/detail/write.html 의 등록/수정/삭제/취소/목록 버튼을 동일 토큰으로 통일. `!text-{color}` important 로 사이트 layout CSS cascade 우회
- ⬜ 결재 워크플로 — v1 스코프 제외

---

## 2. 아키텍처

### 2.1 패키지 구조

```
com.gonet.primary.board/
├── master/                                    ← B1 게시판 자체
│   ├── controller/BoardMngController          /admin/system/board/**
│   ├── dto/{BbsMaster,BbsType,BbsMasterSaveForm,BbsMasterSearch}
│   ├── mapper/BbsMasterMapper                 (+XML)
│   └── service/{BoardMasterService,Impl}
├── category/                                  ← B4 카테고리
│   ├── controller/BoardCategoryMngController  /admin/system/board/{bid}/category/**
│   ├── dto/{BbsCategory,BbsCategorySaveForm}
│   ├── mapper/BbsCategoryMapper               (+XML)
│   └── service/{BoardCategoryService,Impl}
├── article/                                   ← B2 게시글
│   ├── controller/
│   │   ├── BoardArticleMngController          /admin/system/board/{bid}/article/**
│   │   └── BoardUsrController                 /bbs/{siteCode}/{bbsCode}/**
│   ├── dto/{BbsArticle,BbsArticleStatus,BbsArticleSaveForm,BbsArticleSearch}
│   ├── mapper/BbsArticleMapper                (+XML)
│   └── service/{BoardArticleService,Impl}
├── comment/                                   ← B3 댓글
│   ├── controller/
│   │   ├── BoardCommentUsrController          /bbs/{siteCode}/{bbsCode}/{aid}/comments/**
│   │   └── BoardCommentMngController          /admin/system/board/{bid}/article/{aid}/comments/**
│   ├── dto/{BbsComment,BbsCommentStatus,BbsCommentSaveForm}
│   ├── mapper/BbsCommentMapper                (+XML)
│   └── service/{BoardCommentService,Impl}
├── like/                                      ← B5 좋아요
│   ├── controller/BoardLikeApiController      POST /api/v1/board/like/{type}/{id}/toggle
│   ├── dto/{BbsLike,LikeTargetType}
│   ├── mapper/BbsLikeMapper                   (+XML)
│   └── service/{BoardLikeService,Impl}
└── report/                                    ← B5 신고
    ├── controller/{BoardReportApiController,BoardReportMngController}
    ├── dto/{BbsReport,ReportTargetType,ReportStatus,BbsReportSearch}
    ├── mapper/BbsReportMapper                 (+XML)
    └── service/{BoardReportService,Impl}

(B6 검색은 com.gonet.primary.search 별도 도메인)
```

### 2.2 의존 다이어그램

```
                    ┌──────────────────────┐
                    │     tb_site          │
                    └──────────▲───────────┘
                               │ FK site_id
                    ┌──────────┴───────────┐
                    │   tb_bbs_master      │ ← download_auth 7단계 정책
                    │   (게시판 자체)        │
                    └──┬──────────────┬────┘
       FK bbs_master_id │              │ FK bbs_master_id
                    ┌───▼───┐    ┌─────▼─────────┐
                    │ tb_bbs│    │ tb_bbs_       │
                    │ _cate │    │ article       │
                    │ gory  │    │ (게시글)       │
                    └───▲───┘    └──┬──────────┬─┘
       FK category_id (옵셔널) ─────┘          │
                                               │ FK file_group_id
                                ┌──────────────▼────┐
                                │ tb_file_group     │ ← download_auth cascade
                                │ (다운로드 권한)     │   (공지글은 ANONYMOUS)
                                └──────┬────────────┘
                                       │ 1:N
                                ┌──────▼────────┐
                                │ tb_file       │
                                └───────────────┘

       FK article_id ────┐
                ┌────────▼────────────┐
                │ tb_bbs_comment      │ ── self FK parent_comment_id (대댓글)
                │ (댓글)               │
                └─────────────────────┘
                         │ N:1 target
                ┌────────▼────────────┐  ┌──────────────────────┐
                │ tb_bbs_like         │  │ tb_bbs_report        │
                │ (좋아요)             │  │ (신고)                │
                └─────────────────────┘  └──────────────────────┘

                                ┌──────────────────────┐
                                │ tb_search_index      │ ← upsert 훅
                                │ (FULLTEXT ngram)     │   (article CUD 시)
                                └──────────────────────┘
```

### 2.3 URL 맵

| 영역 | 메서드 | URL | 권한 |
|---|---|---|---|
| **관리자 — 게시판 마스터** | GET/POST | `/admin/system/board` (목록·신규·엑셀) | ROLE_STAFF |
| | GET/POST | `/admin/system/board/{bid}` (상세) | |
| | GET/POST | `/admin/system/board/{bid}/edit` | |
| | POST | `/admin/system/board/{bid}/use?active={t,f}` | |
| | POST | `/admin/system/board/{bid}/delete` | |
| **관리자 — 카테고리** | GET/POST | `/admin/system/board/{bid}/category/**` | ROLE_STAFF |
| **관리자 — 게시글 모더레이션** | GET | `/admin/system/board/{bid}/article` (목록) | ROLE_STAFF |
| | GET/POST | `/admin/system/board/{bid}/article/{aid}/{status,notice,delete}` | |
| | POST | `/admin/system/board/{bid}/article/excel` | |
| **관리자 — 댓글 모더레이션** | POST | `/admin/system/board/{bid}/article/{aid}/comments/{cid}/{status,delete}` | ROLE_STAFF |
| **관리자 — 신고 (B5)** | GET/POST | `/admin/system/board/report/**` | ROLE_STAFF |
| **사용자 — 게시판** | GET | `/bbs/{siteCode}/{bbsCode}` (목록) | PERMIT_ALL (read_auth 반영) |
| | GET | `/bbs/{siteCode}/{bbsCode}/{aid}` (상세) | |
| | GET/POST | `/bbs/{siteCode}/{bbsCode}/write` | AUTHENTICATED + write_auth |
| | GET/POST | `/bbs/{siteCode}/{bbsCode}/{aid}/edit` | 작성자/관리자 |
| | POST | `/bbs/{siteCode}/{bbsCode}/{aid}/delete` | 작성자/관리자 |
| **사용자 — 댓글** | POST | `/bbs/{siteCode}/{bbsCode}/{aid}/comments/**` | AUTHENTICATED |
| **REST — 좋아요/신고 (B5)** | POST | `/api/v1/board/like/{article|comment}/{id}/toggle` | AUTHENTICATED |
| | POST | `/api/v1/board/report/{article|comment}/{id}` | AUTHENTICATED |
| **검색 (B6)** | GET | `/search?keyword=...&entityType=ALL` | PERMIT_ALL |

---

## 3. 데이터 모델

### 3.1 `tb_bbs_master` — 게시판 마스터

```sql
CREATE TABLE tb_bbs_master (
  bbs_master_id   VARCHAR(40)  NOT NULL,                     -- BBM_<UUID v7> (40자)
  site_id         VARCHAR(40)  NOT NULL,                     -- SIT_<UUID> FK tb_site
  menu_id         VARCHAR(40),                                -- 옵셔널 메뉴 연결
  bbs_code        VARCHAR(50)  NOT NULL,                     -- 사이트 내 식별 코드
  bbs_name        VARCHAR(100) NOT NULL,
  bbs_type        VARCHAR(20)  NOT NULL,                     -- NOTICE/BODO/FREE/FAQ/QNA/GALLERY/FILE
  comment_yn      CHAR(1)      NOT NULL DEFAULT 'Y',
  file_yn         CHAR(1)      NOT NULL DEFAULT 'Y',
  file_count_max  INT          NOT NULL DEFAULT 5,
  file_size_max   BIGINT       NOT NULL DEFAULT 10485760,    -- 10 MB
  anonymous_yn    CHAR(1)      NOT NULL DEFAULT 'N',         -- GUEST 영구 제외
  notice_top_yn   CHAR(1)      NOT NULL DEFAULT 'Y',
  read_auth       VARCHAR(50)  NOT NULL DEFAULT 'ALL',       -- ALL/MEMBER/EMPLOYEE/ADMIN
  write_auth      VARCHAR(50)  NOT NULL DEFAULT 'MEMBER',    -- MEMBER/EMPLOYEE/ADMIN
  download_auth   VARCHAR(20)  NOT NULL DEFAULT 'ROLE_MEMBER',
                                                              -- ★ 2026-05-01 신설
                                                              -- ANONYMOUS / ROLE_MEMBER / ROLE_EMPLOYEE /
                                                              -- ROLE_STAFF / OWNER_PRIVACY / ROLE_MANAGER /
                                                              -- ROLE_ADMIN
  use_yn          CHAR(1)      NOT NULL DEFAULT 'Y',
  description     VARCHAR(2000),
  grouped_board_ids VARCHAR(1000) NULL,                       -- ★ 2026-05-02 신설 (B7)
                                                              -- CSV of source bbs_master_id (통합 게시판 모드)
                                                              -- NULL = 일반 게시판
                                                              -- 비어있지 않으면 list/count 만 합쳐서 노출
                                                              -- 작성/수정/삭제는 비활성 (canManage 가드)
  delete_yn       CHAR(1)      NOT NULL DEFAULT 'N',
  -- 6감사컬럼 ...
  PRIMARY KEY (bbs_master_id),
  UNIQUE KEY uk_bbs_code (site_id, bbs_code),
  CONSTRAINT chk_bbs_master_download_auth CHECK (download_auth IN (
    'ANONYMOUS','ROLE_MEMBER','ROLE_EMPLOYEE','ROLE_STAFF',
    'OWNER_PRIVACY','ROLE_MANAGER','ROLE_ADMIN'))
);
```

**컬럼 의미**:
- `bbs_type` — UI 분기 (`/templates/front/board/{TYPE}/{list,detail,write}.html`)
- `read_auth` — 본문 조회 권한
- `write_auth` — 글 작성 권한 (GUEST 영구 제외)
- `download_auth` — **첨부 다운로드 권한** (read_auth 와 별개) — §6 참조
- `notice_top_yn` — 게시판 단위 공지 정렬 토글 (개별 글의 `notice_yn` 와 별개)
- `grouped_board_ids` — **통합 게시판(전체글 보기) 모드** (B7) — §7 참조. NULL = 일반 게시판. CSV (콤마 구분) UUID 최대 24개

### 3.2 `tb_bbs_category` — 게시판 내 분류

```sql
CREATE TABLE tb_bbs_category (
  category_id    VARCHAR(40)  NOT NULL,                       -- BCT_<UUID>
  bbs_master_id  VARCHAR(40)  NOT NULL,                       -- BBM_<UUID> FK
  category_code  VARCHAR(50)  NOT NULL,
  category_name  VARCHAR(100) NOT NULL,
  sort_order     INT          NOT NULL DEFAULT 0,
  use_yn         CHAR(1)      NOT NULL DEFAULT 'Y',
  delete_yn      CHAR(1)      NOT NULL DEFAULT 'N',
  PRIMARY KEY (category_id),
  UNIQUE KEY uk_bbs_category (bbs_master_id, category_code)
);
```

- 글당 1 카테고리 (`tb_bbs_article.category_id`) — 1:N
- 삭제 가드: PUBLISHED 글이 매핑된 카테고리는 soft-delete 차단

### 3.3 `tb_bbs_article` — 게시글

```sql
CREATE TABLE tb_bbs_article (
  article_id        VARCHAR(40)  NOT NULL,                   -- ART_<UUID>
  bbs_master_id     VARCHAR(40)  NOT NULL,                   -- BBM_<UUID> FK
  category_id       VARCHAR(40),                              -- BCT_<UUID> 옵셔널
  file_group_id     VARCHAR(40)  NOT NULL,                   -- FG0_<UUID> FK (B2.2)
  writer_user_id    VARCHAR(40),                              -- MBR/EMP/ADM_<UUID>
  writer_user_type  VARCHAR(20),                              -- MEMBER/EMPLOYEE/ADMIN
  writer_name       VARCHAR(100) NOT NULL,
  writer_password   VARCHAR(100),                             -- 컬럼 보존, 폼 필드 제거
  title             VARCHAR(300) NOT NULL,
  content           MEDIUMTEXT   NOT NULL,
  press_name        VARCHAR(100),                             -- BODO 타입 보도자료 출처
  link_url          VARCHAR(500),                             -- 외부 링크
  published_at      DATE,                                     -- 보도일자
  notice_yn         CHAR(1)      NOT NULL DEFAULT 'N',       -- ★ Y → file_group ANONYMOUS 강제
  secret_yn         CHAR(1)      NOT NULL DEFAULT 'N',
  view_count        BIGINT UNSIGNED NOT NULL DEFAULT 0,
  like_count        BIGINT UNSIGNED NOT NULL DEFAULT 0,      -- B5 비정규화 (tb_bbs_like 동기)
  report_count      INT          NOT NULL DEFAULT 0,         -- B5 신고 누적
  comment_count     INT          NOT NULL DEFAULT 0,
  client_ip         VARCHAR(50),
  status            VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
                                                              -- PUBLISHED/HIDDEN/REPORTED/DELETED
  delete_yn         CHAR(1)      NOT NULL DEFAULT 'N',
  PRIMARY KEY (article_id),
  KEY idx_article_bbs_status (bbs_master_id, status, notice_yn, created_at),
  KEY idx_article_writer (writer_user_id),
  FULLTEXT KEY ft_article (title, content)
);
```

#### 3.3.1 상태 머신

```
        ┌─────────────┐
        │  PUBLISHED  │ ← 사용자가 글 등록 (기본 상태)
        └──┬───┬──┬───┘
   관리자  │   │  │  자동(B5 임계 도달) / 관리자 강제
  HIDDEN  ▼   │  └──▶ REPORTED  ──▶ HIDDEN/PUBLISHED 복귀 가능
        ┌─────────┐         │
        │ HIDDEN  │ ◀───────┘
        └─────┬───┘
   관리자/본인 │ soft delete
              ▼
        ┌─────────┐
        │ DELETED │ + delete_yn='Y'
        └─────────┘
```

#### 3.3.2 view_count 30분 쿠키 dedup

- 쿠키 `bbs_v_{articleId}` (30분 TTL, HttpOnly + Secure + SameSite=Lax)
- `incrementViewCount` 매퍼는 6감사컬럼 미주입 — `updated_by` 보존, `updated_at` 만 DB 트리거로 자동 갱신
- 비밀글 본문 차단 시 increment 도 skip

### 3.4 `tb_bbs_comment` — 댓글

```sql
CREATE TABLE tb_bbs_comment (
  comment_id        VARCHAR(40)  NOT NULL,                   -- CMT_<UUID>
  article_id        VARCHAR(40)  NOT NULL,                   -- ART_<UUID> FK
  parent_comment_id VARCHAR(40),                              -- self FK (대댓글, depth=2까지)
  writer_user_id    VARCHAR(40),
  writer_user_type  VARCHAR(20),
  writer_name       VARCHAR(100) NOT NULL,
  writer_password   VARCHAR(100),
  content           TEXT         NOT NULL,
  secret_yn         CHAR(1)      NOT NULL DEFAULT 'N',
  depth             INT          NOT NULL DEFAULT 1,         -- 1=top, 2=reply
  like_count        INT          NOT NULL DEFAULT 0,         -- B5
  report_count      INT          NOT NULL DEFAULT 0,         -- B5
  client_ip         VARCHAR(50),
  status            VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
  delete_yn         CHAR(1)      NOT NULL DEFAULT 'N',
  PRIMARY KEY (comment_id)
);
```

- depth=2 까지만 — 그 이상의 답글은 같은 부모로 평탄화
- `secret_yn='Y'` 시 본문 노출은 작성자 / 글주인 / 관리자만

### 3.5 `tb_bbs_like` — 좋아요 (B5)

```sql
CREATE TABLE tb_bbs_like (
  like_id        VARCHAR(40)  NOT NULL,                       -- BLK_<UUID>
  target_type    VARCHAR(20)  NOT NULL,                       -- BBS_ARTICLE / BBS_COMMENT
  target_id      VARCHAR(40)  NOT NULL,                       -- ART/CMT_<UUID>
  user_id        VARCHAR(40)  NOT NULL,                       -- MBR_<UUID>
  user_type      VARCHAR(20)  NOT NULL,                       -- MEMBER/EMPLOYEE/ADMIN
  delete_yn      CHAR(1)      NOT NULL DEFAULT 'N',
  PRIMARY KEY (like_id),
  UNIQUE KEY uk_bbs_like (target_type, target_id, user_id)
);
```

- `toggle()` — UNIQUE 충돌 시 활성/비활성 swap. soft delete 후 재토글은 `markActive`
- 토글 시 `like_count` 비정규화 동기

### 3.6 `tb_bbs_report` — 신고 (B5)

```sql
CREATE TABLE tb_bbs_report (
  report_id        VARCHAR(40)  NOT NULL,                     -- BRP_<UUID>
  target_type      VARCHAR(20)  NOT NULL,                     -- BBS_ARTICLE / BBS_COMMENT
  target_id        VARCHAR(40)  NOT NULL,
  reporter_user_id VARCHAR(40)  NOT NULL,
  reason_code      VARCHAR(50),                                -- tb_code_group=REPORT_REASON
  reason_text      VARCHAR(500),
  status           VARCHAR(20)  NOT NULL DEFAULT 'OPEN',       -- OPEN/REVIEWED/REJECTED
  review_note      VARCHAR(500),
  delete_yn        CHAR(1)      NOT NULL DEFAULT 'N',
  PRIMARY KEY (report_id)
);
```

- 활성 신고수가 `gopcms.board.report-threshold` (기본 5) 도달 시 자동 PUBLISHED → REPORTED 전환
- 관리자 검토 후 PUBLISHED 복귀 가능 (단 `report_count` 자동 리셋 안 함 — 운영 가이드)

### 3.7 `tb_search_index` — 통합 검색 (B6)

```sql
CREATE TABLE tb_search_index (
  index_id      VARCHAR(40)  NOT NULL,                        -- SIX_<UUID>
  entity_type   VARCHAR(20)  NOT NULL,                        -- BBS_ARTICLE/CONTENT/BBS_COMMENT
  entity_id     VARCHAR(40)  NOT NULL,
  site_id       VARCHAR(40),
  title         VARCHAR(300),
  content_text  MEDIUMTEXT,
  writer_name   VARCHAR(100),
  thumbnail_url VARCHAR(500),
  published_at  DATETIME,
  delete_yn     CHAR(1)      NOT NULL DEFAULT 'N',
  PRIMARY KEY (index_id),
  UNIQUE KEY uk_search_entity (entity_type, entity_id),
  FULLTEXT KEY ft_search (title, content_text, writer_name) WITH PARSER ngram
);
```

- BoardArticleServiceImpl `create/update/softDelete/adminUpdateStatus` 직후 `searchIndexService.upsertArticle()` 호출
- PUBLISHED 가 아니면 자동 soft delete (색인 비활성)
- HTML strip 은 OWASP HtmlSanitizer 의 `stripAll` 정책

### 3.8 DDL 요청서 인덱스 (시간순)

| 일자 | 파일 | 변경 |
|---|---|---|
| 2026-04-25 | [board_core_tables_and_url_access.sql](ddl-requests/2026-04-25_board_core_tables_and_url_access.sql) | 코어 3종 + URL access 6행 |
| 2026-04-26 | [bbs_article_file_group_id.sql](ddl-requests/2026-04-26_bbs_article_file_group_id.sql) | `tb_bbs_article.file_group_id NOT NULL` + FK |
| 2026-04-27 | [bbs_type_add_bodo.sql](ddl-requests/2026-04-27_bbs_type_add_bodo.sql) | BODO 타입 추가 |
| 2026-04-27 | [bbs_comment_secret.sql](ddl-requests/2026-04-27_bbs_comment_secret.sql) | 비밀댓글 컬럼 |
| 2026-04-27 | [bbs_like_and_report.sql](ddl-requests/2026-04-27_bbs_like_and_report.sql) | B5 like/report 테이블 + count 비정규화 |
| 2026-04-27 | [search_index.sql](ddl-requests/2026-04-27_search_index.sql) | B6 `tb_search_index` + ngram FULLTEXT |
| 2026-04-27 | [search_admin_url_access.sql](ddl-requests/2026-04-27_search_admin_url_access.sql) | `/admin/system/search` 가드 + 백필 도구 |
| 2026-05-01 | [roles_employee_manager_privacy.sql](ddl-requests/2026-05-01_roles_employee_manager_privacy.sql) | ROLE_EMPLOYEE / ROLE_MANAGER / ROLE_PRIVACY 신설 |
| 2026-05-01 | (DB 일괄) | 모든 ID `VARCHAR(36) → VARCHAR(40)` 확장 + `tb_bbs_master.download_auth` 컬럼 + 7값 CHECK |
| 2026-05-02 | [bbs_master_grouped_board_ids.sql](ddl-requests/2026-05-02_bbs_master_grouped_board_ids.sql) | **B7** `tb_bbs_master.grouped_board_ids VARCHAR(1000)` 컬럼 (멱등 ALTER, 백필 불필요) |

---

## 4. DTO 매트릭스

### 4.1 BbsType (enum)
```
NOTICE("공지"), BODO("보도자료"), FREE("자유"),
FAQ("FAQ"), QNA("Q&A"), GALLERY("갤러리"), FILE("자료실")
```
- 사이트 화면 템플릿 폴더 결정 (`front/board/{NAME}/{list,detail,write}.html`)

### 4.2 BbsMasterSaveForm 검증

| 필드 | 검증 |
|---|---|
| `siteId` | `@NotBlank @Size(max=40)` |
| `bbsCode` | `@Pattern(^[A-Za-z][A-Za-z0-9_\-]*$)` 사이트 내 UNIQUE |
| `bbsType` | `@Pattern(^(NOTICE\|BODO\|FREE\|FAQ\|QNA\|GALLERY\|FILE\|YOUTUBE)$)` |
| `readAuth` | `@Pattern(^(ALL\|MEMBER\|EMPLOYEE\|ADMIN)$)` |
| `writeAuth` | `@Pattern(^(MEMBER\|EMPLOYEE\|ADMIN)$)` GUEST 제거 |
| `downloadAuth` | `@Pattern(^(ANONYMOUS\|ROLE_MEMBER\|ROLE_EMPLOYEE\|ROLE_STAFF\|OWNER_PRIVACY\|ROLE_MANAGER\|ROLE_ADMIN)$)` |
| `fileSizeMaxMb` | `@Min(1)` 폼은 MB → entity byte 환산 |
| `groupedBoardIds` | `@Size(max=1000)` (B7) — 통합 게시판 대상 CSV. NULL/빈문자 = 일반 게시판. Service 단 추가 검증: 중복 제거 / 중첩 금지 / 24개 상한 |

### 4.3 상태/타입 enum

| Enum | 값 |
|---|---|
| `BbsArticleStatus` | PUBLISHED / HIDDEN / REPORTED / DELETED |
| `BbsCommentStatus` | PUBLISHED / HIDDEN / REPORTED / DELETED |
| `LikeTargetType` | BBS_ARTICLE / BBS_COMMENT |
| `ReportTargetType` | BBS_ARTICLE / BBS_COMMENT |
| `ReportStatus` | OPEN / REVIEWED / REJECTED |

---

## 5. ID Prefix 정책 (2026-05-01)

모든 PK 는 **`<3대문자 prefix>_<36자 UUID v7>` = 40자**. 도메인 식별성 강화 + 사고 시 추적 편의.

| 테이블 | prefix | 예 |
|---|---|---|
| `tb_bbs_master` | `BBM` | `BBM_019d9b80-0b79-78c8-af0d-47a764a8878d` |
| `tb_bbs_article` | `ART` | `ART_019d9b80-...` |
| `tb_bbs_comment` | `CMT` | `CMT_019d9b80-...` |
| `tb_bbs_category` | `BCT` | `BCT_019d9b80-...` |
| `tb_bbs_like` | `BLK` | `BLK_019d9b80-...` |
| `tb_bbs_report` | `BRP` | `BRP_019d9b80-...` |
| `tb_file_group` | `FG0` | `FG0_019d9b80-...` (2자 도메인 0 패딩) |
| `tb_search_index` | `SIX` | `SIX_019d9b80-...` |

호출: `UuidV7Generator.generate("ART")` → 자동 prefix 합성 + `[A-Z0-9]{3}` 검증.

기존 36자 데이터는 prefix 없이 영구 보존 — 백워드 호환을 위해 정규식 `([A-Z0-9]{3}_)?[0-9a-fA-F-]{36}` 양 형태 모두 허용.

---

## 6. download_auth 정책 (2026-05-01 신설)

### 6.1 7단계 정책 매트릭스

| 정책 | 라벨 | 비회원 | MEMBER | 작성자 본인 | EMPLOYEE | STAFF | PRIVACY 단독 | MANAGER | ADMIN |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `ANONYMOUS` | 누구나 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `ROLE_MEMBER` | 회원 | ✗ | ✓ | ✓ | ✓ | ✓ | ✗ | ✓ | ✓ |
| `ROLE_EMPLOYEE` | 직원 | ✗ | ✗ | ✗ | ✓ | ✓ | ✗ | ✓ | ✓ |
| `ROLE_STAFF` | STAFF | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ | ✓ | ✓ |
| **`OWNER_PRIVACY`** | 개인정보관리자 | ✗ | ✗ | **✓** | ✗ | ✗ | **✓** | ✗ ★ | ✗ ★ |
| `ROLE_MANAGER` | 책임자 | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ |
| `ROLE_ADMIN` | 전체관리자 | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ |

★ **OWNER_PRIVACY 만 ROLE_ADMIN 자동 통과 안 됨** — closure 단절(ROLE_PRIVACY parent NULL) 로 admin 의 role_ids CSV 에 PRIVACY UUID 가 들어가지 않음. 명시 부여만 통과.

### 6.2 정책 cascade 흐름

```
[1] 마스터 등록 시
    BbsMasterSaveForm.downloadAuth → BoardMasterServiceImpl.create()
    → m.setDownloadAuth(form.getDownloadAuth())
    → INSERT tb_bbs_master.download_auth = 사용자 선택값

[2] 글 작성 시
    BoardArticleServiceImpl.create()
    → resolveArticleDownloadAuth(noticeYn, master.downloadAuth)
       ├─ noticeYn='Y' → "ANONYMOUS" (★ 공지글 우선)
       └─ noticeYn='N' → master.downloadAuth (그대로)
    → ensureFileGroup(articleId, siteId, resolvedAuth)
    → file_group.download_auth 동기화 (UPSERT)

[3] 마스터 download_auth 변경 시 (수정)
    BoardMasterServiceImpl.update()
    → fileGroupMapper.cascadeUpdateDownloadAuthByBbs(bbsMasterId, newAuth)
    → JOIN tb_bbs_article + tb_file_group 일괄 UPDATE
    → WHERE notice_yn = 'N' (★ 공지글 제외 — ANONYMOUS 보존)
    → 감사 이벤트 BBS_MASTER_DOWNLOAD_AUTH_CASCADE

[4] 공지 토글 시 (adminToggleNotice)
    notice='Y' → file_group.download_auth = 'ANONYMOUS'
    notice='N' → file_group.download_auth = master.downloadAuth (회복)
    → 감사 이벤트 BBS_ARTICLE_NOTICE_FG_SYNC

[5] 다운로드 시 — FileServiceImpl.enforceDownloadAuth()
    각 정책별 분기 평가
    OWNER_PRIVACY 는 BoardArticleService.findCreatedBy() lookup
    ROLE_PRIVACY UUID 는 role_ids CSV 토큰 매치
```

### 6.3 OWNER_PRIVACY 통과 조건 (Step 6 — 2026-05-01)

```
1. role_ids CSV 에 ROLE_PRIVACY UUID(00000000-0000-7000-8000-000000000018) 포함 → 통과
   감사 이벤트 FILE_PRIVACY_DOWNLOAD reason=ROLE_PRIVACY
2. file_group.entity_type='BBS' 이고 article.created_by == 현재 user_id → 통과
   단 created_by ∉ {"", "ALL", "ANONYMOUS", "SYSTEM"} (sentinel 가드)
   감사 이벤트 FILE_PRIVACY_DOWNLOAD reason=OWNER
3. 그 외 → AccessDeniedException 403
```

**의도된 보안 동작**:
- ROLE_ADMIN 도 자동 통과 안 됨 — 운영 admin 이 자기 글이 아니면 차단
- closure 단절 + CSV 토큰 정확 매치 (substring 매치 회피)
- created_by sentinel 가드 — 시드/시스템 글의 대량 owner-bypass 사고 차단

### 6.4 공지글 ANONYMOUS 정책

모든 게시판에 공지글 기능 필수. 정책 우선순위 = **`notice_yn='Y'` ≫ master.download_auth**.

| 케이스 | file_group.download_auth | 결과 |
|---|---|---|
| OWNER_PRIVACY 게시판 + 공지글 | ANONYMOUS | 누구나 다운로드 ✓ |
| OWNER_PRIVACY 게시판 + 일반글 | OWNER_PRIVACY | 본인 + ROLE_PRIVACY 만 |
| 마스터 cascade 시 공지글 | ANONYMOUS 보존 | `WHERE notice_yn='N'` |
| 공지 토글 ON | ANONYMOUS | adminToggleNotice 즉시 반영 |
| 공지 토글 OFF | master.downloadAuth | 정책 자동 회복 |

---

## 7. 통합 게시판(전체글 보기) — B7 (2026-05-02 신설)

### 7.1 개념

`tb_bbs_master.grouped_board_ids` 가 비어있으면 **일반 게시판** (자기 글 CRUD), 채워져 있으면 **통합 게시판** — 여러 게시판의 글을 한 화면에 모아서 보여주는 read-only 뷰.

| 모드 | grouped_board_ids | list/count | 작성 | 수정 | 삭제 | 모더레이션 |
|---|---|:---:|:---:|:---:|:---:|:---:|
| 일반 | NULL/empty | 자기 글 | ✅ | ✅ | ✅ | ✅ |
| 통합 | "id1,id2,id3" | 대상 게시판들 UNION | ❌ | ❌ | ❌ | ❌ |

### 7.2 데이터 모델

```sql
ALTER TABLE tb_bbs_master
  ADD COLUMN grouped_board_ids VARCHAR(1000) NULL
  COMMENT 'CSV of source bbs_master_id (통합 게시판 모드). NULL = 일반 게시판';
```

- **CSV 포맷**: `bbs_master_id,bbs_master_id,...` (UUID v7 40자 prefix 포함 + 콤마 = 41자 → 최대 약 24개)
- **NULL = 일반 모드** — 기존 모든 게시판 영향 없음 (백필 불필요)
- **인덱스 X** — Service 단에서 split + IN 절로 사용
- DDL 요청서: [2026-05-02_bbs_master_grouped_board_ids.sql](ddl-requests/2026-05-02_bbs_master_grouped_board_ids.sql)

### 7.3 DTO / Service

#### BbsMaster (entity)
```java
private String groupedBoardIds;

public boolean isAggregator() {
    return groupedBoardIds != null && !groupedBoardIds.isBlank();
}

public List<String> getGroupedBoardIdList() {
    if (!isAggregator()) return Collections.emptyList();
    // split + trim + 빈값 제거
}
```

#### BbsMasterSaveForm
```java
@Size(max = 1000, message = "통합 게시판 대상은 최대 1000자(약 24개)까지")
private String groupedBoardIds;
```

#### BoardMasterServiceImpl.normalizeGroupedBoardIds(raw)
검증 규칙:
1. **공백/null → null** (일반 모드)
2. **split + trim + 빈값 제거 + 중복 제거** — `LinkedHashSet` 으로 입력 순서 보존
3. **자기 자신 포함 가능** (정책: 통합 게시판이 자기 게시판 글도 함께 노출 허용)
4. **site 무관** — cross-site 통합 허용
5. **중첩 금지** — 대상이 또 다른 통합 게시판이면 거부 (`IllegalArgumentException`)
6. **최대 24개** — DB `VARCHAR(1000)` 길이 제약

### 7.4 관리자 화면 (form.html)

`/admin/system/board/{bid}/edit` 의 "통합 게시판 (전체글 보기)" fieldset:

| 요소 | 동작 |
|---|---|
| 후보 테이블 | 활성 + 일반(비-aggregator) 게시판 목록 + **본인 마스터** (수정 모드 시 본인이 통합 게시판이어도 후보 포함) |
| 체크박스 | 각 행에 `grouped-board-cb` class. checked 상태는 `groupedBoardIds` CSV contains 검사로 자동 결정 |
| hidden input | `<input type="hidden" id="groupedBoardIds" th:field="*{groupedBoardIds}"/>` |
| sync JS | 체크박스 change 시 모든 checked.value 를 join(',') 로 hidden 에 직렬화 + 선택 카운트 표시 |
| CSP | `<script th:nonce="${cspNonce}">` |

후보 로딩 — `BoardMngController.listGroupCandidates(siteId, selfId)`:
```java
for (BbsMaster m : all) {
    // 일반 게시판은 모두 후보. 통합 게시판은 본인일 때만 후보 (자기 자신 포함 허용)
    if (m.isAggregator() && !Objects.equals(m.getBbsMasterId(), selfId)) continue;
    filtered.add(m);
}
```

### 7.5 SQL 분기 — list/count

`BbsArticleMapper.listAggregated` / `countAggregated` (예시):
```xml
<select id="listAggregated" ...>
  SELECT ...
  FROM tb_bbs_article a
  JOIN tb_bbs_master m ON m.bbs_master_id = a.bbs_master_id
  WHERE a.bbs_master_id IN
    <foreach collection="bbsMasterIds" item="id" open="(" separator="," close=")">
      #{id}
    </foreach>
    AND a.status = 'PUBLISHED'
    AND a.delete_yn = 'N'
    AND m.use_yn = 'Y' AND m.delete_yn = 'N'
  ORDER BY a.notice_yn DESC, a.created_at DESC
  LIMIT #{pageSize} OFFSET #{offset}
</select>
```

Service 가 `master.isAggregator()` 분기 — true 면 `listAggregated`, false 면 기존 단일 `findList`.

### 7.6 권한 정책

**제안**: 통합 게시판의 `read_auth` 가 게이트. 운영자가 대상 게시판들의 `read_auth` 가 통합 게시판의 `read_auth` 이하임을 보장 (운영 가이드).

대안 (후속): SQL 단에서 각 source 게시판의 read_auth 평가 — 비용 大 (master JOIN + role_ids CSV 매칭). v1 미적용.

**작성/수정/삭제는 통합 게시판에서 무조건 거부**:
- `BoardArticleService.create/update/softDelete` 진입 직후 `if (master.isAggregator()) throw new IllegalStateException(...)`
- 화면 단에서도 작성 버튼 / ⋯ 메뉴 숨김 (서버 거부는 방어선)

### 7.7 URL 라우팅 — detail은 source로 redirect

`/bbs/{siteCode}/{aggCode}/{articleId}` 진입 시:
1. articleId 조회 → 실제 `bbs_master_id` 확인
2. 그 master 의 bbsCode/siteCode 조회
3. `/bbs/{원래siteCode}/{원래bbsCode}/{articleId}` 로 302 redirect

**이유**: 댓글·첨부·좋아요·신고는 source 게시판의 정책(comment_yn, file_yn, write_auth 등) 따라야 함.

---

## 8. canManage 가드 — 수정/삭제/모더레이션 분리 (2026-05-02 신설)

통합 게시판 URL 로 진입한 경우, 작성자/관리자라도 **수정·삭제·모더레이션 버튼이 노출되지 않아야** 함. 원본 게시판 URL 로 가야 관리 가능.

### 8.1 핵심 변수

각 detail.html 의 `<section layout:fragment="content">` 에 단일 변수 선언:
```html
th:with="canManage=${master.bbsMasterId == article.bbsMasterId}"
```

### 8.2 가드 패턴

| 영역 | 가드 |
|---|---|
| **사용자 — 글 액션 바** | `<div th:if="${canManage and (canEdit or canDelete)}">` — 수정/삭제 버튼 묶음 |
| **사용자 — 댓글 ⋯ 메뉴** | `<details th:if="${canManage and (canEditComment or canDeleteComment)}">` — 댓글 수정/삭제 묶음 |
| **관리자 — 상단 삭제 버튼** | `<form th:if="${canManage}">` |
| **관리자 — 모더레이션 section** | `<section th:if="${canManage}">` 전체 숨김 (상태 변경 + 공지 토글) |
| **관리자 — 댓글 모더레이션 액션** | `<div th:if="${canManage}">` — 상태 select / 강제 삭제 |
| **사용자 안내** (관리자 detail) | `canManage=false` 시 "이 게시글은 다른 게시판의 글입니다 (통합 게시판 뷰). 수정·삭제·모더레이션은 원본 게시판에서만 가능합니다." 노출 |

### 8.3 적용 파일 (8 + 1)

- 사용자 detail × 8: `front/board/{NOTICE/BODO/FAQ/FREE/FILE/GALLERY/QNA/YOUTUBE}/detail.html`
- 관리자 detail × 1: `admin/system/board/article/detail.html`

### 8.4 알려진 한계

> ⚠️ UI 가드만으로는 우회 가능 (URL 직접 호출). **Service 단 가드** (BoardArticleServiceImpl `update / softDelete / adminUpdateStatus / adminToggleNotice` + BoardCommentServiceImpl `update / softDelete / adminUpdateStatus` 진입 직후 `article.bbsMasterId != path-bid` 거부) 추가가 후속 PR 필요.

---

## 9. 버튼 일관성 — 사용자 화면 (2026-05-02 통일)

### 9.1 토큰 매트릭스

모든 사용자 list/detail/write.html 의 버튼이 단일 토큰으로 통일. `!text-{color}` important 로 airbnb 등 사이트 layout CSS cascade 우회.

| 액션 | 클래스 |
|---|---|
| **등록/글쓰기/저장** (primary) | `inline-flex items-center gap-1 px-4 py-2 rounded-md bg-blue-600 !text-white text-sm font-semibold hover:bg-blue-700 shadow-sm transition` |
| **수정** (primary alt) | 동일 (페이지의 주 액션) |
| **삭제** (danger) | `inline-flex items-center gap-1 px-4 py-2 rounded-md border border-rose-300 bg-white !text-rose-700 text-sm font-semibold hover:bg-rose-50 shadow-sm transition` |
| **취소/목록** (secondary) | `inline-flex items-center gap-1 px-4 py-2 rounded-md border border-slate-300 bg-white !text-slate-700 text-sm font-semibold hover:bg-slate-50 shadow-sm transition` |

### 9.2 변경 핵심

- `rounded` → `rounded-md` — 부드러운 모서리
- `font-medium` → `font-semibold` — 시각적 강조
- `text-{color}` → `!text-{color}` — 사이트 layout CSS cascade 우회
- `red-*` → `rose-*` — 부드러운 위험 색상
- `shadow-sm transition` — depth + hover 부드러움
- `gap-1` — 아이콘 + 텍스트 조합 시 간격
- YOUTUBE 의 빨강 primary 도 다른 7개와 동일한 파랑 primary 로 통일

### 9.3 적용 파일 (16 + 7)

- detail.html × 8 — 수정 / 삭제 / 목록
- write.html × 8 — 저장(등록) / 취소
- list.html × 7 (이전 PR 에서 적용) — 글쓰기/등록 — `bg-slate-900 text-white` 가독성 문제 해소

---

## 10. Service 레이어 핵심 동작

### 10.1 BoardMasterServiceImpl

| 메서드 | 동작 |
|---|---|
| `create(form)` | bbs_code 사이트 내 UNIQUE 검증 → `normalizeGroupedBoardIds` (B7) → INSERT (download_auth + grouped_board_ids 포함) → 감사 BBS_MASTER_CREATE (`aggregator` 플래그 포함) |
| `update(form)` | 검증 → `normalizeGroupedBoardIds` → UPDATE → download_auth 변경 감지 시 cascade UPDATE 모든 article file_group (공지글 제외) → 감사 BBS_MASTER_UPDATE + DOWNLOAD_AUTH_CASCADE |
| `toggleUse(id, active)` | use_yn 토글, 멱등 |
| `softDelete(id)` | delete_yn='Y' + use_yn='N' |

### 10.2 BoardArticleServiceImpl

| 메서드 | 동작 |
|---|---|
| `create(form, ip)` | write_auth 검증 → ART_<UUID> 생성 → noticeYn 결정 (STAFF만 Y 가능) → resolveArticleDownloadAuth → ensureFileGroup → INSERT → searchIndexService.upsertArticle |
| `update(form, ip)` | ensureOwnerOnly → UPDATE → syncAttachments → file_group.download_auth 재동기화 → search 색인 갱신 |
| `softDelete(id)` | 작성자/관리자 검증 → status='DELETED' + delete_yn='Y' → 색인 자동 비활성 |
| `adminUpdateStatus` | STAFF 권한 → PUBLISHED/HIDDEN/REPORTED 토글 → 색인 동기 |
| `adminToggleNotice(id, notice)` | STAFF 권한 → notice_yn 토글 → file_group.download_auth cascade (Y → ANONYMOUS, N → master.downloadAuth) |
| `incrementViewCount(id)` | 6감사컬럼 미주입 — updated_by 보존 |
| `findCreatedBy(id)` | OWNER_PRIVACY 평가용 lightweight lookup. soft-delete 무관 |

### 10.3 BoardCommentServiceImpl

| 메서드 | 동작 |
|---|---|
| `create(form, articleId, me)` | comment_yn='Y' 검증, 부모 댓글 동일 article 검증, depth 자동 계산 (2 초과는 부모 평탄화) → INSERT → article.comment_count 재계산 |
| `update(form, commentId, me)` | 작성자/관리자 검증 → UPDATE |
| `softDelete(commentId, me)` | 작성자/관리자 → soft delete → comment_count 재계산 |
| `adminUpdateStatus(cid, status)` | STAFF 강제 상태 전환 + comment_count 재계산 |

### 10.4 BoardLikeServiceImpl (B5)

| 메서드 | 동작 |
|---|---|
| `toggle(target, id, me, sourceUrl)` | UNIQUE `(target_type, target_id, user_id)` 충돌 시 active swap → like_count 비정규화 동기 → 감사 BBS_LIKE_TOGGLE |
| `isLikedBy(target, id, userId)` | 사용자별 활성 좋아요 상태 |

### 10.5 BoardReportServiceImpl (B5)

| 메서드 | 동작 |
|---|---|
| `report(target, id, reporterId, reasonCode, reasonText)` | 중복 신고 거부 → INSERT → report_count 비정규화 → 임계 도달 시 자동 PUBLISHED → REPORTED + searchIndex sync |
| `search(criteria)` | 관리자 모더레이션 큐 |
| `review(reportId, status, note)` | OPEN → REVIEWED/REJECTED |

---

## 11. 사용자 화면 분기 — bbs_type 7타입

| 타입 | list | detail | write | 특이사항 |
|---|---|---|---|---|
| **NOTICE** | 단순 테이블 + 공지 정렬 | 본문 + 첨부 | STAFF 만 | 공지 게시판 |
| **BODO** | 보도자료 카드 + 출처 강조 | 보도일자 + press_name | STAFF | press_name 필드 |
| **FREE** | 단순 테이블 | 본문 + 댓글 | MEMBER 이상 | 자유게시판 |
| **FAQ** | 아코디언 (질문 클릭 → 답변) | inline expand | STAFF | 댓글 비활성 권장 |
| **QNA** | Q/A 카드 + 답변상태 pill | 비밀글 차단 안내 | MEMBER + 비밀체크박스 | 본인+관리자만 본문 |
| **GALLERY** | 썸네일 그리드 | 큰 이미지 + 본문 | MEMBER | 첫 이미지 cover |
| **FILE** | 파일 다운로드 강조 행 | 첨부 강조 + 다운로드 버튼 | MEMBER | confettied download UI |

각 타입의 템플릿: [`/templates/front/board/{TYPE}/{list,detail,write}.html`](../src/main/resources/templates/front/board/)

`BoardUsrController` 가 `master.bbsType` 분기로 view name 결정. 신규 타입 추가 시:
1. `BbsType` enum 추가
2. `front/board/{TYPE}/` 디렉터리 + 3 화면
3. `BbsMasterSaveForm.bbsType` `@Pattern` 정규식 갱신
4. (옵션) DB CHECK 제약 갱신

---

## 12. 감사 이벤트 — 5경로

| 이벤트 | 발생 위치 | 비고 |
|---|---|---|
| `BBS_MASTER_CREATE/UPDATE/USE_TOGGLE/DELETE` | BoardMasterServiceImpl | |
| `BBS_MASTER_DOWNLOAD_AUTH_CASCADE` | BoardMasterServiceImpl.update() | download_auth 변경 시 |
| `BBS_ARTICLE_CREATE/UPDATE/DELETE/STATUS/NOTICE` | BoardArticleServiceImpl | |
| `BBS_ARTICLE_NOTICE_FG_SYNC` | adminToggleNotice() | 공지 토글 → file_group cascade |
| `BBS_COMMENT_CREATE/UPDATE/DELETE/STATUS` | BoardCommentServiceImpl | |
| `BBS_CATEGORY_CREATE/UPDATE/USE_TOGGLE/DELETE` | BoardCategoryServiceImpl | |
| `BBS_LIKE_TOGGLE` | BoardLikeServiceImpl | |
| `BBS_REPORT_CREATE/REVIEW/AUTO_REPORTED` | BoardReportServiceImpl | 자동 임계 전환 별도 이벤트 |
| `FILE_PRIVACY_DOWNLOAD` | FileServiceImpl.enforceOwnerPrivacy() | OWNER_PRIVACY 통과 시 reason=OWNER/ROLE_PRIVACY |

---

## 13. 검색 색인 (B6) — `tb_search_index`

### 13.1 색인 동기화 훅

| 트리거 | Service 메서드 | 동작 |
|---|---|---|
| 글 생성 | `BoardArticleServiceImpl.create()` | upsertArticle (PUBLISHED 만 활성) |
| 글 수정 | `update()` | upsertArticle |
| 글 삭제 | `softDelete()` | softDeleteByEntity |
| 상태 전환 | `adminUpdateStatus()` | upsertArticle (PUBLISHED 가 아니면 자동 비활성) |
| 자동 REPORTED | `BoardReportServiceImpl.syncSearchIndexAfterAutoReport` | fresh fetch + upsert |
| 댓글 CUD | `BoardCommentServiceImpl` | upsertComment (옵션) |

### 13.2 검색 화면

`/search?keyword=...&entityType=ALL` — 사용자 화면 ([SearchUsrController](../src/main/java/com/gonet/primary/search/controller/SearchUsrController.java))

- ngram parser MATCH AGAINST IN NATURAL LANGUAGE MODE
- 도메인 탭 (ALL / BBS_ARTICLE / CONTENT)
- snippet 80자 추출 + 키워드 highlight
- 사이트 컨텍스트 자동 적용 (siteContext.siteId 필터)

### 13.3 백필 도구

`/admin/system/search` — 관리자 대시보드. [전체 색인 재구축] 버튼.

---

## 14. 알려진 한계 (후속 PR 후보)

1. **신고 누적 임계 도달 후 PUBLISHED 복귀 시 report_count 자동 리셋 안 함** — 운영 가이드: REVIEWED 처리 후에도 누적 기록 보존하는 것이 의도. 다음 임계 자동 전환 위해 관리자가 0으로 재설정 필요
2. **댓글 좋아요/신고 UI 가 7+1(QNA) 타입 모두 적용** (2026-04-28 해소)
3. **검색 결과 highlight 가 단순 String.replace 기반** — 정규식 escape / 다단어 검색 향후
4. **OWNER_PRIVACY 정책의 그룹 ZIP 다운로드** — 동일 정책 평가됨. 본인 글의 모든 첨부 일괄 zip 받기 자동 통과
5. **결재 워크플로** — v1 스코프 제외
6. **WYSIWYG / Tiptap / TUI Editor** — 본문 입력 도구 미통합 (현재는 textarea + sanitize)
7. **B7 통합 게시판 — Article list/count 분기 미적용** (2026-05-02) — 마스터 컬럼·DTO·Service 검증·관리자 화면은 완료. `BbsArticleMapper.listAggregated/countAggregated` + `BoardArticleService.list/count` 분기 + 사용자 list/detail 의 source 게시판 뱃지 표시 + detail redirect 는 후속 PR
8. **B7 canManage UI 가드만 존재 — Service 단 가드 미적용** (2026-05-02) — `BoardArticleServiceImpl.update/softDelete/adminUpdateStatus/adminToggleNotice` + `BoardCommentServiceImpl.update/softDelete/adminUpdateStatus` 진입 직후 `article.bbsMasterId != path-bid` 거부 가드 후속 PR 필요. URL 직접 호출 우회 방어선 부재
9. **B7 권한 정책 단순화** — 통합 게시판의 `read_auth` 가 게이트. 운영자가 대상 게시판들의 `read_auth` 가 통합 게시판의 `read_auth` 이하임을 보장 (운영 가이드). SQL 단 source 게시판별 read_auth 평가는 후속

---

## 15. 운영 시나리오 — 스모크 테스트

### 15.1 마스터 등록 → 글 작성 → 공지 토글

```
1. /admin/system/board/new — bbs_type=NOTICE, download_auth=ROLE_MEMBER
   → tb_bbs_master.download_auth='ROLE_MEMBER'

2. /bbs/{sc}/{bc}/write — 회원 A 가 첨부와 함께 글 작성
   → ensureFileGroup(articleId, siteId, 'ROLE_MEMBER')
   → tb_file_group.download_auth='ROLE_MEMBER'

3. 비회원 다운로드 시도 → 401 로그인 redirect ✓

4. STAFF 가 같은 글 detail → "공지" 토글 ON
   → adminToggleNotice → notice_yn='Y'
   → file_group.download_auth = 'ANONYMOUS' (cascade)

5. 비회원 재시도 → 200 다운로드 성공 ✓
```

### 15.2 OWNER_PRIVACY QNA

```
1. /admin/system/board/new — bbs_type=QNA, download_auth=OWNER_PRIVACY

2. 회원 A 가 글 + 첨부 작성
   → file_group.download_auth='OWNER_PRIVACY'

3. 회원 B 다운로드 시도 → 403 + log FILE_DOWNLOAD_DENIED_PRIVACY

4. 회원 A 본인 시도 → 200 + log FILE_PRIVACY_DOWNLOAD reason=OWNER

5. ROLE_ADMIN(PRIVACY 미부여) 시도 → 403 ★ 자동 통과 안 됨

6. ROLE_PRIVACY 보유자 시도 → 200 + log reason=ROLE_PRIVACY

7. STAFF 가 "공지" 토글 ON → ANONYMOUS 강등
8. 비회원 시도 → 200 ✓ (공지글 정책 우선)
```

### 15.3 마스터 download_auth 변경 cascade

```
1. 게시판 download_auth: ROLE_MEMBER → ROLE_ADMIN 변경 저장
2. cascadeUpdateDownloadAuthByBbs(bbsMasterId, 'ROLE_ADMIN')
   → 일반글 N개의 file_group → ROLE_ADMIN
   → 공지글 N개는 ANONYMOUS 보존 (★ WHERE notice_yn='N')
3. 일반회원의 일반글 첨부 다운로드 → 403
4. 일반회원의 공지글 첨부 다운로드 → 200 ✓
```

### 15.4 통합 게시판(B7) — 등록 → 진입 → 가드

```
1. /admin/system/board/new — 일반 게시판 A, B, C 등록
   → tb_bbs_master.grouped_board_ids = NULL (3건)

2. /admin/system/board/new — 통합 게시판 "ALL"
   → 폼의 통합 게시판 fieldset 에서 A/B/C 체크 (자기 자신 옵션 가능)
   → hidden input groupedBoardIds = "BBM_<A>,BBM_<B>,BBM_<C>"
   → BoardMasterServiceImpl.create() → normalizeGroupedBoardIds()
   → INSERT tb_bbs_master.grouped_board_ids = "BBM_<A>,BBM_<B>,BBM_<C>"
   → 감사 BBS_MASTER_CREATE { aggregator: true }

3. 관리자 list 화면 → "ALL" 행에 [통합] 뱃지 ✓

4. ALL 게시판의 article detail 페이지 (관리자) 진입
   /admin/system/board/{ALL_id}/article/{aid}
   → 만약 article.bbsMasterId == ALL_id (자기 자신 포함 정책에 해당) → canManage=true
   → 만약 article.bbsMasterId != ALL_id (A/B/C 의 글) → canManage=false
       → 삭제 / 모더레이션 section / 댓글 액션 모두 자동 비노출
       → "다른 게시판의 글입니다" 안내 박스 노출

5. 사용자 detail (front/board/{TYPE}/detail.html) 도 동일
   → 통합 URL 진입 시 수정/삭제 버튼 비노출 → 원본 게시판으로 가야 관리 가능

6. 통합 게시판이 또 다른 통합 게시판에 포함되려고 시도
   → normalizeGroupedBoardIds 가 IllegalArgumentException
   → "통합 게시판을 다시 통합 대상으로 포함할 수 없습니다 (중첩 금지)" ✓
```

---

## 16. 변경 이력

| 일자 | 변경 |
|---|---|
| 2026-04-25 | B1 마스터 CRUD |
| 2026-04-26 | B2.1 글 CRUD + B2.2 첨부 picker, GUEST 항구 제외 |
| 2026-04-27 | B3 댓글, B4 카테고리/BODO/비밀댓글, B5 좋아요/신고/자동 REPORTED, B6 검색 색인, B5/B6 후속 마무리 |
| 2026-05-01 | **download_auth 7단계 정책** (ANONYMOUS / ROLE_MEMBER / ROLE_EMPLOYEE / ROLE_STAFF / OWNER_PRIVACY / ROLE_MANAGER / ROLE_ADMIN), **OWNER_PRIVACY 가드 + ROLE_PRIVACY**, **공지글 ANONYMOUS 우선 정책**, **VARCHAR(40) ID + 도메인 prefix** (BBM/ART/CMT/BCT/BLK/BRP/FG0/SIX) |
| 2026-05-02 | **B7 통합 게시판(전체글 보기)** — `tb_bbs_master.grouped_board_ids VARCHAR(1000)` 신설 + DTO `BbsMaster.isAggregator() / getGroupedBoardIdList()` + `BoardMasterServiceImpl.normalizeGroupedBoardIds()` (자기 자신 포함 허용 / 중첩 금지 / site 무관 / 24개 상한) + 관리자 form/list/detail 화면 (체크박스 picker · 통합 뱃지 · 정보 패널). **canManage 가드** — `master.bbsMasterId == article.bbsMasterId` 일 때만 수정/삭제/모더레이션 노출 (사용자 8 detail + 관리자 detail). **버튼 일관성 통일** — 16+ 파일 등록/수정/삭제/취소/목록 단일 토큰화 (`!text-{color}` important + `rounded-md` + `font-semibold` + `shadow-sm transition`) |
