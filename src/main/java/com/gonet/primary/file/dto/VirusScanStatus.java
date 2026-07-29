package com.gonet.primary.file.dto;

import java.util.Set;

/**
 * 백신 검사 상태 — V9 CHECK 제약의 6값과 1:1.
 *
 * <p>다운로드 허용 여부를 여기서 정한다. {@code PENDING} 을 허용하는 것은 백신 미연동
 * 운영을 전제로 한 판단이며(앞의 다중 방어는 이미 통과했다), 나머지 4종은 <b>결과가
 * 안전하다고 확인되지 않았다</b>는 뜻이므로 막는다 — 모르는 것은 열지 않는다.
 */
public final class VirusScanStatus {

    public static final String PENDING = "PENDING";
    public static final String CLEAN = "CLEAN";
    public static final String INFECTED = "INFECTED";
    public static final String ERROR = "ERROR";
    public static final String QUARANTINED = "QUARANTINED";
    public static final String RESCANNING = "RESCANNING";

    private static final Set<String> DOWNLOADABLE = Set.of(CLEAN, PENDING);

    /** 화면 선택지 겸 검증 집합 — V9 chk_file_scan_status 와 1:1. */
    public static final Set<String> ALL =
            Set.of(PENDING, CLEAN, INFECTED, ERROR, QUARANTINED, RESCANNING);

    private VirusScanStatus() {
    }

    /** V9 CHECK 제약이 허용하는 값인가 — 화면에서 임의 문자열이 들어오는 것을 막는다. */
    public static boolean isKnown(String status) {
        return status != null && ALL.contains(status);
    }

    /** 일반 사용자에게 내보내도 되는 상태인가. 관리자 강제 다운로드는 이 판정을 우회한다. */
    public static boolean isDownloadable(String status) {
        return status != null && DOWNLOADABLE.contains(status);
    }
}
