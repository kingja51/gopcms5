-- ============================================================================
-- V6 — 인증·인가 테이블 (관리자·회원·역할 계층·URL 접근제어) + vw_user_login
-- ----------------------------------------------------------------------------
-- 원안(D:\test\auth테이블.sql) 검토 교정판 (2026-07-28):
--   · 실행 불가 5건 수정 — department_id 중복 정의(admin/admin_withdraw),
--     vw_user_login 의 미존재 컬럼(department_name → tb_department 조인),
--     tb_auth 정의 신설, FK 대상 우선 생성 순서 재배열
--   · 규약 적용 — ID 컬럼 ascii_bin · 소문자 컬럼명 · DEFAULT uuid_v7() 제거(앱 Uid 채번)
--     · 접두어 신규 등록(DPT/AGR/AUT/RLA/RLH/RUA/CGR/ARL/AIP/APH/MBC/MDN/MBO/MPH)
--   · 역할 계층: ROLE_ADMIN > ROLE_MANAGER > ROLE_STAFF > ROLE_MEMBER > ROLE_REAL
--     — adjacency(parent_role_id) + closure(tb_role_hierarchy, depth=0 self 포함).
--     계층 변경 시 closure 재전개 + 사용자 role_ids/role_codes CSV 재계산은 앱 책임.
--   · 로그인 계약: 관리자 /adm/login · 사용자 /login?siteCode= — vw_user_login 단일 뷰 조회
--     (user_type + site 스코프 필수 — 동일 login_id 가 양측에 존재 가능)
--   · 보류(후속 결정): 회원 ROLE_REAL 부여 방식(현재 'ROLE_MEMBER' 고정) ·
--     EMPLOYEE(직원) 로그인 분기(뷰 확장 지점 주석) · tb_popup 은 배너·팝업 페이즈로 분리
-- ============================================================================

-- ① 부서 (조직 트리) — 레지스트리 DPT 는 tb_department 로 확정(구 tb_dept 명칭 대체)
CREATE TABLE `tb_department` (
  `department_id`        varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '부서 ID (DPT_ + UUIDv7)',
  `parent_department_id` varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '상위 부서 ID (NULL=root)',
  `department_code`      varchar(30)  NOT NULL COMMENT '부서 코드',
  `department_name`      varchar(100) NOT NULL COMMENT '부서명',
  `depth`                int(11)      NOT NULL DEFAULT 1 COMMENT '트리 깊이',
  `sort_order`           int(11)      NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `manager_employee_id`  varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '부서장 직원 ID (직원 도메인 페이즈에서 연결)',
  `description`          varchar(500) DEFAULT NULL COMMENT '설명',
  `use_yn`               char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`            char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`           varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`           varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`           timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`           varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`           varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`           timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`department_id`),
  UNIQUE KEY `uk_department_code` (`department_code`),
  KEY `idx_department_parent` (`parent_department_id`,`sort_order`),
  CONSTRAINT `fk_department_parent` FOREIGN KEY (`parent_department_id`) REFERENCES `tb_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='부서 (조직 트리)';

-- ② 관리자 그룹 (접속 정책 묶음 — IP/시간대/2FA 강제)
CREATE TABLE `tb_admin_group` (
  `admin_group_id`      varchar(40)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '관리자그룹 ID (AGR_ + UUIDv7)',
  `group_code`          varchar(30)   NOT NULL COMMENT '그룹 코드',
  `group_name`          varchar(100)  NOT NULL COMMENT '그룹 명',
  `description`         varchar(500)  DEFAULT NULL COMMENT '설명',
  `default_role_id`     varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '신규 관리자 기본 역할 ID',
  `ip_whitelist`        varchar(2000) DEFAULT NULL COMMENT '허용 IP CIDR CSV (그룹 공통)',
  `allowed_time_from`   time          DEFAULT NULL COMMENT '허용 시작 시각',
  `allowed_time_to`     time          DEFAULT NULL COMMENT '허용 종료 시각',
  `two_factor_required` char(1)       NOT NULL DEFAULT 'Y' COMMENT '2FA 강제 여부' CHECK (`two_factor_required` in ('Y','N')),
  `use_yn`              char(1)       NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`           char(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`          varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`          varchar(50)   DEFAULT NULL COMMENT '생성자 IP',
  `created_at`          timestamp     NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`          varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`          varchar(50)   DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`          timestamp     NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`admin_group_id`),
  UNIQUE KEY `uk_admin_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='관리자 그룹 (접속 정책)';

-- ③ 역할 (계층형 — adjacency. 전수 전개는 tb_role_hierarchy)
CREATE TABLE `tb_role` (
  `role_id`        varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '역할 ID (ROL_ + UUIDv7)',
  `site_id`        varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID (NULL=전역 역할)',
  `site_code`      varchar(30)  DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `parent_role_id` varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '직계 상위 역할 ID',
  `role_code`      varchar(50)  NOT NULL COMMENT '역할 코드 (ROLE_ADMIN > ROLE_MANAGER > ROLE_STAFF > ROLE_MEMBER > ROLE_REAL)',
  `role_name`      varchar(100) NOT NULL COMMENT '역할 명',
  `description`    varchar(500) DEFAULT NULL COMMENT '설명',
  `sort_order`     int(11)      NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn`         char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`      char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`     varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`     timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`     varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`     timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_role_code` (`site_id`,`role_code`),
  KEY `idx_role_parent` (`parent_role_id`),
  CONSTRAINT `fk_role_parent` FOREIGN KEY (`parent_role_id`) REFERENCES `tb_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='역할 (계층형)';

-- ④ 권한 (세부 기능 권한 — 원안 누락분 신설. 역할과 N:M = tb_role_auth)
CREATE TABLE `tb_auth` (
  `auth_id`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '권한 ID (AUT_ + UUIDv7)',
  `auth_code`   varchar(50)  NOT NULL COMMENT '권한 코드 (예: CONTENT_WRITE, BBS_MANAGE)',
  `auth_name`   varchar(100) NOT NULL COMMENT '권한 명',
  `description` varchar(500) DEFAULT NULL COMMENT '설명',
  `sort_order`  int(11)      NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn`      char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`   char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`  varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`  varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`  timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`  varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`  varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`  timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`auth_id`),
  UNIQUE KEY `uk_auth_code` (`auth_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='권한 (세부 기능)';

-- ⑤ 역할-권한 매핑
CREATE TABLE `tb_role_auth` (
  `role_auth_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ID (RLA_ + UUIDv7)',
  `role_id`      varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '역할 ID',
  `auth_id`      varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '권한 ID',
  `delete_yn`    char(1)     NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`   varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`   varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at`   timestamp   NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`   varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`   varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`   timestamp   NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`role_auth_id`),
  UNIQUE KEY `uk_role_auth` (`role_id`,`auth_id`),
  KEY `idx_role_auth_a` (`auth_id`),
  CONSTRAINT `fk_role_auth_role` FOREIGN KEY (`role_id`) REFERENCES `tb_role` (`role_id`),
  CONSTRAINT `fk_role_auth_auth` FOREIGN KEY (`auth_id`) REFERENCES `tb_auth` (`auth_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='역할-권한 매핑';

-- ⑥ 역할 계층 Closure Table — depth=0(self) 행 필수 시드, 계층 변경 시 앱이 재전개
CREATE TABLE `tb_role_hierarchy` (
  `role_hierarchy_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ID (RLH_ + UUIDv7)',
  `ancestor_role_id`  varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '조상 역할 ID (상위)',
  `descendant_role_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '후손 역할 ID (하위)',
  `depth`             int(11)     NOT NULL DEFAULT 1 COMMENT '깊이 (0=self, 1=직계, N=간접)' CHECK (`depth` >= 0),
  `delete_yn`         char(1)     NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`        varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`        varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at`        timestamp   NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`        varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`        varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`        timestamp   NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`role_hierarchy_id`),
  UNIQUE KEY `uk_role_hierarchy` (`ancestor_role_id`,`descendant_role_id`),
  KEY `idx_role_hier_descendant` (`descendant_role_id`,`depth`),
  KEY `idx_role_hier_ancestor` (`ancestor_role_id`,`depth`),
  CONSTRAINT `fk_role_hier_anc` FOREIGN KEY (`ancestor_role_id`) REFERENCES `tb_role` (`role_id`),
  CONSTRAINT `fk_role_hier_dec` FOREIGN KEY (`descendant_role_id`) REFERENCES `tb_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='역할 계층 Closure Table (조상-후손 전수)';

-- ⑦ 공통코드 그룹 → 코드 (FK 순서 교정 — 그룹 먼저)
CREATE TABLE `tb_code_group` (
  `code_group_id` varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '코드그룹 ID (CGR_ + UUIDv7)',
  `group_code`    varchar(50)  NOT NULL COMMENT '그룹 코드',
  `group_name`    varchar(100) NOT NULL COMMENT '그룹 명',
  `description`   varchar(500) DEFAULT NULL COMMENT '설명',
  `use_yn`        char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`     char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`    varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`    varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`    timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`    varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`    varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`    timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`code_group_id`),
  UNIQUE KEY `uk_code_group` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='공통코드 그룹';

CREATE TABLE `tb_code` (
  `code_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '코드 ID (COD_ + UUIDv7)',
  `code_group_id` varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '코드그룹 ID',
  `code`          varchar(50)  NOT NULL COMMENT '코드값',
  `code_name`     varchar(100) NOT NULL COMMENT '코드명',
  `code_value`    varchar(500) DEFAULT NULL COMMENT '부가 값',
  `sort_order`    int(11)      NOT NULL DEFAULT 0 COMMENT '정렬 순서',
  `use_yn`        char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`     char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`    varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`    varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`    timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`    varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`    varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`    timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`code_id`),
  UNIQUE KEY `uk_code_group_code` (`code_group_id`,`code`),
  KEY `idx_code_sort` (`code_group_id`,`sort_order`),
  CONSTRAINT `fk_code_group` FOREIGN KEY (`code_group_id`) REFERENCES `tb_code_group` (`code_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='공통코드';

-- ⑧ 관리자 — 원안의 department_id 중복 제거 · 소문자 컬럼 · admin_seq 는 뷰 uniq_id 용
CREATE TABLE `tb_admin` (
  `admin_id`              varchar(40)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '관리자 ID (ADM_ + UUIDv7)',
  `admin_seq`             bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '관리자 일련번호 (vw_user_login.uniq_id)',
  `admin_group_id`        varchar(40)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '관리자그룹 ID (접속 정책)',
  `department_id`         varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '부서 ID',
  `login_id`              varchar(50)   NOT NULL COMMENT '로그인 ID (/adm/login)',
  `password`              varchar(100)  NOT NULL COMMENT '비밀번호 (BCrypt)',
  `password_changed_at`   datetime      NOT NULL COMMENT '비밀번호 변경 일시',
  `password_expire_at`    datetime      DEFAULT NULL COMMENT '비밀번호 만료 일시',
  `role_ids`              text          DEFAULT NULL COMMENT '역할 ID CSV — closure 후손 전개 스냅샷 (계층 변경 시 재계산)',
  `role_codes`            text          DEFAULT NULL COMMENT 'ROLE 코드 CSV — 위와 동기 (Security 권한 부여용)',
  `admin_name`            varchar(512)  NOT NULL COMMENT '{AG} AES-256-GCM 관리자 이름',
  `email`                 varchar(512)  NOT NULL COMMENT '{AG} AES-256-GCM 이메일',
  `email_hash`            char(64)      NOT NULL COMMENT 'HMAC-SHA256(email) 검색/중복확인용',
  `phone`                 varchar(512)  DEFAULT NULL COMMENT '{AG} AES-256-GCM 전화번호',
  `two_factor_enabled_yn` char(1)       NOT NULL DEFAULT 'N' COMMENT '2FA 활성화 여부' CHECK (`two_factor_enabled_yn` in ('Y','N')),
  `two_factor_secret`     varchar(512)  DEFAULT NULL COMMENT '{AG} TOTP Secret',
  `ip_whitelist`          varchar(2000) DEFAULT NULL COMMENT '개인 IP 화이트리스트 (그룹 오버라이드)',
  `allowed_time_from`     time          DEFAULT NULL COMMENT '로그인 허용 시작 시각',
  `allowed_time_to`       time          DEFAULT NULL COMMENT '로그인 허용 종료 시각',
  `status`                varchar(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT '상태' CHECK (`status` in ('ACTIVE','LOCKED','INACTIVE','SUSPENDED')),
  `login_fail_count`      int(11)       NOT NULL DEFAULT 0 COMMENT '로그인 실패 횟수 (5회 잠금)' CHECK (`login_fail_count` >= 0),
  `locked_until`          datetime      DEFAULT NULL COMMENT '잠금 해제 예정 일시 (30분)',
  `captcha_required_yn`   char(1)       NOT NULL DEFAULT 'N' COMMENT '잠금 해제 후 CAPTCHA 강제 — 다음 성공 시 자동 N' CHECK (`captcha_required_yn` in ('Y','N')),
  `last_login_at`         datetime      DEFAULT NULL COMMENT '최종 로그인 일시',
  `last_login_ip`         varchar(50)   DEFAULT NULL COMMENT '최종 로그인 IP',
  `last_access_at`        datetime      DEFAULT NULL COMMENT '최종 접속 일시',
  `remarks`               varchar(1000) DEFAULT NULL COMMENT '비고',
  `delete_yn`             char(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`            varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`            varchar(50)   DEFAULT NULL COMMENT '생성자 IP',
  `created_at`            timestamp     NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`            varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`            varchar(50)   DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`            timestamp     NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`admin_id`),
  UNIQUE KEY `uk_admin_seq` (`admin_seq`),
  UNIQUE KEY `uk_admin_login` (`login_id`),
  KEY `idx_admin_group` (`admin_group_id`),
  KEY `idx_admin_email_hash` (`email_hash`),
  KEY `idx_admin_status` (`status`,`delete_yn`),
  KEY `idx_admin_dept` (`department_id`),
  CONSTRAINT `fk_admin_group` FOREIGN KEY (`admin_group_id`) REFERENCES `tb_admin_group` (`admin_group_id`),
  CONSTRAINT `fk_admin_dept` FOREIGN KEY (`department_id`) REFERENCES `tb_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='관리자';

-- ⑨ 관리자-역할 매핑 (정본 — role_ids CSV 는 이 테이블에서 전개한 스냅샷)
CREATE TABLE `tb_admin_role` (
  `admin_role_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ID (ARL_ + UUIDv7)',
  `admin_id`      varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '관리자 ID',
  `role_id`       varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '역할 ID',
  `delete_yn`     char(1)     NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`    varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`    varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at`    timestamp   NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`    varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`    varchar(50) DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`    timestamp   NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`admin_role_id`),
  UNIQUE KEY `uk_admin_role` (`admin_id`,`role_id`),
  KEY `idx_admin_role_role` (`role_id`),
  CONSTRAINT `fk_admin_role_admin` FOREIGN KEY (`admin_id`) REFERENCES `tb_admin` (`admin_id`),
  CONSTRAINT `fk_admin_role_role` FOREIGN KEY (`role_id`) REFERENCES `tb_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='관리자-역할 매핑';

-- ⑩ 관리자 접속 허용 IP
CREATE TABLE `tb_admin_allow_ip` (
  `ip_id`          varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ID (AIP_ + UUIDv7)',
  `admin_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '관리자 ID',
  `ip_address`     varchar(50)  NOT NULL COMMENT 'IP 주소 (SINGLE) / CIDR (CIDR)',
  `ip_type`        varchar(20)  NOT NULL DEFAULT 'SINGLE' COMMENT '유형' CHECK (`ip_type` in ('SINGLE','RANGE','CIDR')),
  `ip_start`       varchar(50)  DEFAULT NULL COMMENT 'IP 범위 시작 (RANGE)',
  `ip_end`         varchar(50)  DEFAULT NULL COMMENT 'IP 범위 종료 (RANGE)',
  `description`    varchar(200) DEFAULT NULL COMMENT '설명 (예: 본사 사무실)',
  `access_count`   int(11)      NOT NULL DEFAULT 0 COMMENT '접근 횟수 (성공 시 +1)',
  `last_access_at` datetime     DEFAULT NULL COMMENT '마지막 접근 일시',
  `expires_at`     datetime     DEFAULT NULL COMMENT '만료 일시 (NULL=영구)',
  `use_yn`         char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`      char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`     varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`     timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`     varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`     timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`ip_id`),
  KEY `idx_admin_allow_ip_admin` (`admin_id`,`delete_yn`,`use_yn`),
  KEY `idx_admin_allow_ip_expires` (`expires_at`),
  CONSTRAINT `fk_admin_allow_ip_admin` FOREIGN KEY (`admin_id`) REFERENCES `tb_admin` (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='관리자 접속 허용 IP (정의 시 whitelist)';

-- ⑪ 관리자 비밀번호 이력 (재사용 방지 — insert-only)
CREATE TABLE `tb_admin_password_history` (
  `pwd_history_id` varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ID (APH_ + UUIDv7)',
  `admin_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '관리자 ID',
  `password_hash`  varchar(100) NOT NULL COMMENT '비밀번호 해시 (BCrypt)',
  `changed_at`     datetime     NOT NULL COMMENT '변경 일시',
  `created_by`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`     varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`     timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`pwd_history_id`),
  KEY `idx_admin_pwd_hst` (`admin_id`,`changed_at`),
  CONSTRAINT `fk_admin_pwd_hst` FOREIGN KEY (`admin_id`) REFERENCES `tb_admin` (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='관리자 비밀번호 이력 (재사용 방지)';

-- ⑫ 탈퇴 관리자 (감사 보관 — department_id 중복 제거)
CREATE TABLE `tb_admin_withdraw` (
  `admin_id`            varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '원 관리자 ID',
  `admin_seq`           bigint(20)  NOT NULL COMMENT '원 관리자 일련번호',
  `admin_group_id`      varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '관리자그룹 ID (스냅샷)',
  `department_id`       varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '부서 ID (스냅샷)',
  `login_id`            varchar(50) NOT NULL COMMENT '로그인 ID (감사 추적용 평문 유지)',
  `email_hash`          char(64)    NOT NULL COMMENT '이메일 해시',
  `withdraw_at`         datetime    NOT NULL COMMENT '탈퇴 처리 일시',
  `withdraw_reason`     varchar(50) NOT NULL COMMENT '탈퇴 사유' CHECK (`withdraw_reason` in ('RESIGN','TRANSFER','FORCE')),
  `last_login_at`       datetime    DEFAULT NULL COMMENT '최종 로그인 일시',
  `last_login_ip`       varchar(50) DEFAULT NULL COMMENT '최종 로그인 IP',
  `retention_expire_at` datetime    NOT NULL COMMENT '감사 보관 만료 일시 (최소 3년 권고)',
  `withdrawn_by`        varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '탈퇴 처리자 ID',
  `created_by`          varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`          varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at`          timestamp   NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`admin_id`),
  KEY `idx_admin_withdraw_seq` (`admin_seq`),
  KEY `idx_admin_withdraw_login` (`login_id`),
  KEY `idx_admin_withdraw_expire` (`retention_expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='탈퇴 관리자 (감사 보관)';

-- ⑬ 활성 회원 — 사이트 스코프 로그인 (/login?siteCode=)
CREATE TABLE `tb_member` (
  `member_id`            varchar(40)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '회원 ID (MBR_ + UUIDv7)',
  `member_seq`           bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '회원 일련번호 (vw_user_login.uniq_id)',
  `site_id`              varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID (NULL=전역 회원 — uk 무력화 주의, 정책상 site 필수 권장)',
  `site_code`            varchar(30)   DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `login_id`             varchar(50)   NOT NULL COMMENT '로그인 ID (사이트 내 유일)',
  `password`             varchar(100)  NOT NULL COMMENT '비밀번호 (BCrypt)',
  `password_changed_at`  datetime      NOT NULL COMMENT '비밀번호 변경 일시',
  `password_expire_at`   datetime      DEFAULT NULL COMMENT '비밀번호 만료 일시',
  `role_ids`             text          DEFAULT NULL COMMENT '역할 ID CSV — closure 후손 전개 스냅샷',
  `member_name`          varchar(150)  DEFAULT NULL COMMENT '회원 이름 (평문)',
  `nickname`             varchar(100)  DEFAULT NULL COMMENT '닉네임',
  `email`                varchar(512)  DEFAULT NULL COMMENT '{AG} AES-256-GCM 이메일',
  `email_hash`           char(64)      DEFAULT NULL COMMENT 'HMAC-SHA256(email)',
  `email_verified_yn`    char(1)       NOT NULL DEFAULT 'N' COMMENT '이메일 인증 여부' CHECK (`email_verified_yn` in ('Y','N')),
  `phone`                varchar(512)  DEFAULT NULL COMMENT '{AG} AES-256-GCM 전화번호',
  `phone_hash`           char(64)      DEFAULT NULL COMMENT 'HMAC-SHA256(phone)',
  `birth_date`           varchar(512)  DEFAULT NULL COMMENT '{AG} AES-256-GCM 생년월일',
  `birth_year`           char(4)       DEFAULT NULL COMMENT 'YYYY 평문 — 연령대 통계용 (PIPA 일반 개인정보)',
  `gender`               char(1)       DEFAULT NULL COMMENT '성별 (M/F/N)' CHECK (`gender` in ('M','F','N')),
  `di`                   varchar(512)  DEFAULT NULL COMMENT '{AG} 본인인증 DI',
  `di_hash`              char(64)      DEFAULT NULL COMMENT 'HMAC-SHA256(di) 중복가입 방지',
  `parent_name`          varchar(150)  DEFAULT NULL COMMENT '14세 미만 법정대리인 이름 (평문)',
  `parent_di`            varchar(512)  DEFAULT NULL COMMENT '{AG} 법정대리인 본인확인 DI',
  `parent_di_hash`       char(64)      DEFAULT NULL COMMENT 'HMAC-SHA256(parent_di)',
  `address_zipcode`      varchar(10)   DEFAULT NULL COMMENT '우편번호',
  `address`              varchar(1024) DEFAULT NULL COMMENT '{AG} 기본주소',
  `address_detail`       varchar(1024) DEFAULT NULL COMMENT '{AG} 상세주소',
  `join_type`            varchar(20)   NOT NULL DEFAULT 'HOMEPAGE' COMMENT '가입 유형' CHECK (`join_type` in ('EMAIL','KAKAO','NAVER','GOOGLE','APPLE','HOMEPAGE','MOBILE')),
  `status`               varchar(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT '상태' CHECK (`status` in ('ACTIVE','LOCKED','EMAIL_PENDING','SUSPENDED')),
  `login_fail_count`     int(11)       NOT NULL DEFAULT 0 COMMENT '로그인 실패 횟수 (5회 잠금)' CHECK (`login_fail_count` >= 0),
  `locked_until`         datetime      DEFAULT NULL COMMENT '잠금 해제 예정 일시 (30분)',
  `captcha_required_yn`  char(1)       NOT NULL DEFAULT 'N' COMMENT '잠금 해제 후 CAPTCHA 강제 — 다음 성공 시 자동 N' CHECK (`captcha_required_yn` in ('Y','N')),
  `last_login_at`        datetime      DEFAULT NULL COMMENT '최종 로그인 일시',
  `last_login_ip`        varchar(50)   DEFAULT NULL COMMENT '최종 로그인 IP',
  `last_access_at`       datetime      DEFAULT NULL COMMENT '최종 접속 일시 (휴면 판정용)',
  `dormant_scheduled_at` datetime      DEFAULT NULL COMMENT '휴면 전환 예정 통지 일시',
  `privacy_agree_yn`     char(1)       NOT NULL COMMENT '개인정보 수집 동의' CHECK (`privacy_agree_yn` in ('Y','N')),
  `terms_agree_yn`       char(1)       NOT NULL COMMENT '이용약관 동의' CHECK (`terms_agree_yn` in ('Y','N')),
  `marketing_agree_yn`   char(1)       NOT NULL DEFAULT 'N' COMMENT '마케팅 수신 동의' CHECK (`marketing_agree_yn` in ('Y','N')),
  `sms_agree_yn`         char(1)       NOT NULL DEFAULT 'N' COMMENT 'SMS 수신 동의' CHECK (`sms_agree_yn` in ('Y','N')),
  `email_agree_yn`       char(1)       NOT NULL DEFAULT 'N' COMMENT '이메일 수신 동의' CHECK (`email_agree_yn` in ('Y','N')),
  `delete_yn`            char(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`           varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`           varchar(50)   DEFAULT NULL COMMENT '생성자 IP',
  `created_at`           timestamp     NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`           varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`           varchar(50)   DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`           timestamp     NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_member_seq` (`member_seq`),
  UNIQUE KEY `uk_member_login` (`site_id`,`login_id`),
  UNIQUE KEY `uk_member_identity` (`site_id`,`member_name`,`di_hash`,`parent_di_hash`),
  KEY `idx_member_email_hash` (`email_hash`),
  KEY `idx_member_phone_hash` (`phone_hash`),
  KEY `idx_member_last_access` (`last_access_at`),
  KEY `idx_member_status` (`status`,`delete_yn`),
  KEY `idx_member_ci_hash` (`di_hash`),
  KEY `idx_member_birth_year` (`birth_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='활성 회원';

-- ⑭ 회원 동의 이력 (insert-only)
CREATE TABLE `tb_member_consent` (
  `member_consent_id` varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ID (MBC_ + UUIDv7)',
  `member_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '회원 ID',
  `consent_type`      varchar(30)  NOT NULL COMMENT '동의 유형' CHECK (`consent_type` in ('TERMS','PRIVACY','MARKETING','SMS','EMAIL','THIRD_PARTY')),
  `consent_version`   varchar(20)  NOT NULL COMMENT '약관 버전',
  `agree_yn`          char(1)      NOT NULL COMMENT '동의 여부' CHECK (`agree_yn` in ('Y','N')),
  `agreed_at`         datetime     NOT NULL COMMENT '동의 일시',
  `client_ip`         varchar(50)  DEFAULT NULL COMMENT '동의 클라이언트 IP',
  `user_agent`        varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `created_by`        varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`        varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`        timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`member_consent_id`),
  KEY `idx_mbr_consent` (`member_id`,`consent_type`,`agreed_at`),
  CONSTRAINT `fk_mbr_consent` FOREIGN KEY (`member_id`) REFERENCES `tb_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='회원 동의 이력';

-- ⑮ 휴면 회원 (분리 보관 — vw_user_login 미포함: 휴면 로그인 시도는 앱이 별도 안내)
CREATE TABLE `tb_member_dormant` (
  `member_id`           varchar(40)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '원 회원 ID',
  `member_seq`          bigint(20)    NOT NULL COMMENT '원 회원 일련번호',
  `site_id`             varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID',
  `site_code`           varchar(30)   DEFAULT NULL COMMENT 'site_code 캐시',
  `login_id`            varchar(50)   NOT NULL COMMENT '로그인 ID',
  `password`            varchar(100)  NOT NULL COMMENT '비밀번호 (BCrypt)',
  `password_changed_at` datetime      NOT NULL COMMENT '비밀번호 변경 일시',
  `role_ids`            text          DEFAULT NULL COMMENT '역할 ID CSV 스냅샷',
  `member_name`         varchar(150)  DEFAULT NULL COMMENT '회원 이름 (평문, 스냅샷)',
  `nickname`            varchar(100)  DEFAULT NULL COMMENT '닉네임',
  `email`               varchar(512)  DEFAULT NULL COMMENT '{AG} 이메일',
  `email_hash`          char(64)      DEFAULT NULL COMMENT '이메일 해시',
  `phone`               varchar(512)  DEFAULT NULL COMMENT '{AG} 전화번호',
  `phone_hash`          char(64)      DEFAULT NULL COMMENT '전화번호 해시',
  `birth_date`          varchar(512)  DEFAULT NULL COMMENT '{AG} 생년월일',
  `birth_year`          char(4)       DEFAULT NULL COMMENT 'YYYY 평문',
  `gender`              char(1)       DEFAULT NULL COMMENT '성별',
  `di`                  varchar(512)  DEFAULT NULL COMMENT '{AG} DI',
  `di_hash`             char(64)      DEFAULT NULL COMMENT 'DI 해시',
  `parent_name`         varchar(150)  DEFAULT NULL COMMENT '법정대리인 이름 스냅샷',
  `parent_di`           varchar(512)  DEFAULT NULL COMMENT '{AG} 법정대리인 DI 스냅샷',
  `parent_di_hash`      char(64)      DEFAULT NULL COMMENT '법정대리인 DI 해시',
  `address_zipcode`     varchar(10)   DEFAULT NULL COMMENT '우편번호',
  `address`             varchar(1024) DEFAULT NULL COMMENT '{AG} 기본주소',
  `address_detail`      varchar(1024) DEFAULT NULL COMMENT '{AG} 상세주소',
  `join_type`           varchar(20)   NOT NULL DEFAULT 'HOMEPAGE' COMMENT '가입 유형',
  `status`              varchar(20)   NOT NULL DEFAULT 'DORMANT' COMMENT '상태 (DORMANT)',
  `last_login_at`       datetime      DEFAULT NULL COMMENT '최종 로그인 일시',
  `last_access_at`      datetime      DEFAULT NULL COMMENT '최종 접속 일시',
  `dormant_at`          datetime      NOT NULL COMMENT '휴면 전환 일시',
  `dormant_reason`      varchar(50)   NOT NULL DEFAULT 'INACTIVE_1Y' COMMENT '휴면 전환 사유',
  `restored_at`         datetime      DEFAULT NULL COMMENT '복귀 처리 일시',
  `delete_yn`           char(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`          varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`          varchar(50)   DEFAULT NULL COMMENT '생성자 IP',
  `created_at`          timestamp     NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`          varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`          varchar(50)   DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`          timestamp     NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_mbr_dormant_seq` (`member_seq`),
  UNIQUE KEY `uk_mbr_dormant_login` (`site_id`,`login_id`),
  KEY `idx_mbr_dormant_at` (`dormant_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='휴면 회원 (분리 보관)';

-- ⑯ 휴면 전환 안내 발송 이력
CREATE TABLE `tb_member_dormant_notice` (
  `notice_id`  varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ID (MDN_ + UUIDv7)',
  `member_id`  varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '회원 ID',
  `stage`      varchar(10) NOT NULL COMMENT '단계' CHECK (`stage` in ('30D','7D','1D')),
  `sent_at`    datetime    NOT NULL DEFAULT current_timestamp() COMMENT '발송 일시',
  `created_by` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(50) DEFAULT NULL COMMENT '생성자 IP',
  `created_at` timestamp   NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`notice_id`),
  UNIQUE KEY `uk_mdn_member_stage` (`member_id`,`stage`),
  KEY `idx_mdn_sent_at` (`sent_at`),
  CONSTRAINT `fk_mdn_member` FOREIGN KEY (`member_id`) REFERENCES `tb_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='회원 휴면 전환 안내 발송 이력';

-- ⑰ 회원 OAuth 매핑 — DEFAULT uuid_v7() 제거(앱 Uid.next 채번 — DB 함수 채번 금지 규약)
CREATE TABLE `tb_member_oauth` (
  `member_oauth_id`  varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ID (MBO_ + UUIDv7 — 앱 채번)',
  `member_id`        varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '회원 ID',
  `provider`         varchar(20)  NOT NULL COMMENT 'OAuth 제공자' CHECK (`provider` in ('NAVER','KAKAO','GOOGLE')),
  `provider_user_id` varchar(255) NOT NULL COMMENT 'provider 측 사용자 식별자 (sub/id)',
  `email_at_link`    varchar(255) DEFAULT NULL COMMENT '연결 당시 provider email (감사용)',
  `name_at_link`     varchar(255) DEFAULT NULL COMMENT '연결 당시 provider name (감사용)',
  `linked_at`        datetime     NOT NULL DEFAULT current_timestamp() COMMENT '연결 일시',
  `last_login_at`    datetime     DEFAULT NULL COMMENT '직전 OAuth 로그인 일시',
  `use_yn`           char(1)      NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`        char(1)      NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`       varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`       timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`       varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`       timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`member_oauth_id`),
  UNIQUE KEY `uk_oauth_provider_user` (`provider`,`provider_user_id`,`delete_yn`),
  KEY `idx_oauth_member` (`member_id`,`delete_yn`),
  CONSTRAINT `fk_oauth_member` FOREIGN KEY (`member_id`) REFERENCES `tb_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='회원 OAuth2 외부 계정 매핑';

-- ⑱ 회원 비밀번호 이력 (insert-only)
CREATE TABLE `tb_member_password_history` (
  `pwd_history_id` varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ID (MPH_ + UUIDv7)',
  `member_id`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '회원 ID',
  `password_hash`  varchar(100) NOT NULL COMMENT '비밀번호 해시 (BCrypt)',
  `changed_at`     datetime     NOT NULL COMMENT '변경 일시',
  `created_by`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`     varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`     timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`pwd_history_id`),
  KEY `idx_mbr_pwd_hst` (`member_id`,`changed_at`),
  CONSTRAINT `fk_mbr_pwd_hst` FOREIGN KEY (`member_id`) REFERENCES `tb_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='회원 비밀번호 이력 (재사용 방지)';

-- ⑲ 탈퇴 회원 (법정 최소항목만)
CREATE TABLE `tb_member_withdraw` (
  `member_id`           varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '원 회원 ID',
  `member_seq`          bigint(20)   NOT NULL COMMENT '원 회원 일련번호',
  `site_id`             varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID',
  `site_code`           varchar(30)  DEFAULT NULL COMMENT 'site_code 캐시',
  `login_id_hash`       char(64)     NOT NULL COMMENT 'HMAC-SHA256(login_id) 부정가입 추적',
  `di_hash`             char(64)     DEFAULT NULL COMMENT '재가입 탐지용 DI 해시',
  `withdraw_at`         datetime     NOT NULL COMMENT '탈퇴 일시',
  `withdraw_reason`     varchar(500) DEFAULT NULL COMMENT '탈퇴 사유',
  `withdraw_status`     varchar(50)  DEFAULT NULL COMMENT '탈퇴 상태' CHECK (`withdraw_status` is null or `withdraw_status` in ('USER_REQUEST','ADMIN_FORCE','DORMANT_EXPIRED')),
  `retention_expire_at` datetime     NOT NULL COMMENT '보관 만료 일시 (파기 예정)',
  `legal_basis`         varchar(100) DEFAULT NULL COMMENT '보관 근거 법령 조항',
  `created_by`          varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`          varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`          timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`member_id`),
  KEY `idx_mbr_wd_seq` (`member_seq`),
  KEY `idx_mbr_wd_expire` (`retention_expire_at`),
  KEY `idx_mbr_wd_di_hash` (`di_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='탈퇴 회원 (법정 최소항목만)';

-- ⑳ URL 접근제어 규칙 — DynamicAuthorizationManager 원천 (무매칭 DENY)
CREATE TABLE `tb_role_url_access` (
  `url_access_id`      varchar(40)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ID (RUA_ + UUIDv7)',
  `site_id`            varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID (NULL=전역 규칙)',
  `site_code`          varchar(30)   DEFAULT NULL COMMENT 'site_code 캐시',
  `url_pattern`        varchar(500)  NOT NULL COMMENT 'Ant 스타일 URL 패턴 (예: /adm/**)',
  `http_method`        varchar(10)   NOT NULL DEFAULT 'ALL' COMMENT 'HTTP 메서드' CHECK (`http_method` in ('ALL','GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS')),
  `access_type`        varchar(20)   NOT NULL COMMENT '접근 유형' CHECK (`access_type` in ('PERMIT_ALL','AUTHENTICATED','ANONYMOUS','ROLE','AUTH','IP_ONLY','DENY')),
  `required_roles`     text          DEFAULT NULL COMMENT 'ROLE 타입용 role_id CSV (하나라도 매칭 — 사용자 role_ids 전개와 교집합)',
  `required_auths`     text          DEFAULT NULL COMMENT 'AUTH 타입용 auth_id CSV',
  `allowed_user_types` varchar(100)  DEFAULT NULL COMMENT '허용 user_type CSV (MEMBER,ADMIN — EMPLOYEE 는 직원 도메인 도입 시)',
  `allowed_ips`        text          DEFAULT NULL COMMENT 'IP_ONLY 타입용 CIDR CSV',
  `require_csrf_yn`    char(1)       NOT NULL DEFAULT 'Y' COMMENT 'CSRF 체크 요구' CHECK (`require_csrf_yn` in ('Y','N')),
  `require_2fa_yn`     char(1)       NOT NULL DEFAULT 'N' COMMENT '2FA 추가 요구' CHECK (`require_2fa_yn` in ('Y','N')),
  `priority`           int(11)       NOT NULL DEFAULT 100 COMMENT '매칭 우선순위 (낮을수록 먼저)',
  `description`        varchar(500)  DEFAULT NULL COMMENT '설명',
  `remarks`            varchar(1000) DEFAULT NULL COMMENT '비고',
  `use_yn`             char(1)       NOT NULL DEFAULT 'Y' COMMENT '사용 여부' CHECK (`use_yn` in ('Y','N')),
  `delete_yn`          char(1)       NOT NULL DEFAULT 'N' COMMENT '삭제 여부' CHECK (`delete_yn` in ('Y','N')),
  `created_by`         varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`         varchar(50)   DEFAULT NULL COMMENT '생성자 IP',
  `created_at`         timestamp     NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`         varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`         varchar(50)   DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`         timestamp     NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`url_access_id`),
  UNIQUE KEY `uk_role_url_access` (`site_id`,`url_pattern`,`http_method`),
  KEY `idx_role_url_access_priority` (`use_yn`,`delete_yn`,`priority`),
  KEY `idx_role_url_access_site` (`site_id`,`use_yn`,`priority`),
  CONSTRAINT `chk_rua_roles` CHECK (`access_type` <> 'ROLE' or `required_roles` is not null),
  CONSTRAINT `chk_rua_auths` CHECK (`access_type` <> 'AUTH' or `required_auths` is not null),
  CONSTRAINT `chk_rua_ips`   CHECK (`access_type` <> 'IP_ONLY' or `allowed_ips` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='URL 접근제어 규칙 (권한 중심)';

-- ㉑ 통합 로그인 VIEW — /adm/login(ADMIN) · /login?siteCode=(MEMBER) 단일 조회 지점.
--    ★ 조회 시 user_type + (MEMBER 는 site_id) 필터 필수 — 동일 login_id 양측 공존 가능.
--    원안 교정: user_type 'STAFF'→'ADMIN' · department_name 은 tb_department 조인.
--    확장 지점: 직원(EMPLOYEE) 도입 시 tb_employee UNION 분기 추가.
--    보류: member role_codes 'ROLE_MEMBER' 고정 — ROLE_REAL 부여 방식은 후속 결정.
CREATE VIEW `vw_user_login` AS
SELECT 'MEMBER'              AS user_type,
       m.member_id           AS user_id,
       m.member_seq          AS uniq_id,
       m.site_id             AS site_id,
       NULL                  AS group_id,
       m.login_id            AS login_id,
       m.password            AS password,
       m.status              AS status,
       m.login_fail_count    AS login_fail_count,
       m.locked_until        AS locked_until,
       m.last_login_at       AS last_login_at,
       m.password_changed_at AS password_changed_at,
       m.password_expire_at  AS password_expire_at,
       'N'                   AS two_factor_enabled_yn,
       NULL                  AS two_factor_secret,
       NULL                  AS ip_whitelist,
       NULL                  AS allowed_time_from,
       NULL                  AS allowed_time_to,
       m.role_ids            AS role_ids,
       'ROLE_MEMBER'         AS role_codes,
       NULL                  AS department_id,
       NULL                  AS department_name,
       m.member_name         AS display_name,
       m.delete_yn           AS delete_yn
FROM tb_member m
WHERE m.delete_yn = 'N'
UNION ALL
SELECT 'ADMIN'               AS user_type,
       a.admin_id            AS user_id,
       a.admin_seq           AS uniq_id,
       NULL                  AS site_id,
       a.admin_group_id      AS group_id,
       a.login_id            AS login_id,
       a.password            AS password,
       a.status              AS status,
       a.login_fail_count    AS login_fail_count,
       a.locked_until        AS locked_until,
       a.last_login_at       AS last_login_at,
       a.password_changed_at AS password_changed_at,
       a.password_expire_at  AS password_expire_at,
       a.two_factor_enabled_yn AS two_factor_enabled_yn,
       a.two_factor_secret   AS two_factor_secret,
       a.ip_whitelist        AS ip_whitelist,
       a.allowed_time_from   AS allowed_time_from,
       a.allowed_time_to     AS allowed_time_to,
       a.role_ids            AS role_ids,
       a.role_codes          AS role_codes,
       a.department_id       AS department_id,
       d.department_name     AS department_name,
       a.admin_name          AS display_name,
       a.delete_yn           AS delete_yn
FROM tb_admin a
LEFT JOIN tb_department d ON d.department_id = a.department_id
WHERE a.delete_yn = 'N';
