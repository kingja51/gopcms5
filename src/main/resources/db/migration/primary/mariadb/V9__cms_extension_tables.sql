-- ============================================================================
-- V9 (primary) — CMS 확장 21테이블: 파일 · 게시판 · 배너/팝업 · 일정 · 설문 · 직원 · 공통
-- ----------------------------------------------------------------------------
-- 원안: D:\test\primary.sql (선행 프로젝트 덤프). 아래를 교정해 반영한다.
--
-- [파괴적 구문 제거]
--   · `DROP TABLE IF EXISTS` 전량 삭제 — 마이그레이션은 앞으로만 간다.
--   · **tb_department 는 만들지 않는다** — V6 에서 이미 생성됐고 tb_admin 이 FK 로 물고 있다.
--     원안대로 DROP+CREATE 하면 관리자 계정의 부서 참조가 끊긴다.
--   · AUTO_INCREMENT=87 등 덤프 잔재 제거.
--
-- [규약 정합]
--   · 전 ID 컬럼 `CHARACTER SET ascii COLLATE ascii_bin` (conventions §1.2)
--   · 대문자 컬럼명 교정: PASSWORD→password · STATUS→status · POSITION→job_position
--   · CHECK 은 테이블 레벨 명명 제약 (V8 실측 — 인라인 CHECK 은 값 추가 시 컬럼 재정의 필요)
--   · 전 테이블·컬럼 COMMENT
--
-- [설계 교정 — 원안의 문제]
--   · tb_bbs_article.writer_user_type 에 상충하는 CHECK 2개(4값/6값)가 걸려 있었다.
--     엄격한 쪽이 이겨 STAFF·MANAGER 가 거부되므로 하나로 합쳤다(tb_bbs_comment 동일).
--   · tb_bbs_article.file_group_id 가 NOT NULL 이라 첨부 없는 글도 빈 파일그룹을 만들어야 했다
--     → nullable 로 완화.
--   · FK 누락 보강: tb_popup·tb_file_group·tb_bbs_master → tb_site,
--     tb_banner·tb_popup → tb_file_group, tb_bbs_article → tb_bbs_category,
--     tb_survey_answer → tb_survey_option.
--   · tb_holiday.`year` 는 데이터 타입명과 겹쳐 혼동을 부른다 → holiday_year.
--   · site_code 폭을 tb_site 와 같은 varchar(30) 으로 통일 (원안 50 — 조인 시 불일치 소지).
--
-- [의도적으로 FK 를 두지 않은 곳]
--   · menu_id · target_id(좋아요/신고) · entity_id(파일그룹) — 대상 테이블이 상황에 따라
--     달라지는 다형 참조라 FK 로 묶을 수 없다. 값 참조 + 서비스 계층 검증으로 다룬다.
-- ============================================================================

-- ══ 1. 파일 ═════════════════════════════════════════════════════════════════

-- 파일 그룹 — 업로드 묶음의 소유자. 게시글·컨텐츠·배너가 group_id 하나만 들고 있으면 된다.
CREATE TABLE `tb_file_group` (
  `file_group_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '파일그룹 ID (FGR_ + UUIDv7)',
  `entity_type`     varchar(50)  NOT NULL COMMENT '소유 엔티티 유형 (BBS/CONTENT/BANNER/POPUP/MEMBER …)',
  `entity_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '소유 엔티티 ID (다형 참조 — FK 없음)',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID (전역 자료는 NULL)',
  `site_code`       varchar(30)  DEFAULT NULL COMMENT 'site_code 캐시',
  `download_auth`   varchar(20)  NOT NULL DEFAULT 'ROLE_MEMBER' COMMENT '다운로드 최소 권한',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`file_group_id`),
  KEY `idx_filegroup_entity` (`entity_type`,`entity_id`),
  KEY `idx_filegroup_site` (`site_id`,`delete_yn`),
  CONSTRAINT `fk_filegroup_site` FOREIGN KEY (`site_id`) REFERENCES `tb_site` (`site_id`),
  CONSTRAINT `chk_filegroup_download_auth` CHECK (`download_auth` in ('ANONYMOUS','ROLE_MEMBER','ROLE_EMPLOYEE','ROLE_STAFF','OWNER_PRIVACY','ROLE_MANAGER','ROLE_ADMIN')),
  CONSTRAINT `chk_filegroup_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='파일 그룹 — 업로드 묶음의 소유·권한 단위';

-- 파일 — 실제 물리 파일 1건. 웹루트 밖에 저장하고 컨트롤러를 통해서만 내보낸다.
CREATE TABLE `tb_file` (
  `file_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '파일 ID (FIL_ + UUIDv7)',
  `file_group_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '소속 파일그룹 ID',
  `original_name`   varchar(512) NOT NULL COMMENT '원본 파일명 (사용자 제시 — 그대로 신뢰하지 않는다)',
  `stored_name`     varchar(512) NOT NULL COMMENT '저장 파일명 (UUIDv7.ext — 원본명과 분리해 경로 조작 차단)',
  `stored_path`     varchar(1024) NOT NULL COMMENT '저장 경로 (웹루트 외부)',
  `thumbnail_path`  varchar(1024) DEFAULT NULL COMMENT '썸네일 경로 (비이미지·생성 생략 시 NULL)',
  `extension`       varchar(20)  NOT NULL COMMENT '확장자 (소문자)',
  `mime_detected`   varchar(100) NOT NULL COMMENT 'Tika 매직바이트 판별 MIME — 방어의 기준값',
  `mime_client`     varchar(100) DEFAULT NULL COMMENT '클라이언트가 제시한 Content-Type (참고용, 신뢰 금지)',
  `size_bytes`      bigint(20)   NOT NULL COMMENT '파일 크기(바이트)',
  `file_hash`       char(64)     NOT NULL COMMENT 'SHA-256 — 무결성 점검(FIM)·중복 판정',
  `original_content` mediumtext  DEFAULT NULL COMMENT '문서 파서가 추출한 본문(Markdown) — 검색·요약용',
  `is_image_yn`     char(1)      NOT NULL DEFAULT 'N' COMMENT '이미지 여부',
  `reencoded_yn`    char(1)      NOT NULL DEFAULT 'N' COMMENT '재인코딩 완료 여부 (이미지 내 스크립트 제거)',
  `virus_scan_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '백신 검사 상태',
  `download_count`  int(10) unsigned NOT NULL DEFAULT 0 COMMENT '다운로드 횟수',
  `sort_order`      int(11)      NOT NULL DEFAULT 0 COMMENT '그룹 내 정렬 순서',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`file_id`),
  KEY `idx_file_group` (`file_group_id`,`sort_order`),
  KEY `idx_file_hash` (`file_hash`),
  KEY `idx_file_scan` (`virus_scan_status`,`created_at`),
  CONSTRAINT `fk_file_group` FOREIGN KEY (`file_group_id`) REFERENCES `tb_file_group` (`file_group_id`),
  CONSTRAINT `chk_file_is_image_yn` CHECK (`is_image_yn` in ('Y','N')),
  CONSTRAINT `chk_file_reencoded_yn` CHECK (`reencoded_yn` in ('Y','N')),
  CONSTRAINT `chk_file_delete_yn` CHECK (`delete_yn` in ('Y','N')),
  CONSTRAINT `chk_file_scan_status` CHECK (`virus_scan_status` in ('PENDING','CLEAN','INFECTED','ERROR','QUARANTINED','RESCANNING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='업로드 파일 — 다중 방어(확장자·매직바이트·재인코딩) 결과 보관';

-- ══ 2. 직원 (내부 로그인) ═══════════════════════════════════════════════════
-- tb_admin 과 같은 계열의 계정 테이블. 인증 원천 vw_user_login 에 EMPLOYEE 분기를
-- 추가할 때 이 테이블을 UNION 한다 (V6 뷰 주석의 확장 지점).
CREATE TABLE `tb_employee` (
  `employee_id`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '직원 ID (EMP_ + UUIDv7)',
  `employee_seq`    bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '직원 일련번호 (vw_user_login.uniq_id)',
  `department_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '부서 ID',
  `department_name` varchar(100) DEFAULT NULL COMMENT '부서명 캐시 (조직 개편 시점 스냅샷)',
  `login_id`        varchar(50)  NOT NULL COMMENT '로그인 ID',
  `password`        varchar(100) NOT NULL COMMENT '비밀번호 (BCrypt)',
  `password_changed_at` datetime NOT NULL COMMENT '비밀번호 변경 일시',
  `password_expire_at`  datetime DEFAULT NULL COMMENT '비밀번호 만료 일시',
  `role_ids`        text         DEFAULT NULL COMMENT '역할 ID CSV — closure 전개 스냅샷',
  `role_codes`      text         DEFAULT NULL COMMENT 'ROLE 코드 CSV — 위와 동기(원안 누락분 추가, tb_admin 과 정합)',
  `employee_no`     varchar(50)  DEFAULT NULL COMMENT '사번',
  `employee_name`   varchar(512) NOT NULL COMMENT '{AG} AES-256-GCM 이름',
  `email`           varchar(512) NOT NULL COMMENT '{AG} AES-256-GCM 이메일',
  `email_hash`      char(64)     NOT NULL COMMENT 'HMAC-SHA256(email) — 검색·중복확인',
  `phone`           varchar(512) DEFAULT NULL COMMENT '{AG} AES-256-GCM 전화번호',
  `job_position`    varchar(100) DEFAULT NULL COMMENT '직위 (원안 POSITION — 함수명 충돌 회피해 개명)',
  `hire_date`       date         DEFAULT NULL COMMENT '입사일',
  `resign_date`     date         DEFAULT NULL COMMENT '퇴사일',
  `two_factor_enabled_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '2FA 활성화 여부',
  `two_factor_secret`     varchar(512) DEFAULT NULL COMMENT '{AG} TOTP 시크릿',
  `ip_whitelist`    varchar(2000) DEFAULT NULL COMMENT '허용 IP CIDR CSV',
  `allowed_time_from` time       DEFAULT NULL COMMENT '접속 허용 시작 시각',
  `allowed_time_to`   time       DEFAULT NULL COMMENT '접속 허용 종료 시각',
  `status`          varchar(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '상태',
  `login_fail_count` int(11)     NOT NULL DEFAULT 0 COMMENT '로그인 실패 누계 (5회 잠금)',
  `locked_until`    datetime     DEFAULT NULL COMMENT '잠금 해제 예정 일시',
  `captcha_required_yn` char(1)  NOT NULL DEFAULT 'N' COMMENT '잠금 해제 후 CAPTCHA 강제 — 다음 성공 시 자동 N',
  `last_login_at`   datetime     DEFAULT NULL COMMENT '최종 로그인 일시',
  `last_login_ip`   varchar(50)  DEFAULT NULL COMMENT '최종 로그인 IP',
  `last_access_at`  datetime     DEFAULT NULL COMMENT '최종 접속 일시',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`employee_id`),
  UNIQUE KEY `uk_employee_seq` (`employee_seq`),
  UNIQUE KEY `uk_employee_login` (`login_id`),
  UNIQUE KEY `uk_employee_no` (`employee_no`),
  KEY `idx_employee_email_hash` (`email_hash`),
  KEY `idx_employee_dept` (`department_id`),
  KEY `idx_employee_status` (`status`,`delete_yn`),
  CONSTRAINT `fk_employee_dept` FOREIGN KEY (`department_id`) REFERENCES `tb_department` (`department_id`),
  CONSTRAINT `chk_employee_status` CHECK (`status` in ('ACTIVE','LOCKED','INACTIVE','SUSPENDED','RESIGNED')),
  CONSTRAINT `chk_employee_2fa_yn` CHECK (`two_factor_enabled_yn` in ('Y','N')),
  CONSTRAINT `chk_employee_captcha_yn` CHECK (`captcha_required_yn` in ('Y','N')),
  CONSTRAINT `chk_employee_delete_yn` CHECK (`delete_yn` in ('Y','N')),
  CONSTRAINT `chk_employee_fail_count` CHECK (`login_fail_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='직원 (내부 로그인 계정)';

-- ══ 3. 배너 · 팝업 ══════════════════════════════════════════════════════════

CREATE TABLE `tb_banner` (
  `banner_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '배너 ID (BNR_ + UUIDv7)',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '사이트 ID',
  `site_code`       varchar(30)  DEFAULT NULL COMMENT 'site_code 캐시',
  `banner_title`    varchar(200) NOT NULL COMMENT '배너 제목(관리용)',
  `banner_location` varchar(50)  NOT NULL COMMENT '노출 위치 코드 (MAIN_TOP/SIDE 등 — 레이아웃 슬롯과 약속)',
  `file_group_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '이미지 파일그룹 — 첫 이미지를 노출',
  `alt_text`        varchar(500) DEFAULT NULL COMMENT '대체 텍스트 (접근성 필수)',
  `link_url`        varchar(1000) DEFAULT NULL COMMENT '클릭 시 이동 URL',
  `link_target`     varchar(10)  NOT NULL DEFAULT '_self' COMMENT '링크 타겟 (_self|_blank)',
  `show_from`       datetime     NOT NULL COMMENT '노출 시작 일시',
  `show_to`         datetime     NOT NULL COMMENT '노출 종료 일시',
  `sort_order`      int(11)      NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn`          char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`banner_id`),
  KEY `idx_banner_show` (`site_id`,`banner_location`,`use_yn`,`show_from`,`show_to`),
  KEY `idx_banner_file_group` (`file_group_id`),
  CONSTRAINT `fk_banner_site` FOREIGN KEY (`site_id`) REFERENCES `tb_site` (`site_id`),
  CONSTRAINT `fk_banner_file_group` FOREIGN KEY (`file_group_id`) REFERENCES `tb_file_group` (`file_group_id`),
  CONSTRAINT `chk_banner_link_target` CHECK (`link_target` in ('_self','_blank')),
  CONSTRAINT `chk_banner_use_yn` CHECK (`use_yn` in ('Y','N')),
  CONSTRAINT `chk_banner_delete_yn` CHECK (`delete_yn` in ('Y','N')),
  CONSTRAINT `chk_banner_period` CHECK (`show_to` >= `show_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='배너';

CREATE TABLE `tb_popup` (
  `popup_id`        varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '팝업 ID (POP_ + UUIDv7)',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '사이트 ID',
  `site_code`       varchar(30)  DEFAULT NULL COMMENT 'site_code 캐시',
  `popup_title`     varchar(200) NOT NULL COMMENT '팝업 제목(관리용)',
  `popup_content`   mediumtext   DEFAULT NULL COMMENT '팝업 내용 HTML (저장 전 sanitize)',
  `popup_type`      varchar(20)  NOT NULL DEFAULT 'LAYER' COMMENT '유형 (LAYER|WINDOW|MODAL|BANNER)',
  `link_url`        varchar(1000) DEFAULT NULL COMMENT '클릭 시 이동 URL',
  `file_group_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '이미지 파일그룹',
  `width_px`        int(11)      DEFAULT NULL COMMENT '너비(px)',
  `height_px`       int(11)      DEFAULT NULL COMMENT '높이(px)',
  `position_x`      int(11)      DEFAULT NULL COMMENT 'X 좌표(px)',
  `position_y`      int(11)      DEFAULT NULL COMMENT 'Y 좌표(px)',
  `show_from`       datetime     NOT NULL COMMENT '노출 시작 일시',
  `show_to`         datetime     NOT NULL COMMENT '노출 종료 일시',
  `show_days`       varchar(30)  DEFAULT NULL COMMENT '노출 요일 CSV (MON,TUE …) — NULL=매일',
  `show_time_from`  time         DEFAULT NULL COMMENT '노출 시작 시각 — NULL=하루 종일',
  `show_time_to`    time         DEFAULT NULL COMMENT '노출 종료 시각',
  `cookie_days`     int(11)      NOT NULL DEFAULT 1 COMMENT '"오늘 그만보기" 쿠키 유효일',
  `sort_order`      int(11)      NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn`          char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`popup_id`),
  KEY `idx_popup_show` (`site_id`,`use_yn`,`show_from`,`show_to`),
  KEY `idx_popup_file_group` (`file_group_id`),
  CONSTRAINT `fk_popup_site` FOREIGN KEY (`site_id`) REFERENCES `tb_site` (`site_id`),
  CONSTRAINT `fk_popup_file_group` FOREIGN KEY (`file_group_id`) REFERENCES `tb_file_group` (`file_group_id`),
  CONSTRAINT `chk_popup_type` CHECK (`popup_type` in ('LAYER','WINDOW','MODAL','BANNER')),
  CONSTRAINT `chk_popup_use_yn` CHECK (`use_yn` in ('Y','N')),
  CONSTRAINT `chk_popup_delete_yn` CHECK (`delete_yn` in ('Y','N')),
  CONSTRAINT `chk_popup_period` CHECK (`show_to` >= `show_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='팝업';

-- ══ 4. 게시판 ═══════════════════════════════════════════════════════════════

CREATE TABLE `tb_bbs_master` (
  `bbs_master_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '게시판 ID (BBM_ + UUIDv7)',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '사이트 ID',
  `site_code`       varchar(30)  DEFAULT NULL COMMENT 'site_code 캐시',
  `menu_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '연결 메뉴 ID (다형 참조 — FK 없음)',
  `bbs_code`        varchar(50)  NOT NULL COMMENT '사이트 내 식별 코드 — URL 에 노출',
  `bbs_name`        varchar(100) NOT NULL COMMENT '게시판 이름',
  `bbs_type`        varchar(20)  NOT NULL COMMENT '게시판 유형 (NOTICE|BODO|FREE|FAQ|QNA|GALLERY|FILE|YOUTUBE)',
  `comment_yn`      char(1)      NOT NULL DEFAULT 'Y' COMMENT '댓글 사용',
  `file_yn`         char(1)      NOT NULL DEFAULT 'Y' COMMENT '첨부 사용',
  `file_count_max`  int(11)      NOT NULL DEFAULT 5 COMMENT '첨부 최대 개수',
  `file_size_max`   bigint(20)   NOT NULL DEFAULT 10485760 COMMENT '첨부 최대 크기(바이트, 기본 10MB)',
  `anonymous_yn`    char(1)      NOT NULL DEFAULT 'N' COMMENT '익명 게시판 여부',
  `notice_top_yn`   char(1)      NOT NULL DEFAULT 'Y' COMMENT '공지 상단 고정 사용',
  `html_yn`         char(1)      NOT NULL DEFAULT 'N' COMMENT '본문 HTML 허용 (Y=sanitize 후 utext / N=평문)',
  `captcha_yn`      char(1)      NOT NULL DEFAULT 'N' COMMENT '작성 시 CAPTCHA 요구',
  `read_auth`       varchar(20)  NOT NULL DEFAULT 'ALL' COMMENT '읽기 권한 (ALL|MEMBER|EMPLOYEE|ADMIN)',
  `write_auth`      varchar(20)  NOT NULL DEFAULT 'MEMBER' COMMENT '쓰기 권한 (GUEST|MEMBER|EMPLOYEE|ADMIN)',
  `download_auth`   varchar(20)  NOT NULL DEFAULT 'ROLE_MEMBER' COMMENT '첨부 다운로드 최소 권한',
  `grouped_board_ids` varchar(1000) DEFAULT NULL COMMENT '통합 게시판 모드 — 묶을 게시판 ID CSV (NULL=일반)',
  `description`     varchar(500) DEFAULT NULL COMMENT '관리자 메모',
  `use_yn`          char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`bbs_master_id`),
  UNIQUE KEY `uk_bbs_code` (`site_id`,`bbs_code`),
  KEY `idx_bbs_master_site_use` (`site_id`,`use_yn`,`delete_yn`),
  KEY `idx_bbs_master_menu` (`menu_id`),
  CONSTRAINT `fk_bbs_master_site` FOREIGN KEY (`site_id`) REFERENCES `tb_site` (`site_id`),
  CONSTRAINT `chk_bbs_master_type` CHECK (`bbs_type` in ('NOTICE','BODO','FREE','FAQ','QNA','GALLERY','FILE','YOUTUBE')),
  CONSTRAINT `chk_bbs_master_read_auth` CHECK (`read_auth` in ('ALL','MEMBER','EMPLOYEE','ADMIN')),
  CONSTRAINT `chk_bbs_master_write_auth` CHECK (`write_auth` in ('GUEST','MEMBER','EMPLOYEE','ADMIN')),
  CONSTRAINT `chk_bbs_master_download_auth` CHECK (`download_auth` in ('ANONYMOUS','ROLE_MEMBER','ROLE_EMPLOYEE','ROLE_STAFF','OWNER_PRIVACY','ROLE_MANAGER','ROLE_ADMIN')),
  CONSTRAINT `chk_bbs_master_yn` CHECK (`comment_yn` in ('Y','N') AND `file_yn` in ('Y','N')
      AND `anonymous_yn` in ('Y','N') AND `notice_top_yn` in ('Y','N')
      AND `html_yn` in ('Y','N') AND `captcha_yn` in ('Y','N')
      AND `use_yn` in ('Y','N') AND `delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='게시판 마스터 — 게시판별 정책';

CREATE TABLE `tb_bbs_category` (
  `category_id`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '카테고리 ID (BCT_ + UUIDv7)',
  `bbs_master_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '게시판 ID',
  `category_code`   varchar(50)  NOT NULL COMMENT '카테고리 코드 (게시판 내 유일)',
  `category_name`   varchar(100) NOT NULL COMMENT '카테고리 명',
  `sort_order`      int(11)      NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn`          char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_bbs_category` (`bbs_master_id`,`category_code`),
  CONSTRAINT `fk_bbs_category_master` FOREIGN KEY (`bbs_master_id`) REFERENCES `tb_bbs_master` (`bbs_master_id`),
  CONSTRAINT `chk_bbs_category_use_yn` CHECK (`use_yn` in ('Y','N')),
  CONSTRAINT `chk_bbs_category_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='게시판 카테고리';

CREATE TABLE `tb_bbs_article` (
  `article_id`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '게시글 ID (BBA_ + UUIDv7)',
  `bbs_master_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '게시판 ID',
  `category_id`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '카테고리 ID',
  `file_group_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '첨부 파일그룹 (첨부 없으면 NULL — 원안의 NOT NULL 완화)',
  `writer_user_id`  varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '작성자 ID (비로그인 NULL)',
  `writer_user_type` varchar(20) DEFAULT NULL COMMENT '작성자 유형',
  `writer_name`     varchar(100) NOT NULL COMMENT '작성자 표시명',
  `writer_password` varchar(100) DEFAULT NULL COMMENT '비로그인 글 수정·삭제용 비밀번호 (BCrypt)',
  `title`           varchar(300) NOT NULL COMMENT '제목',
  `content`         mediumtext   NOT NULL COMMENT '본문 (html_yn=N 이면 평문)',
  `press_name`      varchar(100) DEFAULT NULL COMMENT '언론사명 (보도자료 게시판)',
  `link_url`        varchar(500) DEFAULT NULL COMMENT '원문 링크 (보도자료·유튜브)',
  `published_at`    date         DEFAULT NULL COMMENT '게재 일자 (보도자료 등 표시용)',
  `notice_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '공지 상단 고정 여부',
  `secret_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '비밀글 — 작성자·관리자만 열람',
  `view_count`      bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '조회수',
  `like_count`      bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '좋아요 수 (tb_bbs_like 집계 캐시)',
  `report_count`    int(11)      NOT NULL DEFAULT 0 COMMENT '신고 수 (tb_bbs_report 집계 캐시)',
  `comment_count`   int(11)      NOT NULL DEFAULT 0 COMMENT '댓글 수 (집계 캐시)',
  `client_ip`       varchar(50)  DEFAULT NULL COMMENT '작성 IP',
  `status`          varchar(20)  NOT NULL DEFAULT 'PUBLISHED' COMMENT '상태 (PUBLISHED|HIDDEN|REPORTED|DELETED)',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`article_id`),
  KEY `idx_article_bbs_status` (`bbs_master_id`,`status`,`notice_yn`,`created_at`),
  KEY `idx_article_writer` (`writer_user_id`),
  KEY `idx_article_file_group` (`file_group_id`),
  KEY `idx_article_category` (`category_id`),
  -- MariaDB 는 ngram 파서를 제공하지 않는다(MySQL 전용, 실측 확인) — 기본 파서로 둔다.
  -- 한국어 형태소 검색은 pom 의 lucene-analysis-nori 로 별도 색인할 계획이라
  -- 이 인덱스는 영문·숫자 위주의 보조 수단이다.
  FULLTEXT KEY `ft_article` (`title`,`content`),
  CONSTRAINT `fk_article_bbs` FOREIGN KEY (`bbs_master_id`) REFERENCES `tb_bbs_master` (`bbs_master_id`),
  CONSTRAINT `fk_article_category` FOREIGN KEY (`category_id`) REFERENCES `tb_bbs_category` (`category_id`),
  CONSTRAINT `fk_article_file_group` FOREIGN KEY (`file_group_id`) REFERENCES `tb_file_group` (`file_group_id`),
  CONSTRAINT `chk_article_writer_type` CHECK (`writer_user_type` is null
      or `writer_user_type` in ('MEMBER','EMPLOYEE','ADMIN','STAFF','MANAGER','GUEST')),
  CONSTRAINT `chk_article_status` CHECK (`status` in ('PUBLISHED','HIDDEN','REPORTED','DELETED')),
  CONSTRAINT `chk_article_yn` CHECK (`notice_yn` in ('Y','N') AND `secret_yn` in ('Y','N')
      AND `delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='게시글';

CREATE TABLE `tb_bbs_comment` (
  `comment_id`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '댓글 ID (BBC_ + UUIDv7)',
  `article_id`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '게시글 ID',
  `parent_comment_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '상위 댓글 ID (대댓글)',
  `writer_user_id`  varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '작성자 ID',
  `writer_user_type` varchar(20) DEFAULT NULL COMMENT '작성자 유형',
  `writer_name`     varchar(100) NOT NULL COMMENT '작성자 표시명',
  `writer_password` varchar(100) DEFAULT NULL COMMENT '비로그인 댓글 비밀번호 (BCrypt)',
  `content`         text         NOT NULL COMMENT '댓글 본문 (평문)',
  `like_count`      bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '좋아요 수',
  `report_count`    int(11)      NOT NULL DEFAULT 0 COMMENT '신고 수',
  `secret_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '비밀 댓글 — 작성자·글쓴이·관리자만 열람',
  `depth`           int(11)      NOT NULL DEFAULT 1 COMMENT '들여쓰기 깊이',
  `client_ip`       varchar(50)  DEFAULT NULL COMMENT '작성 IP',
  `status`          varchar(20)  NOT NULL DEFAULT 'PUBLISHED' COMMENT '상태 (PUBLISHED|HIDDEN|REPORTED|DELETED)',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`comment_id`),
  KEY `idx_comment_article` (`article_id`,`parent_comment_id`,`created_at`),
  KEY `idx_comment_parent` (`parent_comment_id`),
  CONSTRAINT `fk_comment_article` FOREIGN KEY (`article_id`) REFERENCES `tb_bbs_article` (`article_id`),
  CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_comment_id`) REFERENCES `tb_bbs_comment` (`comment_id`),
  CONSTRAINT `chk_comment_writer_type` CHECK (`writer_user_type` is null
      or `writer_user_type` in ('MEMBER','EMPLOYEE','ADMIN','STAFF','MANAGER','GUEST')),
  CONSTRAINT `chk_comment_status` CHECK (`status` in ('PUBLISHED','HIDDEN','REPORTED','DELETED')),
  CONSTRAINT `chk_comment_yn` CHECK (`secret_yn` in ('Y','N') AND `delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='게시글 댓글';

-- 좋아요·신고는 게시글/댓글/컨텐츠를 함께 가리키는 다형 참조라 FK 를 두지 않는다.
CREATE TABLE `tb_bbs_like` (
  `like_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '좋아요 ID (LIK_ + UUIDv7)',
  `target_type`     varchar(20)  NOT NULL COMMENT '대상 유형 (ARTICLE|COMMENT|CONTENT)',
  `target_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '대상 ID (다형 참조)',
  `user_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '누른 사용자 ID (익명 좋아요 미지원 — 중복 방지 불가)',
  `user_type`       varchar(20)  NOT NULL COMMENT '사용자 유형',
  `source_url`      varchar(1000) DEFAULT NULL COMMENT '클릭이 일어난 페이지 경로 (분석용)',
  `menu_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '메뉴 ID (통계 분해용)',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부 (좋아요 취소)',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`like_id`),
  UNIQUE KEY `uk_like_target_user` (`target_type`,`target_id`,`user_id`),
  KEY `idx_like_target` (`target_type`,`target_id`,`delete_yn`),
  KEY `idx_like_user` (`user_id`,`target_type`),
  KEY `idx_like_menu` (`menu_id`),
  CONSTRAINT `chk_like_target_type` CHECK (`target_type` in ('ARTICLE','COMMENT','CONTENT')),
  CONSTRAINT `chk_like_user_type` CHECK (`user_type` in ('MEMBER','EMPLOYEE','ADMIN','STAFF')),
  CONSTRAINT `chk_like_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='좋아요 — 게시글·댓글·컨텐츠 통합';

CREATE TABLE `tb_bbs_report` (
  `report_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '신고 ID (RPT_ + UUIDv7)',
  `target_type`     varchar(20)  NOT NULL COMMENT '대상 유형 (ARTICLE|COMMENT|CONTENT)',
  `target_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '대상 ID (다형 참조)',
  `reporter_user_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '신고자 ID',
  `reporter_user_type` varchar(20) NOT NULL COMMENT '신고자 유형',
  `source_url`      varchar(1000) DEFAULT NULL COMMENT '신고가 접수된 페이지 경로',
  `menu_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '메뉴 ID',
  `reason_code`     varchar(30)  NOT NULL COMMENT '신고 사유 코드',
  `reason_text`     varchar(1000) DEFAULT NULL COMMENT '신고 상세 사유',
  `status`          varchar(20)  NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태 (PENDING|REVIEWED|REJECTED)',
  `reviewed_by`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '처리자 ID',
  `reviewed_at`     timestamp    NULL DEFAULT NULL COMMENT '처리 일시',
  `review_note`     varchar(1000) DEFAULT NULL COMMENT '처리 메모',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`report_id`),
  UNIQUE KEY `uk_report_target_reporter` (`target_type`,`target_id`,`reporter_user_id`),
  KEY `idx_report_target` (`target_type`,`target_id`),
  KEY `idx_report_status` (`status`,`created_at`),
  KEY `idx_report_reporter` (`reporter_user_id`),
  KEY `idx_report_menu` (`menu_id`),
  CONSTRAINT `chk_report_target_type` CHECK (`target_type` in ('ARTICLE','COMMENT','CONTENT')),
  CONSTRAINT `chk_report_reporter_type` CHECK (`reporter_user_type` in ('MEMBER','EMPLOYEE','ADMIN','STAFF')),
  CONSTRAINT `chk_report_reason` CHECK (`reason_code` in ('SPAM','OFFENSIVE','ILLEGAL','COPYRIGHT','PRIVACY','OTHER')),
  CONSTRAINT `chk_report_status` CHECK (`status` in ('PENDING','REVIEWED','REJECTED')),
  CONSTRAINT `chk_report_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='신고 — 게시글·댓글·컨텐츠 통합';

-- ══ 5. 일정 ═════════════════════════════════════════════════════════════════
-- master 가 site/menu 를 들고, 개별 일정은 시간만 다룬다(얇은 owner 패턴).

CREATE TABLE `tb_schedule_master` (
  `schedule_master_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '일정 마스터 ID (SCM_ + UUIDv7)',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '사이트 ID',
  `site_code`       varchar(30)  DEFAULT NULL COMMENT 'site_code 캐시',
  `menu_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '연결 메뉴 ID',
  `master_title`    varchar(200) NOT NULL COMMENT '일정 그룹 제목 (예: 2026 학사일정)',
  `master_content`  mediumtext   DEFAULT NULL COMMENT '그룹 안내문 HTML (sanitize)',
  `use_yn`          char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`schedule_master_id`),
  KEY `idx_schedule_master_site` (`site_id`,`use_yn`,`delete_yn`),
  KEY `idx_schedule_master_menu` (`menu_id`),
  CONSTRAINT `fk_schedule_master_site` FOREIGN KEY (`site_id`) REFERENCES `tb_site` (`site_id`),
  CONSTRAINT `chk_schedule_master_use_yn` CHECK (`use_yn` in ('Y','N')),
  CONSTRAINT `chk_schedule_master_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='일정 마스터 — 사이트·메뉴 소유 + 그룹 헤더';

CREATE TABLE `tb_schedule` (
  `schedule_id`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '일정 ID (SCH_ + UUIDv7)',
  `schedule_master_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '일정 마스터 ID',
  `schedule_title`  varchar(200) NOT NULL COMMENT '일정 제목',
  `schedule_content` mediumtext  DEFAULT NULL COMMENT '일정 내용 HTML (sanitize)',
  `schedule_category` varchar(30) DEFAULT NULL COMMENT '분류 (EVENT/MEETING/HOLIDAY/NOTICE 권장)',
  `start_at`        datetime     NOT NULL COMMENT '시작 일시',
  `end_at`          datetime     NOT NULL COMMENT '종료 일시',
  `location`        varchar(200) DEFAULT NULL COMMENT '장소',
  `link_url`        varchar(1000) DEFAULT NULL COMMENT '관련 링크',
  `all_day_yn`      char(1)      NOT NULL DEFAULT 'N' COMMENT '종일 일정 여부',
  `color_code`      varchar(10)  DEFAULT NULL COMMENT '캘린더 색상 (#RRGGBB)',
  `use_yn`          char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`schedule_id`),
  KEY `idx_schedule_master` (`schedule_master_id`,`use_yn`,`delete_yn`),
  KEY `idx_schedule_master_range` (`schedule_master_id`,`start_at`,`end_at`),
  KEY `idx_schedule_range` (`start_at`,`end_at`,`use_yn`,`delete_yn`),
  KEY `idx_schedule_category` (`schedule_category`,`use_yn`,`delete_yn`),
  CONSTRAINT `fk_schedule_master` FOREIGN KEY (`schedule_master_id`) REFERENCES `tb_schedule_master` (`schedule_master_id`),
  CONSTRAINT `chk_schedule_all_day_yn` CHECK (`all_day_yn` in ('Y','N')),
  CONSTRAINT `chk_schedule_use_yn` CHECK (`use_yn` in ('Y','N')),
  CONSTRAINT `chk_schedule_delete_yn` CHECK (`delete_yn` in ('Y','N')),
  CONSTRAINT `chk_schedule_period` CHECK (`end_at` >= `start_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='일정 — 개별 일정 + 기간';

-- ══ 6. 설문 ═════════════════════════════════════════════════════════════════

CREATE TABLE `tb_survey_master` (
  `survey_master_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '설문 마스터 ID (SVM_ + UUIDv7)',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '사이트 ID',
  `site_code`       varchar(30)  DEFAULT NULL COMMENT 'site_code 캐시',
  `menu_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '연결 메뉴 ID',
  `master_title`    varchar(200) NOT NULL COMMENT '설문 그룹 제목',
  `master_content`  mediumtext   DEFAULT NULL COMMENT '그룹 안내문 HTML (sanitize)',
  `use_yn`          char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`survey_master_id`),
  KEY `idx_survey_master_site` (`site_id`,`use_yn`,`delete_yn`),
  KEY `idx_survey_master_menu` (`menu_id`),
  CONSTRAINT `fk_survey_master_site` FOREIGN KEY (`site_id`) REFERENCES `tb_site` (`site_id`),
  CONSTRAINT `chk_survey_master_use_yn` CHECK (`use_yn` in ('Y','N')),
  CONSTRAINT `chk_survey_master_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='설문 마스터 — 사이트·메뉴 소유 + 그룹 헤더';

CREATE TABLE `tb_survey` (
  `survey_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '설문 ID (SVY_ + UUIDv7)',
  `survey_master_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '설문 마스터 ID',
  `survey_title`    varchar(200) NOT NULL COMMENT '설문 제목',
  `survey_description` mediumtext DEFAULT NULL COMMENT '설문 설명 HTML (sanitize)',
  `start_at`        datetime     NOT NULL COMMENT '응답 시작 일시',
  `end_at`          datetime     NOT NULL COMMENT '응답 종료 일시',
  `status`          varchar(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '상태 (DRAFT|PUBLISHED|CLOSED)',
  `anonymous_yn`    char(1)      NOT NULL DEFAULT 'N' COMMENT '익명 응답 — Y 면 응답자 ID 를 남기지 않는다',
  `one_response_yn` char(1)      NOT NULL DEFAULT 'Y' COMMENT '1인 1회 응답 제한',
  `use_yn`          char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`survey_id`),
  KEY `idx_survey_master` (`survey_master_id`,`use_yn`,`delete_yn`),
  KEY `idx_survey_master_status` (`survey_master_id`,`status`,`start_at`,`end_at`),
  CONSTRAINT `fk_survey_master` FOREIGN KEY (`survey_master_id`) REFERENCES `tb_survey_master` (`survey_master_id`),
  CONSTRAINT `chk_survey_status` CHECK (`status` in ('DRAFT','PUBLISHED','CLOSED')),
  CONSTRAINT `chk_survey_yn` CHECK (`anonymous_yn` in ('Y','N') AND `one_response_yn` in ('Y','N')
      AND `use_yn` in ('Y','N') AND `delete_yn` in ('Y','N')),
  CONSTRAINT `chk_survey_period` CHECK (`end_at` >= `start_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='설문';

CREATE TABLE `tb_survey_question` (
  `question_id`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '문항 ID (SVQ_ + UUIDv7)',
  `survey_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '설문 ID',
  `question_text`   varchar(1000) NOT NULL COMMENT '문항 내용',
  `question_type`   varchar(20)  NOT NULL DEFAULT 'TEXT' COMMENT '문항 유형 (TEXT|TEXTAREA|RADIO|CHECKBOX|SELECT|SCALE)',
  `required_yn`     char(1)      NOT NULL DEFAULT 'Y' COMMENT '필수 응답 여부',
  `scale_min`       int(11)      DEFAULT NULL COMMENT 'SCALE 최소값',
  `scale_max`       int(11)      DEFAULT NULL COMMENT 'SCALE 최대값',
  `sort_order`      int(11)      NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`question_id`),
  KEY `idx_question_survey` (`survey_id`,`sort_order`,`delete_yn`),
  CONSTRAINT `fk_question_survey` FOREIGN KEY (`survey_id`) REFERENCES `tb_survey` (`survey_id`),
  CONSTRAINT `chk_question_type` CHECK (`question_type` in ('TEXT','TEXTAREA','RADIO','CHECKBOX','SELECT','SCALE')),
  CONSTRAINT `chk_question_required_yn` CHECK (`required_yn` in ('Y','N')),
  CONSTRAINT `chk_question_delete_yn` CHECK (`delete_yn` in ('Y','N')),
  CONSTRAINT `chk_question_scale` CHECK (`scale_min` is null or `scale_max` is null or `scale_max` > `scale_min`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='설문 문항';

CREATE TABLE `tb_survey_option` (
  `option_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '선택지 ID (SVO_ + UUIDv7)',
  `question_id`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '문항 ID',
  `option_text`     varchar(500) NOT NULL COMMENT '선택지 표시 문구',
  `option_value`    varchar(100) DEFAULT NULL COMMENT '선택지 값(코드) — 집계 키',
  `sort_order`      int(11)      NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`option_id`),
  KEY `idx_option_question` (`question_id`,`sort_order`,`delete_yn`),
  CONSTRAINT `fk_option_question` FOREIGN KEY (`question_id`) REFERENCES `tb_survey_question` (`question_id`),
  CONSTRAINT `chk_option_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='설문 선택지';

-- 응답 헤더 — 익명 설문은 member_id 가 NULL 이다. UNIQUE 는 NULL 을 중복으로 보지 않으므로
-- 익명 응답은 여러 건 들어오고, 기명 설문만 1인 1회가 강제된다(의도된 동작).
CREATE TABLE `tb_survey_response` (
  `response_id`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '응답 ID (SVR_ + UUIDv7)',
  `survey_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '설문 ID',
  `member_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '응답 회원 ID (익명 설문은 NULL)',
  `client_ip`       varchar(50)  DEFAULT NULL COMMENT '응답자 IP',
  `submitted_at`    timestamp    NULL DEFAULT current_timestamp() COMMENT '제출 일시',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`response_id`),
  UNIQUE KEY `uk_response_survey_member` (`survey_id`,`member_id`),
  KEY `idx_response_survey` (`survey_id`,`submitted_at`),
  CONSTRAINT `fk_response_survey` FOREIGN KEY (`survey_id`) REFERENCES `tb_survey` (`survey_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='설문 응답 헤더';

CREATE TABLE `tb_survey_answer` (
  `answer_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '응답 상세 ID (SVA_ + UUIDv7)',
  `response_id`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '응답 헤더 ID',
  `question_id`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '문항 ID',
  `option_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '선택지 ID (객관식) — 복수 선택은 행을 나눠 담는다',
  `answer_text`     text         DEFAULT NULL COMMENT '주관식 응답',
  `answer_number`   int(11)      DEFAULT NULL COMMENT 'SCALE 응답 값',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`answer_id`),
  KEY `idx_answer_response` (`response_id`),
  KEY `idx_answer_question_option` (`question_id`,`option_id`),
  CONSTRAINT `fk_answer_response` FOREIGN KEY (`response_id`) REFERENCES `tb_survey_response` (`response_id`),
  CONSTRAINT `fk_answer_question` FOREIGN KEY (`question_id`) REFERENCES `tb_survey_question` (`question_id`),
  CONSTRAINT `fk_answer_option` FOREIGN KEY (`option_id`) REFERENCES `tb_survey_option` (`option_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='설문 응답 상세';

-- ══ 7. 공통 운영 ════════════════════════════════════════════════════════════

CREATE TABLE `tb_holiday` (
  `holiday_id`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '공휴일 ID (HOL_ + UUIDv7)',
  `holiday_date`    date         NOT NULL COMMENT '날짜',
  `holiday_year`    smallint(6)  NOT NULL COMMENT '연도 — 원안 `year` 는 타입명과 겹쳐 개명(조회 분해 키)',
  `holiday_name`    varchar(100) NOT NULL COMMENT '명칭',
  `holiday_type`    varchar(20)  NOT NULL DEFAULT 'PUBLIC' COMMENT '유형 (PUBLIC|COMPANY|MEMORIAL|OTHER)',
  `description`     varchar(500) DEFAULT NULL COMMENT '설명',
  `use_yn`          char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`holiday_id`),
  UNIQUE KEY `uk_holiday_date_name` (`holiday_date`,`holiday_name`),
  KEY `idx_holiday_year` (`holiday_year`,`holiday_date`,`use_yn`,`delete_yn`),
  KEY `idx_holiday_date` (`holiday_date`,`use_yn`,`delete_yn`),
  CONSTRAINT `chk_holiday_type` CHECK (`holiday_type` in ('PUBLIC','COMPANY','MEMORIAL','OTHER')),
  CONSTRAINT `chk_holiday_use_yn` CHECK (`use_yn` in ('Y','N')),
  CONSTRAINT `chk_holiday_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='공휴일 — 영업일 계산·캘린더 표시';

CREATE TABLE `tb_mail_template` (
  `mail_template_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '메일템플릿 ID (MTP_ + UUIDv7)',
  `template_code`   varchar(50)  NOT NULL COMMENT '템플릿 코드 (예: MEMBER_WELCOME) — 코드로 조회한다',
  `template_name`   varchar(100) NOT NULL COMMENT '템플릿 이름(관리용)',
  `subject`         varchar(500) NOT NULL COMMENT '메일 제목 (변수 치환 대상)',
  `body_html`       mediumtext   DEFAULT NULL COMMENT '본문 HTML (Thymeleaf 문법)',
  `sender_email`    varchar(255) DEFAULT NULL COMMENT '발신 주소 (NULL=시스템 기본)',
  `sender_name`     varchar(100) DEFAULT NULL COMMENT '발신자 표시명 (NULL=시스템 기본)',
  `description`     varchar(1000) DEFAULT NULL COMMENT '발송 시점·용도 설명',
  `variables_hint`  varchar(2000) DEFAULT NULL COMMENT '사용 가능한 모델 변수 목록 (편집자 안내)',
  `use_yn`          char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
  `delete_yn`       char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`mail_template_id`),
  UNIQUE KEY `uk_mail_template_code` (`template_code`),
  KEY `idx_mail_template_use` (`use_yn`,`delete_yn`),
  CONSTRAINT `chk_mail_template_use_yn` CHECK (`use_yn` in ('Y','N')),
  CONSTRAINT `chk_mail_template_delete_yn` CHECK (`delete_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='메일 템플릿 (Thymeleaf HTML)';
