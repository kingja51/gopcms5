-- ============================================================================
-- V1 — 코어: 레이아웃 · 템플릿 · 테마 · 사이트 · 메뉴 · 컨텐츠  (MariaDB)
-- ----------------------------------------------------------------------------
-- · PK/FK 규칙: VARCHAR(40) = 접두어(대문자3)+"_"+UUIDv7  (doc/conventions.md §1~2)
--   → ID 계열 컬럼은 CHARACTER SET ascii COLLATE ascii_bin
--     (테이블 기본 utf8mb4_uca1400_ai_ci 는 대소문자 무시 — 키 오염 차단 + 인덱스 절약)
-- · 3축 모델: layout(구조/뷰 폴더) · template(시각 언어 CSS) · theme(색 클래스)
--   → doc/template-resolver-design.md
-- · 원 DDL(사용자 제공) 대비 반영 사항:
--   tb_template.layout_path → default_layout_id FK 로 교체
--   tb_site.layout_id(널 허용, NULL=템플릿 기본) 추가 · tb_layout / tb_theme 신설
-- ============================================================================

-- ① 레이아웃 (구조 프레임 — 와이어프레임 A~G. 뷰 폴더 templates/layouts/{layout_code}/)
CREATE TABLE `tb_layout` (
  `layout_id`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '레이아웃 ID (LAY_ + UUIDv7)',
  `layout_code`   varchar(50)  NOT NULL COMMENT '레이아웃 코드 (layout-001 … = 뷰 폴더명)',
  `layout_name`   varchar(100) NOT NULL COMMENT '레이아웃 명',
  `wireframe_ref` varchar(30)  DEFAULT NULL COMMENT '와이어프레임 원전 (frame001…)',
  `description`   varchar(500) DEFAULT NULL COMMENT '설명',
  `sort_order`    int(11)      NOT NULL  DEFAULT  0 COMMENT '정렬 순서',
  `use_yn`        char(1)      NOT NULL  DEFAULT  'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`     char(1)      NOT NULL  DEFAULT  'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`    varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`    varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`    timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`    varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`    varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`    timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`layout_id`),
  UNIQUE KEY `uk_layout_code` (`layout_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='레이아웃 (구조 프레임)';

-- ② 템플릿 (시각 언어 — CSS 1장: /tmpl/css/{template_code}.css 규약 경로)
CREATE TABLE `tb_template` (
  `template_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '템플릿 ID (TPL_ + UUIDv7)',
  `template_code`     varchar(50)  NOT NULL COMMENT '템플릿 코드 (= CSS 파일명 /tmpl/css/{code}.css)',
  `template_name`     varchar(100) NOT NULL COMMENT '템플릿 명',
  `default_layout_id` varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '기본 레이아웃 (사이트 layout_id NULL 시 적용)',
  `design_md`         text         DEFAULT NULL COMMENT 'Claude Design Md (시각 언어 원전)',
  `description`       varchar(500) DEFAULT NULL COMMENT '설명',
  `use_yn`            char(1)      NOT NULL  DEFAULT  'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`         char(1)      NOT NULL  DEFAULT  'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`        varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`        varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`        timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`        varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`        varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`        timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`template_id`),
  UNIQUE KEY `uk_template_code` (`template_code`),
  KEY `idx_template_layout` (`default_layout_id`),
  CONSTRAINT `fk_template_layout` FOREIGN KEY (`default_layout_id`) REFERENCES `tb_layout` (`layout_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='템플릿 (시각 언어)';

-- ③ 테마 (템플릿별 색 변형 — 파일 없음, html 클래스 스왑. 관리자 셀렉트 소스)
CREATE TABLE `tb_theme` (
  `theme_id`    varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '테마 ID (THM_ + UUIDv7)',
  `template_id` varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '소속 템플릿 ID',
  `theme_code`  varchar(30)  NOT NULL COMMENT '테마 코드 (blue·teal·indigo·green…)',
  `theme_name`  varchar(100) NOT NULL COMMENT '테마 명',
  `css_class`   varchar(50)  NOT NULL  DEFAULT  '' COMMENT 'html 클래스 ('' = 템플릿 기본 브랜드)',
  `sort_order`  int(11)      NOT NULL  DEFAULT  0 COMMENT '정렬 순서',
  `use_yn`      char(1)      NOT NULL  DEFAULT  'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`   char(1)      NOT NULL  DEFAULT  'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`  varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`  varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`  timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`  varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`  varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`  timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`theme_id`),
  UNIQUE KEY `uk_theme` (`template_id`,`theme_code`),
  -- tb_site 의 복합 FK (template_id, theme_id) 대상 — "선택 테마는 선택 템플릿 소속" 을 DB 가 보증
  UNIQUE KEY `uk_theme_tpl` (`template_id`,`theme_id`),
  CONSTRAINT `fk_theme_template` FOREIGN KEY (`template_id`) REFERENCES `tb_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='테마 (템플릿별 색 변형)';

-- ④ 사이트 (멀티사이트 마스터 — 선택 흐름: 사이트 생성 → 템플릿 → 테마 → 레이아웃)
--    개선(2026-07-28): theme 문자열 → theme_id 복합 FK(템플릿 소속 보증) ·
--    default_yn(폴백 사이트) · parent_site_id(다국어/서브사이트) · 예약 site_code CHECK ·
--    로고/파비콘 · updated_at ON UPDATE · created_at 문법 정정(NULL DEFAULT 순서)
CREATE TABLE `tb_site` (
  `site_id`        varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '사이트 ID (SIT_ + UUIDv7)',
  `site_code`      varchar(30)  NOT NULL COMMENT '사이트 코드 (URL 경로 식별자 /{siteCode}/… — 소문자·숫자·하이픈)',
  `site_name`      varchar(100) NOT NULL COMMENT '사이트 명',
  `domain`         varchar(255) DEFAULT NULL COMMENT '커스텀 도메인 (소문자 저장 — siteCode 판별 보조, canonical 은 경로. conventions.md §5)',
  `default_lang`   varchar(10)  NOT NULL DEFAULT 'ko' COMMENT '기본 언어 (ko/en/ja/zh)',
  `parent_site_id` varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '부모 사이트 (다국어 변형·서브사이트 트리, NULL=대표)',
  `template_id`    varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '선택 템플릿 (NULL=미선택 → krds 기본 템플릿 폴백)',
  `theme_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '선택 테마 (NULL=템플릿 기본 브랜드. 소속 검증=fk_site_theme 복합 FK)',
  `layout_id`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '선택 레이아웃 (NULL=템플릿 기본 레이아웃)',
  `default_yn`     char(1)      NOT NULL DEFAULT 'N' COMMENT '기본 사이트 — 도메인·경로 미해석 시 폴백 (전체 1개, 앱 검증)' CHECK (`default_yn` in ('Y','N')),
  `sort_order`     int(11)      NOT NULL DEFAULT 0 COMMENT '관리 목록 정렬',
  `logo_path`      varchar(255) DEFAULT NULL COMMENT '로고 이미지 경로 (마스트헤드, NULL=사이트명 텍스트)',
  `favicon_path`   varchar(255) DEFAULT NULL COMMENT '파비콘 경로 (NULL=시스템 기본)',
  `description`    varchar(500) DEFAULT NULL COMMENT '설명',
  `head_meta`      text         DEFAULT NULL COMMENT '사이트별 <head> 삽입 HTML 조각 (meta/link/script). 관리자 textarea 편집, th:utext 출력',
  `copyright`      text         DEFAULT NULL COMMENT '사이트별 footer copyright 문구 (HTML 허용)',
  `use_yn`         char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`      char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (소프트 삭제)' CHECK (`delete_yn` in ('Y','N')),
  `created_by`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`     varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`     timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`     varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`     timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`site_id`),
  UNIQUE KEY `uk_site_code` (`site_code`),
  UNIQUE KEY `uk_site_domain` (`domain`),
  KEY `idx_site_template` (`template_id`),
  KEY `idx_site_layout` (`layout_id`),
  KEY `idx_site_parent` (`parent_site_id`),
  KEY `idx_site_active` (`use_yn`,`delete_yn`,`sort_order`),
  -- site_code 는 URL 첫 세그먼트 — 패턴 강제 + 상위 네임스페이스 예약어 차단 (conventions.md §5)
  CONSTRAINT `chk_site_code_pattern`  CHECK (`site_code` REGEXP '^[a-z0-9][a-z0-9-]{1,29}$'),
  CONSTRAINT `chk_site_code_reserved` CHECK (`site_code` NOT IN
    ('adm','api','bbs','member','prg','search','static','css','js','fonts','tmpl','error','actuator')),
  CONSTRAINT `fk_site_template` FOREIGN KEY (`template_id`) REFERENCES `tb_template` (`template_id`),
  CONSTRAINT `fk_site_layout`   FOREIGN KEY (`layout_id`)   REFERENCES `tb_layout` (`layout_id`),
  -- 복합 FK: (템플릿, 테마) 쌍이 tb_theme 에 실존해야 함 → 템플릿 변경 시 테마 리셋을 DB 가 강제
  CONSTRAINT `fk_site_theme`    FOREIGN KEY (`template_id`,`theme_id`)
                                REFERENCES `tb_theme` (`template_id`,`theme_id`),
  CONSTRAINT `fk_site_parent`   FOREIGN KEY (`parent_site_id`) REFERENCES `tb_site` (`site_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='사이트 (멀티사이트 마스터)';

-- ⑤ 메뉴 (계층 트리 — depth 1~4 저장, GNB/LNB 1~3뎁스 · 사이트맵 4뎁스 렌더)
CREATE TABLE `tb_menu` (
  `menu_id`          varchar(40)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '메뉴 ID (MNU_ + UUIDv7)',
  `site_id`          varchar(40)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '사이트 ID',
  `site_code`        varchar(30)   DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `parent_menu_id`   varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '부모 메뉴 ID (NULL=1뎁스)',
  `menu_name`        varchar(100)  NOT NULL COMMENT '메뉴 명',
  `menu_type`        varchar(20)   NOT NULL COMMENT '메뉴 타입' CHECK (`menu_type` in ('CONTENT','BOARD','URL','FOLDER')),
  `link_target_id`   varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '연결 대상 ID (CNT_/BBM_ — 접두어로 대상 판별)',
  `link_url`         varchar(1000) DEFAULT NULL COMMENT '직접 링크 URL (menu_type=URL)',
  `sort_order`       int(11)       NOT NULL  DEFAULT  0 COMMENT '정렬 순서',
  `depth`            int(11)       NOT NULL  DEFAULT  1 COMMENT '트리 깊이',
  `auth_required_yn` char(1)       NOT NULL  DEFAULT  'N' COMMENT '인증 필요 여부' CHECK (`auth_required_yn` in ('Y','N')),
  `use_yn`           char(1)       NOT NULL  DEFAULT  'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`        char(1)       NOT NULL  DEFAULT  'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`       varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`       varchar(50)   DEFAULT NULL COMMENT '생성자 IP',
  `created_at`       timestamp     NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`       varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`       varchar(50)   DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`       timestamp     NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`menu_id`),
  KEY `idx_menu_site_parent` (`site_id`,`parent_menu_id`,`sort_order`),
  KEY `idx_menu_active` (`site_id`,`use_yn`,`delete_yn`,`sort_order`),
  CONSTRAINT `fk_menu_site`   FOREIGN KEY (`site_id`)        REFERENCES `tb_site` (`site_id`),
  CONSTRAINT `fk_menu_parent` FOREIGN KEY (`parent_menu_id`) REFERENCES `tb_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='메뉴 (계층 트리)';

-- ⑥ 컨텐츠 페이지
CREATE TABLE `tb_content` (
  `content_id`           varchar(40)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '콘텐츠 ID (CNT_ + UUIDv7)',
  `site_id`              varchar(40)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '사이트 ID',
  `site_code`            varchar(30)   DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `menu_id`              varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '메뉴 ID',
  `title`                varchar(300)  NOT NULL COMMENT '제목',
  `slug`                 varchar(200)  DEFAULT NULL COMMENT 'URL slug',
  `body`                 mediumtext    DEFAULT NULL COMMENT '본문 (WYSIWYG, sanitized)',
  `original_content`     text          DEFAULT NULL COMMENT '원본 콘텐츠 MD (Markdown 원본 — body 는 렌더된 HTML)',
  `summary`              varchar(1000) DEFAULT NULL COMMENT '요약',
  `body_hash`            char(64)      DEFAULT NULL COMMENT '디스크 HTML 원문 SHA-256(hex). 동기화 변경 감지 키. NULL 이면 강제 sync 대상',
  `meta_keywords`        varchar(500)  DEFAULT NULL COMMENT 'SEO keywords',
  `meta_description`     varchar(500)  DEFAULT NULL COMMENT 'SEO description',
  `status`               varchar(20)   NOT NULL  DEFAULT  'DRAFT' COMMENT '상태' CHECK (`status` in ('DRAFT','REVIEW','APPROVED','PUBLISHED','UNPUBLISHED')),
  `published_at`         datetime      DEFAULT NULL COMMENT '게시 일시',
  `publish_scheduled_at` datetime      DEFAULT NULL COMMENT '예약 발행 일시',
  `unpublish_at`         datetime      DEFAULT NULL COMMENT '게시 만료 일시',
  `view_count`           bigint(20) unsigned NOT NULL  DEFAULT  0 COMMENT '조회수',
  `version_no`           int(11)       NOT NULL  DEFAULT  1 COMMENT '현재 버전 번호',
  `delete_yn`            char(1)       NOT NULL  DEFAULT  'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`           varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`           varchar(50)   DEFAULT NULL COMMENT '생성자 IP',
  `created_at`           timestamp     NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`           varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`           varchar(50)   DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`           timestamp     NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`content_id`),
  UNIQUE KEY `uk_content_slug` (`site_id`,`slug`),
  KEY `idx_content_menu` (`menu_id`,`status`),
  KEY `idx_content_status_pub` (`status`,`published_at`),
  FULLTEXT KEY `ft_content` (`title`,`body`,`summary`),
  CONSTRAINT `fk_content_site` FOREIGN KEY (`site_id`) REFERENCES `tb_site` (`site_id`),
  CONSTRAINT `fk_content_menu` FOREIGN KEY (`menu_id`) REFERENCES `tb_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='콘텐츠 페이지';
