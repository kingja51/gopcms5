-- ============================================================================
-- V917 — 마이페이지 URL 접근 규칙 (P10-3)
-- ----------------------------------------------------------------------------
-- 마이페이지(개인정보 수정·비밀번호 변경)는 <b>실명인증과 무관</b>하다.
-- 그런데 사이트 스코프 규칙(`/ai/member/**` ROLE_REAL, priority 55)이 먼저 걸려
-- 갓 가입한 회원(ROLE_MEMBER 만 보유)이 자기 정보를 못 고치는 상태였다(실측).
--
-- 그래서 ROLE_REAL 규칙보다 <b>앞</b>에서 인증만 요구한다.
-- 실명인증이 필요한 기능(파일 업로드 등)은 여전히 55 번 규칙이 지킨다.
--
-- 개인정보 열람의 실제 관문은 URL 규칙이 아니라 step-up 재인증(StepUpAuth, 5분)이다 —
-- 로그인 세션만으로는 화면이 열리지 않는다.
-- ============================================================================

INSERT INTO tb_role_url_access
  (url_access_id, site_id, site_code, url_pattern, http_method, access_type,
   required_roles, allowed_user_types, priority, description) VALUES

('RUA_01985a10-0000-7000-8000-000000001996', NULL, NULL, '/*/member/mypage/**', 'ALL',
 'AUTHENTICATED', NULL, NULL, 51,
 '마이페이지 하위 — 실명인증과 무관(step-up 재인증이 실제 관문)'),

('RUA_01985a10-0000-7000-8000-000000001997', NULL, NULL, '/*/member/mypage', 'ALL',
 'AUTHENTICATED', NULL, NULL, 52,
 '마이페이지 — 로그인한 회원이면 자기 정보를 볼 수 있어야 한다')
;
