package com.gonet.primary.dashboard.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 대시보드 집계값 묶음 — 화면 한 장을 채우는 데 필요한 수치의 전부.
 *
 * <p>도메인 코드(회원·게시판·파일)는 아직 페이즈가 남아 있지만 <b>테이블은 V9 로 이미 있다</b>.
 * 그래서 이 화면은 서비스 계층을 거치지 않고 집계 쿼리만으로 성립한다 — 도메인이 구현되면
 * 카드/차트는 그대로 두고 뒤의 쿼리만 갈아끼우면 된다.
 *
 * <p>차트용 시계열은 {@link Bucket} 리스트로 통일한다. 라벨·값 두 축이면 막대든 선이든
 * 도넛이든 Chart.js 쪽에서 같은 형태로 받는다 — 차트 종류마다 DTO 를 늘리지 않으려는 것.
 */
@Getter
@Setter
public class DashboardStats {

    /** 차트 한 칸 — 라벨과 값. 월별 추이·상태 분포·TOP N 이 모두 이 형태로 온다. */
    @Getter
    @Setter
    public static class Bucket {
        private String label;
        private long value;
    }

    /* ── 회원 ─────────────────────────────────────────────────────────── */
    private long memberTotal;
    private long memberActive;
    private long memberLocked;
    private long memberSuspended;
    private long memberDormant;
    private long memberWithdraw;
    /** 최근 12개월 가입 추이 (YYYY-MM). */
    private List<Bucket> memberJoinTrend;
    /** 상태 분포 — 도넛. */
    private List<Bucket> memberStatus;

    /* ── 게시판 ───────────────────────────────────────────────────────── */
    private long boardCount;
    private long articleTotal;
    private long commentTotal;
    /** 게시판별 글 수 상위 — 가로 막대. */
    private List<Bucket> articleByBoard;
    /** 최근 12개월 게시글 추이. */
    private List<Bucket> articleTrend;

    /* ── 파일 ─────────────────────────────────────────────────────────── */
    private long fileTotal;
    /** 총 용량(바이트) — 화면에서 MB/GB 로 환산. */
    private long fileBytes;
    /** 확장자 상위. */
    private List<Bucket> fileByExtension;
    /** 백신 검사 상태 분포 — 운영자가 INFECTED/ERROR 를 놓치지 않게 한다. */
    private List<Bucket> fileByScanStatus;

    /* ── 컨텐츠 ───────────────────────────────────────────────────────── */
    private List<Bucket> contentStatus;
}
