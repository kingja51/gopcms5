-- ============================================================================
-- V900 — 개발 전용 시드 (dev/local 프로파일에서만 locations 에 포함)
-- 버전 900+ 는 개발 시드 예약 대역 — 운영 locations 에는 이 폴더가 없다.
-- ============================================================================

-- 데모 사이트: main (KRDS 템플릿 · 기본 테마(theme_id NULL) · 템플릿 기본 레이아웃 · 기본 사이트)
INSERT INTO tb_site (site_id, site_code, site_name, default_lang, template_id, theme_id, layout_id, default_yn, description) VALUES
('SIT_01985a10-0000-7000-8000-000000000301','main','데모 메인 사이트','ko',
 'TPL_01985a10-0000-7000-8000-000000000101', NULL, NULL, 'Y', '개발 검증용 — http://127.0.0.1:8080/main/index');
