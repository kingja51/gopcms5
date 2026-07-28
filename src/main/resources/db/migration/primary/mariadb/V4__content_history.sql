-- ============================================================================
-- V4 — 콘텐츠 버전 이력 (tb_content_history)
-- ----------------------------------------------------------------------------
-- · 불변 스냅샷(insert-only) — updated_* 3종은 의도적으로 제외(감사 6종 규약의 예외).
-- · PK 접두어 CNH 신규 등록 (conventions.md §2 — Uid.next(UidPrefix.CNH)).
-- · PAGE_COMPRESSED=1 — mediumtext 스냅샷 누적 대비 MariaDB 페이지 압축.
-- · 기록 시점: 관리자 컨텐츠 수정 저장 시 서비스가 "변경 전" 스냅샷 insert +
--   tb_content.version_no 증가 (관리자 모듈 페이즈에서 구현).
-- ============================================================================
CREATE TABLE `tb_content_history` (
  `content_history_id` varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '이력 ID (CNH_ + UUIDv7)',
  `content_id`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '콘텐츠 ID',
  `version_no`         int(11)      NOT NULL COMMENT '버전 번호 (tb_content.version_no 스냅샷)',
  `title`              varchar(300) NOT NULL COMMENT '제목 스냅샷',
  `body`               mediumtext   DEFAULT NULL COMMENT '본문 스냅샷',
  `summary`            varchar(1000) DEFAULT NULL COMMENT '요약 스냅샷',
  `changed_by`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '변경 수행자 (ADM_… — 목록 조회 편의 명시 컬럼)',
  `change_note`        varchar(500) DEFAULT NULL COMMENT '변경 사유 메모',
  `created_by`         varchar(40)  CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '생성자 ID',
  `created_ip`         varchar(50)  DEFAULT NULL COMMENT '생성자 IP',
  `created_at`         timestamp    NULL DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`content_history_id`),
  UNIQUE KEY `uk_content_hst` (`content_id`,`version_no`),
  CONSTRAINT `fk_content_hst` FOREIGN KEY (`content_id`) REFERENCES `tb_content` (`content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci
  PAGE_COMPRESSED=1 COMMENT='콘텐츠 버전 이력 (불변 스냅샷 · 페이지 압축)';
