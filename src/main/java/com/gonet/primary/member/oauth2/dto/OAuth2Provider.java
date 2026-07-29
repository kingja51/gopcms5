package com.gonet.primary.member.oauth2.dto;

import java.util.Locale;

/**
 * 소셜 로그인 제공자 — 엔드포인트·스코프·표시명 묶음.
 *
 * <p>enum 이름이 곧 {@code tb_member_oauth.provider} 값이고
 * {@code tb_member.join_type} 값이다. CHECK 제약이 NAVER/KAKAO/GOOGLE 을 그대로 받으니
 * 변환표를 따로 두지 않는다 — 표가 생기면 언젠가 한쪽만 고쳐진다.
 *
 * <p>provider 추가 절차:
 * <ol>
 *   <li>여기에 항목 + 엔드포인트 추가</li>
 *   <li>{@code OAuth2Properties} 에 자격 필드 추가</li>
 *   <li>{@code OAuth2ServiceImpl.parseProfile()} 에 응답 파싱 분기 추가(구조가 제각각이다)</li>
 *   <li>{@code tb_member_oauth} / {@code tb_member} 의 CHECK 제약을 마이그레이션으로 확장</li>
 * </ol>
 */
public enum OAuth2Provider {

    NAVER("네이버",
            "https://nid.naver.com/oauth2.0/authorize",
            "https://nid.naver.com/oauth2.0/token",
            "https://openapi.naver.com/v1/nid/me",
            "name email"),

    KAKAO("카카오",
            "https://kauth.kakao.com/oauth/authorize",
            "https://kauth.kakao.com/oauth/token",
            "https://kapi.kakao.com/v2/user/me",
            "profile_nickname account_email"),

    GOOGLE("구글",
            "https://accounts.google.com/o/oauth2/v2/auth",
            "https://oauth2.googleapis.com/token",
            "https://www.googleapis.com/oauth2/v3/userinfo",
            "openid email profile");

    private final String label;
    private final String authorizeUrl;
    private final String tokenUrl;
    private final String userinfoUrl;
    private final String defaultScope;

    OAuth2Provider(String label, String authorizeUrl, String tokenUrl,
            String userinfoUrl, String defaultScope) {
        this.label = label;
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.userinfoUrl = userinfoUrl;
        this.defaultScope = defaultScope;
    }

    public String label() {
        return label;
    }

    public String authorizeUrl() {
        return authorizeUrl;
    }

    public String tokenUrl() {
        return tokenUrl;
    }

    public String userinfoUrl() {
        return userinfoUrl;
    }

    public String defaultScope() {
        return defaultScope;
    }

    /** 경로 변수로 들어온 값을 안전하게 변환 — 모르는 값이면 null(예외 대신). */
    public static OAuth2Provider fromCode(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
