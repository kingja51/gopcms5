package com.gonet.primary.dashboard.service;

import com.gonet.primary.dashboard.dto.DashboardStats;

/** 관리자 대시보드 집계 조회. */
public interface DashboardService {

    /** 화면 한 장에 필요한 수치 전부 — 카드·차트가 이 하나로 그려진다. */
    DashboardStats getStats();
}
