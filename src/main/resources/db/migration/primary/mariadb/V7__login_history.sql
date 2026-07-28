-- ============================================================================
-- V7 — 로그인 이력 (관리자·회원 공용) — P6-3
-- ----------------------------------------------------------------------------
-- 왜 primary_db 인가: 접두어 레지스트리(conventions §2.3)가 LGH = tb_login_history 로
--   primary 소속을 확정했다. 접근 로그(log_access)와 달리 "계정의 인증 사건"이라
--   계정 테이블과 같은 DB 에 두고 조회한다(잠금 해제·이상징후 판단의 근거).
--
-- insert-only — 갱신하지 않으므로 감사컬럼은 created_* 3종만 둔다.
-- user_id 는 nullable: 존재하지 않는 아이디로 들어온 시도도 남겨야 공격 탐지가 된다.
-- 실패 사유를 코드로 남기되(FAIL_*), 사용자에게 보이는 메시지는 항상 일반화한다
--   (계정 존재 여부·IP 등록 여부가 응답으로 새지 않게 — AdminAuthenticationProvider).
-- ============================================================================

CREATE TABLE `tb_login_history` (
  `login_history_id` varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '로그인 이력 ID (LGH_ + UUIDv7)',
  `user_type`        varchar(20)  NOT NULL COMMENT '사용자 유형' CHECK (`user_type` in ('MEMBER','ADMIN','EMPLOYEE')),
  `user_id`          varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '계정 ID (ADM_/MBR_ — 미존재 아이디 시도는 NULL)',
  `login_id`         varchar(50)  NOT NULL COMMENT '시도한 로그인 ID (탈퇴 후 추적용 평문 스냅샷)',
  `site_id`          varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID (회원 로그인 — 관리자는 NULL)',
  `site_code`        varchar(30)  DEFAULT NULL COMMENT 'site_code 캐시',
  `result`           varchar(20)  NOT NULL COMMENT '결과' CHECK (`result` in ('SUCCESS','FAIL_NOT_FOUND','FAIL_PASSWORD','FAIL_LOCKED','FAIL_DISABLED','FAIL_IP','FAIL_2FA','FAIL_CAPTCHA')),
  `fail_reason`      varchar(200) DEFAULT NULL COMMENT '실패 상세 (운영자용 — 사용자 노출 금지)',
  `client_ip`        varchar(50)  DEFAULT NULL COMMENT '클라이언트 IP (신뢰 프록시 해석 후)',
  `user_agent`       varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `session_id`       varchar(64)  DEFAULT NULL COMMENT '세션 ID 마지막 8자 (접근 로그와 대조용)',
  `attempted_at`     datetime     NOT NULL DEFAULT current_timestamp() COMMENT '시도 일시',
  `created_by`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`       varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`       timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`login_history_id`),
  KEY `idx_login_hst_user` (`user_id`,`attempted_at`),
  KEY `idx_login_hst_login_id` (`login_id`,`attempted_at`),
  KEY `idx_login_hst_ip` (`client_ip`,`attempted_at`),
  KEY `idx_login_hst_result` (`result`,`attempted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='로그인 이력 (관리자·회원 공용, insert-only)';

-- 비밀번호 만료 정책 기본값 — 신규 계정은 90일 후 만료 (V6 의 password_expire_at 은 NULL 허용).
-- 기존 dev 계정에도 소급 적용해 만료 강제 경로가 실제로 동작하는지 확인 가능하게 한다.
UPDATE `tb_admin`  SET `password_expire_at` = DATE_ADD(`password_changed_at`, INTERVAL 90 DAY)
 WHERE `password_expire_at` IS NULL;
UPDATE `tb_member` SET `password_expire_at` = DATE_ADD(`password_changed_at`, INTERVAL 90 DAY)
 WHERE `password_expire_at` IS NULL;
