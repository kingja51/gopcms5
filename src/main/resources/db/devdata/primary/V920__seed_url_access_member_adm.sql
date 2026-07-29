-- ============================================================================
-- V920 — 관리자 회원 관리 URL 접근 규칙 (P10-6)
-- ----------------------------------------------------------------------------
-- 회원 관리 화면 자체는 기존 `/adm/**`(ROLE_ADMIN, priority 20)로 이미 열린다.
-- 여기서 따로 잠그는 것은 <b>개인정보가 시스템 밖으로 나가는 경로</b> 하나다.
--
-- ROLE_PRIVACY 는 ADMIN>MANAGER>… 계층 <b>밖</b>의 독립 역할이라 ROLE_ADMIN 이어도
-- 자동으로 상속되지 않는다(V907). 명시적으로 부여받은 계정만 내려받을 수 있다 —
-- 최소 권한 원칙이고, "관리자니까 다 볼 수 있다" 를 막는 것이 이 역할의 존재 이유다.
--
-- priority 15 — `/adm/**`(20)보다 앞이어야 한다. 뒤에 두면 ROLE_ADMIN 규칙이 먼저
-- 매칭돼 이 제한이 아무 일도 하지 않는다(priority ASC, 첫 매칭 승).
--
-- 마스킹 해제는 URL 규칙으로 가를 수 없다 — 상세와 같은 주소({id}?reason=)이기 때문이다.
-- 그 판정은 MemberAdmController 가 권한을 직접 확인해서 한다.
-- ============================================================================

INSERT INTO tb_role_url_access
  (url_access_id, site_id, site_code, url_pattern, http_method, access_type,
   required_roles, allowed_user_types, priority, description) VALUES

('RUA_01985a10-0000-7000-8000-000000002011', NULL, NULL, '/adm/member/export', 'ALL',
 'ROLE', 'ROL_01985a10-0000-7000-8000-000000001008', 'ADMIN', 15,
 '회원 목록 내려받기 — ROLE_PRIVACY 전용(ROLE_ADMIN 자동 상속 없음)')
;
