-- ============================================================================
-- V912 — 사용자 프로그램 네임스페이스 URL 접근 규칙 (P9-0)
-- ----------------------------------------------------------------------------
-- /bbs/{siteCode}/{bbsCode} · /prg/{siteCode}/... 는 사용자 화면이라 공개로 연다.
-- 실제 접근 판정은 그보다 안쪽에서 한다:
--   · 게시판 읽기/쓰기 권한 → tb_bbs_master.read_auth / write_auth (게시판마다 다르다)
--   · 첨부 다운로드        → tb_file_group.download_auth
-- URL 규칙만으로는 "이 게시판" 단위 판정을 할 수 없어서 두 겹으로 둔다.
--
-- 여기서 AUTHENTICATED 로 잠그면 공개 게시판(공지 등)이 비로그인에게 닫힌다.
-- 반대로 이 규칙이 없으면 무매칭 DENY 라 게시판이 아예 열리지 않는다.
--
-- priority 는 최후 규칙(/** 900)보다 앞이면 된다. 프로그램 네임스페이스끼리는
-- 겹치지 않으므로 같은 대역(200)에 나란히 둔다.
-- ============================================================================

INSERT INTO tb_role_url_access
  (url_access_id, site_id, site_code, url_pattern, http_method, access_type,
   required_roles, allowed_user_types, priority, description) VALUES

('RUA_01985a10-0000-7000-8000-000000001981', NULL, NULL, '/bbs/**', 'ALL',
 'PERMIT_ALL', NULL, NULL, 200,
 '게시판 사용자 화면 — 읽기/쓰기 권한은 tb_bbs_master 가 판정'),

('RUA_01985a10-0000-7000-8000-000000001982', NULL, NULL, '/prg/**', 'ALL',
 'PERMIT_ALL', NULL, NULL, 201,
 '개별 프로그램 사용자 화면 — 프로그램별 판정은 각 도메인이 담당')
;
