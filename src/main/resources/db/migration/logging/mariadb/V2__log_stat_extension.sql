-- ============================================================================
-- V2 (logging) — 로그 5종 + 통계 5종 확장
-- ----------------------------------------------------------------------------
-- 원안: D:\test\logging.sql (선행 프로젝트 덤프) — 아래를 교정해 반영:
--   · DROP TABLE IF EXISTS 제거 — 마이그레이션에서 파괴적 구문 금지(이미 쌓인 로그 소실)
--   · AUTO_INCREMENT=87 등 덤프 잔재 제거
--   · ID 컬럼 ascii_bin 적용 (conventions §1.2) — 로그의 actor/site/menu 참조 ID 포함
--   · client_ip 폭 50 으로 통일 (원안은 log_privacy_access 만 45)
--   · CHECK 은 테이블 레벨 명명 제약으로 — 인라인 CHECK 은 값 목록 하나 늘리는 데도
--     컬럼 재정의가 필요하다 (V8 실측, flyway-migration.md §5)
--   · 전 컬럼 COMMENT 보강
--
-- 시간 파티셔닝 대상 테이블은 PK 에 시각 컬럼을 포함한다(V1 log_access 와 동일 규칙) —
-- 나중에 RANGE 파티션을 붙일 때 PK 재작성 없이 들어갈 수 있게 하는 사전 설계다.
-- 크로스 DB FK 는 두지 않는다 — site_id/menu_id 는 값 참조일 뿐이다 (CLAUDE.md 3-DB 분리).
-- ============================================================================

-- ① 애플리케이션 에러 로그 ---------------------------------------------------
CREATE TABLE `log_error` (
  `log_error_id`    bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '에러로그 ID (로그는 UUID 대신 시퀀스 — V1 log_access 규칙)',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID (값 참조)',
  `site_code`       varchar(30)  DEFAULT NULL COMMENT 'site_code 캐시',
  `menu_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '에러가 발생한 메뉴 ID',
  `actor_user_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '행위자 ID (비로그인 NULL)',
  `actor_user_type` varchar(20)  DEFAULT NULL COMMENT '행위자 유형 (MEMBER|EMPLOYEE|ADMIN|ANONYMOUS)',
  `actor_login_id`  varchar(50)  DEFAULT NULL COMMENT '행위자 로그인 ID 스냅샷',
  `request_uri`     varchar(500) DEFAULT NULL COMMENT '요청 URI',
  `http_method`     varchar(10)  DEFAULT NULL COMMENT 'HTTP 메서드',
  `query_string`    varchar(500) DEFAULT NULL COMMENT '쿼리스트링 (민감 파라미터 마스킹 후)',
  `client_ip`       varchar(50)  DEFAULT NULL COMMENT '클라이언트 IP (신뢰 프록시 해석 후)',
  `user_agent`      varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `error_class`     varchar(255) NOT NULL COMMENT '예외 FQCN (예: java.lang.NullPointerException)',
  `error_message`   varchar(2000) DEFAULT NULL COMMENT '예외 메시지',
  `stack_trace`     mediumtext   DEFAULT NULL COMMENT '스택트레이스 (32KB 내로 절단 권장 — 원문 보관 아님)',
  `status_code`     int(11)      DEFAULT NULL COMMENT '응답 상태 코드 (500 등)',
  `trace_id`        varchar(64)  DEFAULT NULL COMMENT 'MDC traceId — 접근 로그와 대조하는 키',
  `session_id`      varchar(100) DEFAULT NULL COMMENT '세션 ID (마지막 8자 권장)',
  `logged_at`       timestamp    NOT NULL DEFAULT current_timestamp() COMMENT '발생 일시',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`log_error_id`,`logged_at`),
  KEY `idx_log_error_logged_at` (`logged_at`),
  KEY `idx_log_error_class` (`error_class`(100),`logged_at`),
  KEY `idx_log_error_user` (`actor_user_id`,`logged_at`),
  KEY `idx_log_error_uri` (`request_uri`(255),`logged_at`),
  KEY `idx_log_error_menu` (`menu_id`,`logged_at`),
  KEY `idx_log_error_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='애플리케이션 에러 로그 — 미처리 예외 자동 기록';

-- ② 파일 다운로드 이력 -------------------------------------------------------
CREATE TABLE `log_file_download` (
  `log_file_download_id` bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '다운로드로그 ID',
  `file_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'tb_file.file_id (SINGLE/ADMIN)',
  `file_group_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'tb_file_group.file_group_id (GROUP_ZIP)',
  `download_type`   varchar(20)  NOT NULL COMMENT '유형 (SINGLE=단일, ADMIN=관리자 상태무관, GROUP_ZIP=그룹 ZIP)',
  `actor_user_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '내려받은 주체 (익명 NULL)',
  `actor_user_type` varchar(20)  DEFAULT NULL COMMENT 'MEMBER|EMPLOYEE|ADMIN|ANONYMOUS',
  `actor_login_id`  varchar(50)  DEFAULT NULL COMMENT '로그인 ID 스냅샷',
  `original_name`   varchar(512) DEFAULT NULL COMMENT '원본 파일명 (삭제 후 추적용 스냅샷)',
  `extension`       varchar(20)  DEFAULT NULL COMMENT '확장자',
  `size_bytes`      bigint(20)   DEFAULT NULL COMMENT '전송 바이트 수 (Range 요청이면 부분)',
  `request_uri`     varchar(1000) DEFAULT NULL COMMENT '요청 URI',
  `client_ip`       varchar(50)  DEFAULT NULL COMMENT '클라이언트 IP',
  `user_agent`      varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `trace_id`        varchar(64)  DEFAULT NULL COMMENT 'MDC traceId',
  `result`          varchar(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT '결과 (SUCCESS|FAIL|BLOCKED)',
  `downloaded_at`   timestamp    NOT NULL DEFAULT current_timestamp() COMMENT '다운로드 일시',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`log_file_download_id`,`downloaded_at`),
  KEY `idx_logfiledl_file` (`file_id`,`downloaded_at`),
  KEY `idx_logfiledl_group` (`file_group_id`,`downloaded_at`),
  KEY `idx_logfiledl_actor` (`actor_user_id`,`downloaded_at`),
  KEY `idx_logfiledl_type` (`download_type`,`downloaded_at`),
  CONSTRAINT `chk_logfiledl_type` CHECK (`download_type` in ('SINGLE','GROUP_ZIP','ADMIN')),
  CONSTRAINT `chk_logfiledl_result` CHECK (`result` in ('SUCCESS','FAIL','BLOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='파일 다운로드 이력 (file_id 단위 1행)';

-- ③ 개인정보 접근 이력 -------------------------------------------------------
--    개인정보보호법 §29 · 안전성확보조치 고시 §8 의 "접속기록" 요건 대응.
CREATE TABLE `log_privacy_access` (
  `log_privacy_access_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '접근이력 ID',
  `actor_user_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '취급자 ID',
  `actor_user_type` varchar(20)  NOT NULL COMMENT '취급자 유형 (ADMIN|EMPLOYEE|MEMBER|SYSTEM)',
  `actor_login_id`  varchar(50)  DEFAULT NULL COMMENT '취급자 로그인 ID (사람이 읽는 식별자)',
  `client_ip`       varchar(50)  NOT NULL COMMENT '취급자 IP',
  `user_agent`      varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `session_id`      varchar(100) DEFAULT NULL COMMENT '세션 식별자 (다중 세션 변별)',
  `request_uri`     varchar(1000) DEFAULT NULL COMMENT '접근 URI (비-HTTP 경로면 NULL)',
  `http_method`     varchar(10)  DEFAULT NULL COMMENT 'HTTP 메서드',
  `trace_id`        varchar(64)  DEFAULT NULL COMMENT 'MDC traceId — 접근/에러 로그와 타임라인 결합 키',
  `target_entity`   varchar(50)  NOT NULL COMMENT '대상 테이블 (tb_member/tb_admin/tb_employee 등)',
  `target_user_type` varchar(20) DEFAULT NULL COMMENT '정보주체 유형',
  `target_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '정보주체 ID (목록 조회는 NULL)',
  `target_count`    int(11)      NOT NULL DEFAULT 1 COMMENT '처리 건수 (목록·엑셀은 N건)',
  `pii_fields`      varchar(500) DEFAULT NULL COMMENT '취급한 PII 항목 CSV (name,email,phone …)',
  `access_action`   varchar(20)  NOT NULL COMMENT '수행 업무 (READ|SEARCH|EXPORT|PRINT|UPDATE|DELETE|DECRYPT)',
  `access_reason`   varchar(2000) DEFAULT NULL COMMENT '대량 조회·다운로드 사유 (소명 자료)',
  `result`          varchar(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT '처리 결과 (SUCCESS|FAIL|DENIED|ERROR)',
  `fail_reason`     varchar(500) DEFAULT NULL COMMENT '실패·거부 사유',
  `accessed_at`     timestamp    NOT NULL DEFAULT current_timestamp() COMMENT '접근 일시',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`log_privacy_access_id`,`accessed_at`),
  KEY `idx_privacy_actor` (`actor_user_id`,`accessed_at`),
  KEY `idx_privacy_target` (`target_entity`,`target_id`,`accessed_at`),
  KEY `idx_privacy_login_id` (`actor_login_id`,`accessed_at`),
  KEY `idx_privacy_action` (`access_action`,`accessed_at`),
  KEY `idx_privacy_trace` (`trace_id`),
  KEY `idx_privacy_result` (`result`,`accessed_at`),
  CONSTRAINT `chk_privacy_action` CHECK (`access_action` in ('READ','SEARCH','EXPORT','PRINT','UPDATE','DELETE','DECRYPT')),
  CONSTRAINT `chk_privacy_result` CHECK (`result` in ('SUCCESS','FAIL','DENIED','ERROR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='개인정보 접근 이력 (개인정보보호법 §29 접속기록)';

-- ④ 개인정보 파기 이력 -------------------------------------------------------
--    파기 대상 본인을 다시 식별할 수 없어야 하므로 user_id 는 해시만 남긴다.
CREATE TABLE `log_pii_purge` (
  `pii_purge_log_id` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '파기이력 ID (PPG_ + UUIDv7)',
  `user_type`       varchar(20)  NOT NULL COMMENT '정보주체 유형 (MEMBER|EMPLOYEE|ADMIN)',
  `user_id_hash`    char(64)     NOT NULL COMMENT 'HMAC-SHA256(user_id) — 동일인 추적은 되되 역추적은 불가',
  `purged_at`       datetime     NOT NULL COMMENT '파기 일시',
  `purge_reason`    varchar(100) NOT NULL COMMENT '파기 사유 (RETENTION_EXPIRED 등)',
  `table_list`      varchar(500) NOT NULL COMMENT '파기 대상 테이블 CSV',
  `legal_basis`     varchar(500) DEFAULT NULL COMMENT '파기 근거 법령·조항',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`pii_purge_log_id`),
  KEY `idx_pii_purge_time` (`purged_at`),
  KEY `idx_pii_purge_user` (`user_id_hash`),
  CONSTRAINT `chk_pii_purge_user_type` CHECK (`user_type` in ('MEMBER','EMPLOYEE','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='개인정보 파기 이력 (insert-only)';

-- ⑤ 보안 이벤트 로그 ---------------------------------------------------------
CREATE TABLE `log_security` (
  `log_security_id` bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '보안로그 ID',
  `event_type`      varchar(50)  NOT NULL COMMENT '이벤트 유형 (UPLOAD_BLOCK/LOGIN_LOCK/SSRF_BLOCK/CSP_VIOLATION 등)',
  `severity`        varchar(10)  NOT NULL COMMENT '심각도 (INFO|WARN|CRITICAL)',
  `actor_id`        varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '관련 사용자 ID',
  `client_ip`       varchar(50)  DEFAULT NULL COMMENT '클라이언트 IP',
  `user_agent`      varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `request_uri`     varchar(1000) DEFAULT NULL COMMENT '요청 URI',
  `detail_json`     longtext     DEFAULT NULL COMMENT '이벤트별 상세 (JSON)',
  `trace_id`        varchar(64)  DEFAULT NULL COMMENT 'MDC traceId',
  `logged_at`       datetime     NOT NULL DEFAULT current_timestamp() COMMENT '발생 일시',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`log_security_id`,`logged_at`),
  KEY `idx_security_event` (`event_type`,`logged_at`),
  KEY `idx_security_ip` (`client_ip`,`logged_at`),
  KEY `idx_security_actor` (`actor_id`,`logged_at`),
  KEY `idx_security_trace` (`trace_id`),
  KEY `idx_security_severity` (`severity`,`logged_at`),
  CONSTRAINT `chk_security_severity` CHECK (`severity` in ('INFO','WARN','CRITICAL')),
  CONSTRAINT `chk_security_detail_json` CHECK (`detail_json` is null or json_valid(`detail_json`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='보안 이벤트 로그';

-- ⑥ 접근 로그 일별 집계 ------------------------------------------------------
--    log_access 를 배치가 말아 넣는다. bucket_hour NULL = 하루 전체 합계 행.
CREATE TABLE `stat_access_daily` (
  `stat_id`         bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '집계행 ID',
  `stat_date`       date         NOT NULL COMMENT '집계 일자',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID (NULL=전체)',
  `site_code`       varchar(30)  DEFAULT NULL COMMENT 'site_code 캐시',
  `user_type`       varchar(20)  DEFAULT NULL COMMENT '사용자 유형별 분해 (NULL=전체)',
  `bucket_hour`     tinyint(4)   DEFAULT NULL COMMENT '시간대 0~23 (NULL=하루 전체)',
  `pv`              bigint(20)   NOT NULL DEFAULT 0 COMMENT '페이지뷰(요청 수)',
  `uv`              bigint(20)   NOT NULL DEFAULT 0 COMMENT '순방문자 (DISTINCT client_ip)',
  `unique_users`    bigint(20)   NOT NULL DEFAULT 0 COMMENT 'DISTINCT actor_user_id (로그인 사용자만)',
  `avg_response_ms` int(11)      NOT NULL DEFAULT 0 COMMENT '평균 응답 시간(ms)',
  `p95_response_ms` int(11)      NOT NULL DEFAULT 0 COMMENT '95 퍼센타일 응답 시간(ms)',
  `err_4xx`         bigint(20)   NOT NULL DEFAULT 0 COMMENT '4xx 응답 수',
  `err_5xx`         bigint(20)   NOT NULL DEFAULT 0 COMMENT '5xx 응답 수',
  `bytes_sent`      bigint(20)   NOT NULL DEFAULT 0 COMMENT '전송 바이트 합계',
  `computed_at`     timestamp    NOT NULL DEFAULT current_timestamp() COMMENT '집계 수행 일시',
  PRIMARY KEY (`stat_id`),
  UNIQUE KEY `uk_stat_access_daily` (`stat_date`,`site_id`,`user_type`,`bucket_hour`),
  KEY `idx_stat_access_date` (`stat_date`),
  KEY `idx_stat_access_site_date` (`site_id`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='접근 로그 일별 집계 (야간 배치)';

-- ⑦ URI 별 일별 집계 (인기 페이지) -------------------------------------------
CREATE TABLE `stat_access_uri_daily` (
  `stat_id`         bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '집계행 ID',
  `stat_date`       date         NOT NULL COMMENT '집계 일자',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID',
  `site_code`       varchar(30)  DEFAULT NULL COMMENT 'site_code 캐시',
  `request_uri`     varchar(500) NOT NULL COMMENT '요청 URI (쿼리 제외)',
  `pv`              bigint(20)   NOT NULL DEFAULT 0 COMMENT '페이지뷰',
  `uv`              bigint(20)   NOT NULL DEFAULT 0 COMMENT '순방문자',
  `avg_response_ms` int(11)      NOT NULL DEFAULT 0 COMMENT '평균 응답 시간(ms)',
  `computed_at`     timestamp    NOT NULL DEFAULT current_timestamp() COMMENT '집계 수행 일시',
  PRIMARY KEY (`stat_id`),
  UNIQUE KEY `uk_stat_access_uri_daily` (`stat_date`,`site_id`,`request_uri`),
  KEY `idx_stat_access_uri_date` (`stat_date`,`pv` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='URI 별 일별 집계 — 인기 페이지 랭킹';

-- ⑧ 콘텐츠 일별 조회수 -------------------------------------------------------
CREATE TABLE `stat_content_view` (
  `stat_date`       date         NOT NULL COMMENT '집계 일자',
  `content_id`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '컨텐츠 ID (값 참조)',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID — 사이트별 집계 조회용(원안 누락분 추가)',
  `view_count`      bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '해당 일자 조회수',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`stat_date`,`content_id`),
  KEY `idx_stat_content_view_c` (`content_id`,`stat_date`),
  KEY `idx_stat_content_view_site` (`site_id`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='컨텐츠 일별 조회수';

-- ⑨ 일별 방문 통계 -----------------------------------------------------------
CREATE TABLE `stat_daily_visit` (
  `stat_date`       date         NOT NULL COMMENT '집계 일자',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '사이트 ID',
  `visit_count`     bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '방문수(세션 기준)',
  `unique_count`    bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '순방문자수',
  `pv_count`        bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '페이지뷰',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`stat_date`,`site_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='일별 방문 통계';

-- ⑩ 검색어 일별 통계 ---------------------------------------------------------
CREATE TABLE `stat_search_keyword` (
  `stat_date`       date         NOT NULL COMMENT '집계 일자',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '사이트 ID',
  `keyword`         varchar(200) NOT NULL COMMENT '검색어 (정규화·소문자 권장)',
  `search_count`    bigint(20) unsigned NOT NULL DEFAULT 0 COMMENT '검색 횟수',
  `result_count_avg` int(11)     DEFAULT NULL COMMENT '평균 결과 건수 — 0 이면 "찾지 못한 검색어"',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`      varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`      timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`stat_date`,`site_id`,`keyword`),
  KEY `idx_stat_search_count` (`stat_date`,`search_count` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='검색어 일별 통계';
