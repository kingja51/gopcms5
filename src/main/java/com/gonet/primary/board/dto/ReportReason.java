package com.gonet.primary.board.dto;

import java.util.List;

/**
 * 신고 사유 — V9 {@code chk_report_reason} 6값과 1:1.
 *
 * <p>공통코드({@code BBS_REPORT_REASON})에도 같은 값이 있다. 화면 표기는 코드 테이블이,
 * <b>검증은 이 목록이</b> 담당한다 — 코드 테이블은 운영자가 지울 수 있어 검증 근거로 쓰기엔
 * 불안정하고, DB CHECK 와 어긋나면 저장 시점에 500 이 난다.
 */
public final class ReportReason {

    public static final List<String> ALL =
            List.of("SPAM", "OFFENSIVE", "ILLEGAL", "COPYRIGHT", "PRIVACY", "OTHER");

    private ReportReason() {
    }

    public static boolean isValid(String value) {
        return value != null && ALL.contains(value);
    }

    public static String label(String code) {
        return switch (code == null ? "" : code) {
            case "SPAM" -> "스팸/광고";
            case "OFFENSIVE" -> "불쾌/혐오 표현";
            case "ILLEGAL" -> "불법 콘텐츠";
            case "COPYRIGHT" -> "저작권 침해";
            case "PRIVACY" -> "개인정보 노출";
            case "OTHER" -> "기타";
            default -> code;
        };
    }
}
