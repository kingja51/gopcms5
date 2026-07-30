-- ============================================================================
-- V12 — tb_member.member_name 을 NOT NULL 로
--
-- 근거: 회원 테이블에 이름이 없는 행은 성립하지 않는다(사용자 확정 2026-07-30).
--       가입 경로가 이미 이름을 필수 검증하고 있었으므로(MemberJoinServiceImpl.validate)
--       DDL 이 코드를 뒤늦게 따라가는 변경이다.
--
-- ── 순서가 중요하다 ─────────────────────────────────────────────────────────
-- 먼저 NULL 을 없애고 나서 제약을 건다. 순서를 바꾸면 기존 NULL 행 때문에 ALTER 가
-- 실패한다(strict mode). NULL 이 남아 있는 유일한 경우는 <b>이미 탈퇴한 회원</b>이다 —
-- 종전 nullifyPii 가 이름까지 NULL 로 만들었기 때문이다.
--
-- 앞으로는 NULL 대신 <b>마스킹된 이름</b>을 남긴다(홍길동 → 홍*동, 사용자 확정).
-- 이미 NULL 이 된 행도 같은 모양으로 맞춘다:
--   ① 탈퇴 원장에 마스킹 이름이 있으면(V11 이후 탈퇴) 그 값을 가져온다 — 같은 값이다
--   ② 원장에도 없으면(V11 이전 탈퇴) 복구할 원본이 없으므로 '-' 를 넣는다
--      같은 UPDATE 문이 password 에 이미 쓰는 값이고, 이름이 될 수 없는 문자라
--      "값이 있는 척" 하지 않는다
-- 어느 쪽도 파기를 되돌리지 않는다 — 원본 평문은 이미 사라졌다.
--
-- ── parent_name 은 그대로 NULL 허용 ─────────────────────────────────────────
-- 법정대리인은 14세 미만 회원에게만 있다. NOT NULL 로 만들면 성인 회원이
-- 가입할 수 없다 — 여기서 손대지 않는 것이 맞다.
--
-- ── tb_member_dormant.member_name 은 제약을 걸지 않았다 ─────────────────────
-- 값은 같은 규칙으로 채운다(nullifyDormantPii 도 마스킹 값을 남긴다). 다만 NOT NULL
-- 제약까지 옮기지는 않았다 — 요청 범위가 tb_member 이고, 휴면 스냅샷은 원본이 아니라
-- 사본이라 "이름 없는 행" 의 의미가 다르다. 맞추려면 별도 결정이 필요하다.
-- ============================================================================

-- ① 원장에 남은 마스킹 이름으로 되메운다 (V11 이후 탈퇴 행)
UPDATE `tb_member` m
  JOIN `tb_member_withdraw` w ON w.`member_id` = m.`member_id`
   SET m.`member_name` = w.`member_name`
 WHERE m.`member_name` IS NULL
   AND w.`member_name` IS NOT NULL;

-- ② 남은 NULL — 되메울 값이 없는 행
UPDATE `tb_member`
   SET `member_name` = '-'
 WHERE `member_name` IS NULL;

-- ③ 제약 적용. 컬럼 타입·주석은 그대로 유지한다(varchar(150), '평문')
ALTER TABLE `tb_member`
    MODIFY COLUMN `member_name` varchar(150) NOT NULL COMMENT '회원 이름 (평문)';
