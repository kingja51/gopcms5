-- ============================================================================
-- V10 — 회원 인증번호(OTP) 보관 (P10-5 휴면 복원)
-- ----------------------------------------------------------------------------
-- 휴면 복원·이메일 인증에 쓰는 6자리 코드. 관리자 2FA(TOTP)와는 <b>다른 것</b>이다 —
-- TOTP 는 앱이 시간으로 생성하고 서버는 시크릿만 갖지만, 이 OTP 는 서버가 만들어
-- 메일로 보내고 한 번 쓰면 끝난다.
--
-- 설계 원칙 셋:
-- ① 평문으로 저장하지 않는다. DB 가 유출되면 진행 중인 인증이 통째로 뚫린다.
--    HMAC-SHA256 해시만 남기고(PiiHash 와 같은 키) 대조는 해시끼리 한다.
-- ② 시도 횟수를 <b>행에 둔다</b>. 세션에 두면 세션을 새로 잡아 무제한 대입할 수 있다.
-- ③ 만료·사용 여부를 행이 들고 있다. 지우지 않고 남기는 이유는 재발송 쿨다운과
--    남용 추적에 필요하기 때문이다. 정리는 보존기간 배치가 맡는다.
--
-- purpose 로 용도를 나눈다 — 휴면 복원용 코드로 이메일 인증을 통과하는 교차 사용 차단.
-- ============================================================================

CREATE TABLE `tb_member_otp` (
  `otp_id`        varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'OTP ID (MOT_ + UUIDv7)',
  `member_id`     varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '대상 회원 ID (tb_member 또는 tb_member_dormant)',
  `site_id`       varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '사이트 ID',
  `purpose`       varchar(30)  NOT NULL COMMENT '용도 — 교차 사용 방지',
  `code_hash`     char(64)     NOT NULL COMMENT 'HMAC-SHA256(코드) — 평문 저장 금지',
  `expires_at`    datetime     NOT NULL COMMENT '만료 일시 (발급 + TTL)',
  `attempt_count` int(11)      NOT NULL DEFAULT 0 COMMENT '검증 시도 횟수 (세션이 아니라 행에 둔다)',
  `verified_at`   datetime     DEFAULT NULL COMMENT '검증 성공 일시 (NULL = 미사용)',
  `client_ip`     varchar(50)  DEFAULT NULL COMMENT '발급 요청 IP',
  `created_by`    varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`    varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`    timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by`    varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip`    varchar(50)  DEFAULT NULL COMMENT '수정자 IP',
  `updated_at`    timestamp    NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`otp_id`),
  KEY `idx_otp_member_purpose` (`member_id`,`purpose`,`created_at`),
  KEY `idx_otp_expires` (`expires_at`),
  CONSTRAINT `chk_otp_purpose` CHECK (`purpose` in ('DORMANT_RESTORE','EMAIL_VERIFY','PASSWORD_RESET'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci COMMENT='회원 인증번호(OTP) — 평문 미보관, 시도 횟수는 행에 둔다';
