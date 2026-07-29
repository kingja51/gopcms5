-- ============================================================================
-- V913 — 게시판 반응 API 접근 규칙 (P9-4)
-- ----------------------------------------------------------------------------
-- 좋아요·신고는 로그인한 사용자만 할 수 있다. 익명 좋아요는 중복을 막을 수 없어
-- 숫자가 의미를 잃고(DDL 의 user_id NOT NULL), 익명 신고는 남용을 추적할 수 없다.
--
-- priority 는 `/api/**` DENY(80)보다 앞이어야 한다 — 뒤에 두면 영영 닫힌 채로 남는다.
-- 파일 API(69·70) 옆 대역(72·73)에 둔다.
-- ============================================================================

INSERT INTO tb_role_url_access
  (url_access_id, site_id, site_code, url_pattern, http_method, access_type,
   required_roles, allowed_user_types, priority, description) VALUES

('RUA_01985a10-0000-7000-8000-000000001991', NULL, NULL, '/api/v1/board/like', 'ALL',
 'AUTHENTICATED', NULL, NULL, 72,
 '좋아요 토글 — 익명 불가(중복 방지 불가)'),

('RUA_01985a10-0000-7000-8000-000000001992', NULL, NULL, '/api/v1/board/report', 'ALL',
 'AUTHENTICATED', NULL, NULL, 73,
 '신고 접수 — 익명 불가(남용 추적 불가)')
;
