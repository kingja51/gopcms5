-- ============================================================================
-- logging V1 — 접근 로그 · 감사 로그 · shedlock
-- ----------------------------------------------------------------------------
-- · PK 정책(확정 2026-07-28): 대량 로그는 bigint AUTO_INCREMENT — varchar(40) UUID
--   규약의 명시적 예외 (conventions.md §3). 복합 PK (id, logged_at) 는 logged_at
--   파티셔닝 대비(파티션 키는 PK 에 포함되어야 함).
-- · 크로스 DB FK 금지 — 타 DB ID(site_id 등)는 varchar 값만 보관 (conventions §3).
-- · logged_at 은 datetime 통일(timestamp 2038 한계 회피). trace_id 64 통일.
-- · shedlock — 스케줄러 분산 락 (ShedLock jdbc-template provider 표준 스키마).
-- ============================================================================

-- ① 접근 로그 — 모든 HTTP 요청 (정적 리소스 제외)
CREATE TABLE `log_access` (
  `log_access_id`   bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '접근로그 ID (단순 로그 — 숫자 채번, conventions §3 예외)',
  `site_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'SiteContext 해석 결과 (NULL=해석 불가)',
  `site_code`       varchar(30)  DEFAULT NULL COMMENT '집계 편의용 — site_code 캐시',
  `menu_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '요청 시점의 메뉴 ID (request_uri 파싱 회피용)',
  `actor_user_id`   varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '행위자 user_id (ADM_/MBR_ — 비로그인 NULL)',
  `actor_user_type` varchar(20)  DEFAULT NULL COMMENT '행위자 유형 (MEMBER|EMPLOYEE|ADMIN|ANONYMOUS)',
  `actor_login_id`  varchar(50)  DEFAULT NULL COMMENT '행위자 로그인 ID (탈퇴 후 추적용 스냅샷)',
  `request_uri`     varchar(500) NOT NULL COMMENT '요청 URI (쿼리스트링 제외)',
  `http_method`     varchar(10)  NOT NULL COMMENT 'HTTP 메서드 (GET/POST…)',
  `query_string`    varchar(500) DEFAULT NULL COMMENT '쿼리스트링 (민감 파라미터는 적재 필터에서 마스킹)',
  `referer`         varchar(500) DEFAULT NULL COMMENT 'Referer 헤더 (유입 경로 분석)',
  `client_ip`       varchar(50)  DEFAULT NULL COMMENT '클라이언트 IP (X-Forwarded-For 신뢰 프록시 해석 후)',
  `user_agent`      varchar(500) DEFAULT NULL COMMENT 'User-Agent (500자 절단)',
  `status_code`     int(11)      DEFAULT NULL COMMENT '200/302/403/404/500 ...',
  `response_ms`     int(11)      DEFAULT NULL COMMENT '요청-응답 elapsed ms',
  `bytes_sent`      bigint(20)   DEFAULT NULL COMMENT 'Content-Length (가능한 경우)',
  `session_id`      varchar(100) DEFAULT NULL COMMENT '세션 ID(GOPCMS_SID) 마지막 8자 — 전체 저장 금지',
  `trace_id`        varchar(64)  DEFAULT NULL COMMENT 'MDC traceId',
  `logged_at`       datetime     NOT NULL DEFAULT current_timestamp() COMMENT '로그 시각',
  `created_by`      varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`log_access_id`,`logged_at`),
  KEY `idx_log_access_logged_at` (`logged_at`),
  KEY `idx_log_access_site_logged` (`site_id`,`logged_at`),
  KEY `idx_log_access_uri` (`request_uri`(255),`logged_at`),
  KEY `idx_log_access_user` (`actor_user_id`,`logged_at`),
  KEY `idx_access_menu` (`menu_id`,`logged_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci
  COMMENT='접근 로그 — 모든 HTTP 요청 (정적 리소스 제외)';

-- ② 감사 로그 — 관리자 CUD 전수 (before/after JSON 스냅샷)
CREATE TABLE `log_audit` (
  `log_audit_id`    bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '감사로그 ID',
  `actor_user_id`   varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '행위자 user_id',
  `actor_user_type` varchar(20)   DEFAULT NULL COMMENT '행위자 유형',
  `actor_login_id`  varchar(50)   DEFAULT NULL COMMENT '행위자 로그인 ID',
  `action`          varchar(30)   NOT NULL COMMENT '행위 (CREATE/UPDATE/DELETE/LOGIN...)',
  `target_entity`   varchar(50)   DEFAULT NULL COMMENT '대상 엔티티',
  `target_id`       varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '대상 ID (접두어로 테이블 자명)',
  `request_uri`     varchar(1000) DEFAULT NULL COMMENT '요청 URI',
  `http_method`     varchar(10)   DEFAULT NULL COMMENT 'HTTP 메서드',
  `client_ip`       varchar(50)   DEFAULT NULL COMMENT '클라이언트 IP',
  `user_agent`      varchar(500)  DEFAULT NULL COMMENT 'User-Agent',
  `before_json`     longtext      DEFAULT NULL COMMENT '변경 전 JSON' CHECK (`before_json` is null or json_valid(`before_json`)),
  `after_json`      longtext      DEFAULT NULL COMMENT '변경 후 JSON' CHECK (`after_json` is null or json_valid(`after_json`)),
  `result`          varchar(20)   NOT NULL DEFAULT 'SUCCESS' COMMENT '결과' CHECK (`result` in ('SUCCESS','FAIL','ERROR')),
  `trace_id`        varchar(64)   DEFAULT NULL COMMENT 'MDC traceId (log_access 와 조인 키)',
  `logged_at`       datetime      NOT NULL DEFAULT current_timestamp() COMMENT '로그 시각',
  `created_by`      varchar(40)   CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`      varchar(50)   DEFAULT NULL COMMENT '생성자 IP',
  `created_at`      timestamp     NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`log_audit_id`,`logged_at`),
  KEY `idx_audit_actor` (`actor_user_id`,`logged_at`),
  KEY `idx_audit_entity` (`target_entity`,`target_id`,`logged_at`),
  KEY `idx_audit_action` (`action`,`logged_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci
  ROW_FORMAT=DYNAMIC COMMENT='감사 로그 — 관리자 CUD 전수';

-- ③ shedlock — 스케줄러 분산 락 (net.javacrumbs.shedlock jdbc-template 표준)
CREATE TABLE `shedlock` (
  `name`       varchar(64)  NOT NULL COMMENT '락 이름 (스케줄러 작업 단위)',
  `lock_until` timestamp(3) NOT NULL COMMENT '락 만료 시각',
  `locked_at`  timestamp(3) NOT NULL COMMENT '락 획득 시각',
  `locked_by`  varchar(255) NOT NULL COMMENT '획득 인스턴스 식별자',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci
  COMMENT='ShedLock 분산 락 (defaultLockAtMostFor=PT30M)';
