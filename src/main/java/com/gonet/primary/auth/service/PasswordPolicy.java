package com.gonet.primary.auth.service;

/**
 * 비밀번호 구성 규칙 — 공공기관 통상 기준(2종 조합 10자 이상 / 3종 조합 8자 이상).
 *
 * <p>순수 함수라 서비스·검증기·테스트가 같은 판정을 공유한다. 문자 종류는
 * 영문 대문자·소문자·숫자·특수문자 4종으로 센다.
 */
public final class PasswordPolicy {

    /** 비밀번호 유효기간(일) — 만료 시 로그인 거부, 재설정 필요. */
    public static final int VALID_DAYS = 90;

    /** 재사용 금지 대상 이력 개수(직전 N개). */
    public static final int HISTORY_DEPTH = 3;

    private PasswordPolicy() {
        // 유틸리티 클래스 인스턴스화 방지
    }

    /** 규칙 위반 사유. 만족하면 null. */
    public static String violation(String raw) {
        if (raw == null || raw.isBlank()) {
            return "비밀번호를 입력해 주세요.";
        }
        int kinds = kinds(raw);
        if (raw.length() >= 10 && kinds >= 2) {
            return null;
        }
        if (raw.length() >= 8 && kinds >= 3) {
            return null;
        }
        return "비밀번호는 영문·숫자·특수문자 중 2종류 조합 10자 이상,"
                + " 또는 3종류 조합 8자 이상이어야 합니다.";
    }

    private static int kinds(String raw) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (char c : raw.toCharArray()) {
            if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else {
                special = true;
            }
        }
        return (upper ? 1 : 0) + (lower ? 1 : 0) + (digit ? 1 : 0) + (special ? 1 : 0);
    }
}
