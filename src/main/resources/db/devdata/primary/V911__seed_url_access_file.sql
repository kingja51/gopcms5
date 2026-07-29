-- ============================================================================
-- V911 — 파일 도메인 URL 접근 규칙 (P8)
-- ----------------------------------------------------------------------------
-- 무매칭 = DENY 이므로 새 URL 은 규칙을 함께 넣어야 열린다 (conventions §7).
-- /api/** 는 priority 80 에 DENY 가 이미 있으므로, 파일 API 는 그보다 앞선
-- priority 로 명시 허용해야 한다 — 뒤에 두면 DENY 가 먼저 걸린다.
--
-- 권한 판정의 실제 근거는 두 겹이다:
--   ① 여기(URL 규칙) — 이 경로에 누가 들어올 수 있는가
--   ② FileAccessGuard — 업로드는 ROLE_REAL 이상, 다운로드는 file_group.download_auth
-- URL 규칙만으로는 "이 파일" 단위 판정을 할 수 없어서 ②가 반드시 필요하다.
-- ============================================================================

INSERT INTO tb_role_url_access
  (url_access_id, site_id, site_code, url_pattern, http_method, access_type,
   required_roles, allowed_user_types, priority, description) VALUES

-- 업로드 API — 인증만 통과시키고, ROLE_REAL 최소 요건은 서비스 계층이 판정한다.
-- (역할 요건을 여기에도 박으면 정책이 두 곳이 되어 어긋날 때 원인을 찾기 어렵다)
('RUA_01985a10-0000-7000-8000-000000001971', NULL, NULL, '/api/v1/file/upload', 'ALL',
 'AUTHENTICATED', NULL, NULL, 70, '첨부 업로드 — 최소 역할(ROLE_REAL)은 FileAccessGuard 가 판정'),

-- 에디터 본문 이미지 — 담당자 이상. 첨부와 경로를 분리해 정책을 다르게 건다.
('RUA_01985a10-0000-7000-8000-000000001972', NULL, NULL, '/api/v1/file/image', 'ALL',
 'ROLE', 'ROL_01985a10-0000-7000-8000-000000001003', NULL, 69,
 '에디터 본문 이미지 업로드 — ROLE_STAFF 이상'),

-- 다운로드는 공개 경로로 열되, 파일 단위 판정은 download_auth 가 한다.
-- 여기서 AUTHENTICATED 로 잠그면 ANONYMOUS 정책 첨부(공지 등)가 열리지 않는다.
('RUA_01985a10-0000-7000-8000-000000001973', NULL, NULL, '/file/**', 'GET',
 'PERMIT_ALL', NULL, NULL, 75, '파일 스트리밍 — 파일별 권한은 tb_file_group.download_auth')
;
