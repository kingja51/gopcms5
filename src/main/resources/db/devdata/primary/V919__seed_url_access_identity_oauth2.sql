-- ============================================================================
-- V919 — 본인인증(NICE) · 소셜 로그인(OAuth2) URL 접근 규칙
-- ----------------------------------------------------------------------------
-- 무매칭 DENY 라서 새 URL 은 규칙을 넣어야 비로소 열린다(conventions.md §7).
--
-- ① /*/member/join/**  — V914 는 `/*/member/join` 하나만 열었다. 가입이 5단계로
--    나뉘면서 /join/terms, /join/verify, /join/form, /join/complete 가 생겼고,
--    AntPathMatcher 에서 `/*/member/join` 은 하위 경로를 <b>포함하지 않는다</b>.
--    규칙 없이 두면 2단계에서 로그인 화면으로 튕긴다.
--
-- ② /member/identity/nice/** — 콜백 URL 은 NICE 콘솔에 등록한 문자열 하나뿐이라
--    사이트별 경로를 가질 수 없다. 그래서 siteCode 없는 고정 경로이고, 인증 전
--    사용자가 지나가므로 PERMIT_ALL 이다. 콜백 위조는 URL 규칙이 아니라 세션에 둔
--    요청번호(REQ_SEQ) 대조로 막는다(NiceCheckUsrController).
--
-- ③ /member/oauth2/**  — 같은 이유로 사이트 무관 경로다. 위조 방어는 state 1회 소비.
--
-- priority 는 52 — 사이트 스코프 회원 규칙(55)·전역 회원 규칙(60)보다 앞서야 한다.
-- 뒤에 두면 "가입하려면 로그인부터 하라" 는 순환에 빠진다.
-- ============================================================================

INSERT INTO tb_role_url_access
  (url_access_id, site_id, site_code, url_pattern, http_method, access_type,
   required_roles, allowed_user_types, priority, description) VALUES

('RUA_01985a10-0000-7000-8000-000000002001', NULL, NULL, '/*/member/join/**', 'ALL',
 'PERMIT_ALL', NULL, NULL, 53,
 '회원 가입 마법사 하위 단계 — 약관·본인인증·정보입력·완료'),

('RUA_01985a10-0000-7000-8000-000000002002', NULL, NULL, '/member/identity/nice', 'ALL',
 'PERMIT_ALL', NULL, NULL, 52,
 'NICE 본인인증 진입(팝업) — 사이트 무관 고정 경로'),

('RUA_01985a10-0000-7000-8000-000000002003', NULL, NULL, '/member/identity/nice/**', 'ALL',
 'PERMIT_ALL', NULL, NULL, 52,
 'NICE 본인인증 콜백 — 외부 도메인이 POST 한다(요청번호 대조로 위조 차단)'),

('RUA_01985a10-0000-7000-8000-000000002004', NULL, NULL, '/member/oauth2/**', 'ALL',
 'PERMIT_ALL', NULL, NULL, 52,
 '소셜 로그인 인가 시작·콜백 — 사이트는 세션이 기억한다(state 1회 소비로 위조 차단)')
;
