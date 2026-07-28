-- ============================================================================
-- V8 — 로그인 이력 결과코드 FAIL_EXPIRED 추가 + vw_user_login 에 captcha_required_yn 노출
-- ----------------------------------------------------------------------------
-- V7 은 이미 적용됐으므로 수정하지 않고 전진한다(flyway-migration.md 이력 규칙).
--
-- ① FAIL_EXPIRED — "자격은 맞았지만 비밀번호 유효기간이 지나 거부"는 비밀번호 오류와
--    구분해 남겨야 한다(잠금 카운트 대상이 아니고, 대응도 재설정이라 다르다).
--    ※ MariaDB 실측(2026-07-29): 컬럼에 인라인으로 쓴 CHECK 는 information_schema 에
--      컬럼명(`result`)으로 보이지만 DROP CONSTRAINT 로는 지워지지 않는다
--      ("Can't DROP CONSTRAINT `result`") — 컬럼 정의의 일부라서 MODIFY COLUMN 으로
--      컬럼을 CHECK 없이 재정의해야 사라진다. 이후 테이블 레벨 명명 제약으로 다시 건다.
--      → 앞으로 신규 DDL 의 CHECK 는 처음부터 테이블 레벨 명명 제약을 권장.
-- ② captcha_required_yn — 인증 원천은 vw_user_login 단일 조회라는 계약을 지키려면
--    CAPTCHA 강제 여부도 뷰에 있어야 한다(Provider 가 계정 테이블을 직접 보지 않는다).
-- ============================================================================

ALTER TABLE `tb_login_history`
  MODIFY COLUMN `result` varchar(20) NOT NULL COMMENT '결과 (SUCCESS/FAIL_*)';
ALTER TABLE `tb_login_history` ADD CONSTRAINT `chk_login_history_result`
  CHECK (`result` in ('SUCCESS','FAIL_NOT_FOUND','FAIL_PASSWORD','FAIL_LOCKED',
                      'FAIL_DISABLED','FAIL_IP','FAIL_2FA','FAIL_CAPTCHA','FAIL_EXPIRED'));

-- vw_user_login 재정의 — V6 정의 + captcha_required_yn 1컬럼 (그 외 변경 없음)
CREATE OR REPLACE VIEW `vw_user_login` AS
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
       m.captcha_required_yn AS captcha_required_yn,
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
       a.captcha_required_yn AS captcha_required_yn,
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
