-- ============================================================================
-- V907 — ROLE_PRIVACY 역할 추가 (dev 시드)
-- ----------------------------------------------------------------------------
-- 배경: 2026-07-28 사용자가 V906 에 직접 추가 시도 → 적용 완료 파일은 수정 금지
--       (checksum) 이므로 본 파일로 전진. (V906 헤더 주석 참조)
-- 설계: ROLE_PRIVACY(개인정보 관리자)는 ADMIN>MANAGER>... 단일 계층 "밖"의
--       독립 역할 — parent NULL, closure 는 self(depth 0) 1행만.
--       개인정보 열람 권한은 이 역할을 명시적으로 부여받은 계정에만 열리며
--       ROLE_ADMIN 이라도 자동 상속되지 않는다 (최소 권한 원칙).
-- ============================================================================

INSERT INTO tb_role (role_id, site_id, parent_role_id, role_code, role_name, sort_order) VALUES
('ROL_01985a10-0000-7000-8000-000000001008', NULL, NULL, 'ROLE_PRIVACY', '개인정보 관리자', 10);

-- closure self 행 — 계층 재전개 배치가 돌아도 독립 루트라 이 1행이 전부
INSERT INTO tb_role_hierarchy (role_hierarchy_id, ancestor_role_id, descendant_role_id, depth) VALUES
('RLH_01985a10-0000-7000-8000-000000001116','ROL_01985a10-0000-7000-8000-000000001008','ROL_01985a10-0000-7000-8000-000000001008',0);
