package com.gonet.primary.member.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 가입 마법사 진행 상태 — 세션에만 산다.
 *
 * <p><b>왜 폼이 아니라 세션인가</b>: 유형·약관 동의·본인인증 결과는 이전 단계에서
 * 서버가 확정한 값이다. 이걸 hidden 으로 실어 다음 단계로 넘기면 사용자가 값을 바꿔
 * "인증했다" 고 주장할 수 있다. 마지막 단계에서 저장되는 신원 정보는 전부 여기서 온다.
 *
 * <p>{@code siteId} 를 함께 들고 다니는 이유: 마법사 도중 다른 사이트로 이동하면
 * 동의·인증이 엉뚱한 사이트에 적용된다. 매 단계에서 현재 사이트와 대조해 어긋나면 버린다.
 *
 * <p>DI·이름 같은 강한 PII 가 담기므로 가입이 끝나면 즉시 세션에서 제거한다.
 */
@Getter
@Setter
public class JoinSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 세션 속성 키. */
    public static final String SESSION_KEY = "GOPCMS_JOIN_SESSION";

    public static final String TYPE_ADULT = "ADULT";
    /** 14세 미만 — 본인이 아니라 법정대리인이 인증한다. */
    public static final String TYPE_CHILD = "CHILD";

    /** 마법사가 시작된 사이트 — 단계마다 현재 사이트와 대조한다. */
    private String siteId;
    private String siteCode;

    /** ADULT | CHILD. */
    private String userType;

    /* ── STEP 2 약관 ────────────────────────────────────────────────────── */
    private boolean agreed;
    private String termsAgreeYn = "N";
    private String privacyAgreeYn = "N";
    private String marketingAgreeYn = "N";
    private String smsAgreeYn = "N";
    private String emailAgreeYn = "N";
    private LocalDateTime agreedAt;

    /* ── STEP 3 본인인증 ────────────────────────────────────────────────── */
    /** 인증을 마쳤는가. 본인인증을 끈 환경에서는 끝까지 false 로 남는다. */
    private boolean verified;
    /** NICE 가 돌려준 이름 — 사용자가 고칠 수 없는 값이라 그대로 저장한다. */
    private String verifiedName;
    private String verifiedBirthDate;
    /** NICE 성별(1=남,0=여)을 우리 코드(M/F/N)로 옮긴 값. */
    private String verifiedGender;
    private String verifiedMobile;
    private String di;
    private String diHash;

    /** CHILD 일 때만 채워진다 — 인증 주체가 법정대리인이다. */
    private String parentName;
    private String parentDi;
    private String parentDiHash;

    /* ── 소셜 가입 ──────────────────────────────────────────────────────── */
    /** NAVER | KAKAO | GOOGLE — 없으면 홈페이지 직접 가입. */
    private String oauthProvider;
    private String oauthUserId;
    private String oauthEmail;
    private String oauthName;

    /** 본인인증을 마쳤거나 애초에 요구하지 않는 흐름인가. */
    public boolean isIdentityStepDone(boolean identityRequired) {
        return !identityRequired || verified;
    }

    public boolean isChild() {
        return TYPE_CHILD.equals(userType);
    }

    public boolean hasOauth() {
        return oauthProvider != null && oauthUserId != null && !oauthUserId.isBlank();
    }
}
