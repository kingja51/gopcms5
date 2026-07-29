package com.gonet.common.util;

/**
 * 개인정보 마스킹 — 관리자 화면의 <b>기본 표시</b> 형태.
 *
 * <p>왜 화면 단이 아니라 여기인가: 마스킹을 뷰에서 하면 CSV·JSON 응답처럼 뷰를 안 타는
 * 출구가 생길 때마다 빠뜨린다. 값을 문자열로 바꾸는 단 하나의 함수를 두고 모든 출구가
 * 그것을 거치게 한다.
 *
 * <p>되돌릴 수 없는 변환이다 — 마스킹된 값에서 원본을 얻는 경로는 없다. 원본이 필요하면
 * 사유를 남기고 별도 경로로 조회한다(개인정보 접근 이력에 남는다).
 *
 * <p>가리는 정도는 "누가 누구인지 알아볼 수 없되 담당자가 같은 사람인지 대조할 수는
 * 있는" 선에 맞춘다. 전부 가리면 화면이 쓸모없어지고, 덜 가리면 마스킹이 무의미하다.
 */
public final class Mask {

    private static final String EMPTY = "-";

    private Mask() {
    }

    /**
     * 이름 — 가운데를 가린다. 홍길동 → 홍*동, 김철 → 김*, 박 → 박.
     *
     * <p>두 글자는 뒤를 가린다(가운데가 없다). 한 글자는 가릴 곳이 없어 그대로 둔다.
     */
    public static String name(String value) {
        if (isBlank(value)) {
            return EMPTY;
        }
        String trimmed = value.trim();
        int length = trimmed.length();
        if (length == 1) {
            return trimmed;
        }
        if (length == 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.charAt(0) + "*".repeat(length - 2) + trimmed.charAt(length - 1);
    }

    /**
     * 이메일 — 로컬파트 앞 2글자만 남긴다. {@code hong@ex.kr} → {@code ho**@ex.kr}.
     *
     * <p>도메인은 남긴다. 도메인까지 가리면 소속 기관을 구분할 수 없어 문의 응대가 막힌다.
     */
    public static String email(String value) {
        if (isBlank(value)) {
            return EMPTY;
        }
        String trimmed = value.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0) {
            // @ 가 없으면 이메일이 아니다 — 일반 문자열 규칙으로 가린다
            return name(trimmed);
        }
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "*" + domain;
        }
        return local.substring(0, 2) + "*".repeat(local.length() - 2) + domain;
    }

    /**
     * 전화번호 — 가운데 자리를 가린다. {@code 01012345678} → {@code 010-****-5678}.
     *
     * <p>뒤 4자리를 남기는 이유: 본인 확인 문의에서 "뒷자리 4개" 를 묻는 관행이 있고,
     * 그걸 못 보면 담당자가 화면을 두고도 확인을 못 한다.
     */
    public static String phone(String value) {
        if (isBlank(value)) {
            return EMPTY;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return "*".repeat(Math.max(digits.length(), 1));
        }
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return head + "-****-" + tail;
    }

    /** 생년월일 — 연도만 남긴다. {@code 19900101} → {@code 1990-**-**}. */
    public static String birthDate(String value) {
        if (isBlank(value)) {
            return EMPTY;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.length() < 4 ? "****" : digits.substring(0, 4) + "-**-**";
    }

    /** 주소 — 앞 10글자만. 시·군·구까지는 보이되 번지는 가린다. */
    public static String address(String value) {
        if (isBlank(value)) {
            return EMPTY;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 10) {
            return trimmed;
        }
        // 자른 자리가 공백이면 "종로구 …" 처럼 보인다 — 끝 공백을 털고 말줄임을 붙인다
        return trimmed.substring(0, 10).stripTrailing() + "…";
    }

    /**
     * DI 같은 불투명 식별자 — 있고 없음만 알린다.
     *
     * <p>앞 몇 자를 보여 주는 관행이 있지만 DI 는 그 자체가 전 기관 공통 식별자라
     * 부분값도 대조 재료가 된다. 존재 여부만으로 충분하다.
     */
    public static String token(String value) {
        return isBlank(value) ? EMPTY : "(설정됨)";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
