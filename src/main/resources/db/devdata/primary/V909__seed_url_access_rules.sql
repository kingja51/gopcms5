-- ============================================================================
-- V909 — URL 접근제어 규칙 시드 (P6-2 DB RBAC)
-- ----------------------------------------------------------------------------
-- tb_role_url_access = DynamicAuthorizationManager 의 유일 원천.
--   · 평가 순서: priority ASC → 사이트 규칙 우선(site_id NOT NULL) → 긴 패턴 우선
--   · **무매칭 = DENY** — 새 URL 을 만들면 규칙을 함께 등록해야 화면이 열린다
--     (등록 절차: doc/conventions.md §7)
--
-- 배치 위치가 devdata 인 이유: required_roles 가 참조하는 역할(tb_role)이 V906 dev
-- 시드에 있다. 인증 기준 데이터(역할·관리자·URL 규칙)의 운영 이관은 P7 관리자 모듈에서
-- 한 벌로 승격한다. devdata 미포함 기동은 규칙 0건 = 전면 차단이므로 RbacSmokeRunner 가
-- 기동을 중단시킨다(침묵 실패 방지).
--
-- access_type: PERMIT_ALL · AUTHENTICATED · ANONYMOUS · ROLE · AUTH · IP_ONLY · DENY
-- ============================================================================

INSERT INTO tb_role_url_access
  (url_access_id, site_id, site_code, url_pattern, http_method, access_type,
   required_roles, allowed_user_types, priority, description) VALUES

-- ── 관리자 네임스페이스 (/adm/**) ─────────────────────────────────────────────
-- 로그인 폼·처리는 공개. 실제 게이트는 2단: 폼 노출 IP 게이트(LoginAdmController) +
-- (admin_id, ip) 쌍 매칭(AdminAuthenticationProvider).
('RUA_01985a10-0000-7000-8000-000000001901', NULL, NULL, '/adm/login',  'ALL', 'PERMIT_ALL',
 NULL, NULL, 10, '관리자 로그인 폼·인증 처리 (IP 게이트는 컨트롤러·Provider)'),
('RUA_01985a10-0000-7000-8000-000000001902', NULL, NULL, '/adm/logout', 'ALL', 'AUTHENTICATED',
 NULL, 'ADMIN', 11, '관리자 로그아웃'),
-- ROLE_ADMIN 보유자만. required_roles 는 role_id — 사용자 role_ids(closure 전개 CSV)와
-- 교집합으로 판정하므로 상위 역할이 하위 역할 규칙을 자동 통과한다.
('RUA_01985a10-0000-7000-8000-000000001903', NULL, NULL, '/adm/**',     'ALL', 'ROLE',
 'ROL_01985a10-0000-7000-8000-000000001001', 'ADMIN', 20, '관리자 화면 전체 — ROLE_ADMIN'),

-- ── 사용자 로그인 ────────────────────────────────────────────────────────────
('RUA_01985a10-0000-7000-8000-000000001911', NULL, NULL, '/login',  'ALL', 'PERMIT_ALL',
 NULL, NULL, 30, '회원 로그인 폼·인증 처리 (/login?siteCode=)'),
('RUA_01985a10-0000-7000-8000-000000001912', NULL, NULL, '/logout', 'ALL', 'PERMIT_ALL',
 NULL, NULL, 31, '회원 로그아웃'),

-- ── 정적 자원 · 에러 · 헬스 ──────────────────────────────────────────────────
-- 빠뜨리면 CSS/JS 가 통째로 차단돼 화면이 무너진다 (콘솔 404/403 금지 계약).
('RUA_01985a10-0000-7000-8000-000000001921', NULL, NULL, '/css/**',      'ALL', 'PERMIT_ALL', NULL, NULL, 40, '전역·레이아웃 CSS'),
('RUA_01985a10-0000-7000-8000-000000001922', NULL, NULL, '/js/**',       'ALL', 'PERMIT_ALL', NULL, NULL, 41, '앱·벤더 스크립트(self-host)'),
('RUA_01985a10-0000-7000-8000-000000001923', NULL, NULL, '/images/**',   'ALL', 'PERMIT_ALL', NULL, NULL, 42, '이미지'),
('RUA_01985a10-0000-7000-8000-000000001924', NULL, NULL, '/fonts/**',    'ALL', 'PERMIT_ALL', NULL, NULL, 43, '웹폰트'),
('RUA_01985a10-0000-7000-8000-000000001925', NULL, NULL, '/tmpl/**',     'ALL', 'PERMIT_ALL', NULL, NULL, 44, '템플릿 CSS (/tmpl/css/{templateCode}.css)'),
('RUA_01985a10-0000-7000-8000-000000001926', NULL, NULL, '/webjars/**',  'ALL', 'PERMIT_ALL', NULL, NULL, 45, 'webjars'),
('RUA_01985a10-0000-7000-8000-000000001927', NULL, NULL, '/favicon.ico', 'ALL', 'PERMIT_ALL', NULL, NULL, 46, '파비콘'),
-- ERROR 디스패치도 시큐리티 필터를 거친다 — 차단하면 404 페이지가 403 루프가 된다.
('RUA_01985a10-0000-7000-8000-000000001931', NULL, NULL, '/error',           'ALL', 'PERMIT_ALL', NULL, NULL, 50, '에러 디스패치'),
('RUA_01985a10-0000-7000-8000-000000001932', NULL, NULL, '/error/**',        'ALL', 'PERMIT_ALL', NULL, NULL, 51, '에러 디스패치 하위'),
('RUA_01985a10-0000-7000-8000-000000001933', NULL, NULL, '/actuator/health', 'ALL', 'PERMIT_ALL', NULL, NULL, 52, '헬스 체크 (LB·스모크)'),

-- ── 회원 전용 영역 (front 조임) ──────────────────────────────────────────────
-- ai 사이트는 실명인증 회원(ROLE_REAL)까지 요구 — 사이트 스코프 규칙이 전역보다 우선.
('RUA_01985a10-0000-7000-8000-000000001941', 'SIT_01985a10-0000-7000-8000-000000000302', 'ai',
 '/ai/member/**', 'ALL', 'ROLE',
 'ROL_01985a10-0000-7000-8000-000000001005', 'MEMBER', 55, 'ai 회원 전용 — 실명인증(ROLE_REAL)'),
('RUA_01985a10-0000-7000-8000-000000001942', NULL, NULL, '/*/member/**', 'ALL', 'AUTHENTICATED',
 NULL, 'MEMBER', 60, '사이트 회원 전용 영역 — 로그인 필수'),

-- ── 기본 차단 네임스페이스 ──────────────────────────────────────────────────
-- API·운영 엔드포인트는 opt-in — 신설 시 개별 허용 규칙을 먼저 등록한다.
('RUA_01985a10-0000-7000-8000-000000001951', NULL, NULL, '/api/**',      'ALL', 'DENY', NULL, NULL, 80, 'API 는 기본 차단 — 엔드포인트별 규칙 선행 등록'),
('RUA_01985a10-0000-7000-8000-000000001952', NULL, NULL, '/actuator/**', 'ALL', 'DENY', NULL, NULL, 90, 'health 외 액추에이터 차단'),

-- ── front 공개 컨텐츠 (최후 규칙) ───────────────────────────────────────────
-- 이 행을 지우면 무매칭 DENY 로 전 사이트가 닫힌다 — 비공개 전환은 사이트 스코프
-- 규칙(priority < 900)을 추가하는 방식으로 한다.
('RUA_01985a10-0000-7000-8000-000000001961', NULL, NULL, '/**', 'ALL', 'PERMIT_ALL',
 NULL, NULL, 900, 'front 공개 컨텐츠 (/{siteCode}/index·sitemap·{slug})');
