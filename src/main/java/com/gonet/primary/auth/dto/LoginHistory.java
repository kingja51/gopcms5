package com.gonet.primary.auth.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * tb_login_history 1건 — 인증 사건 기록(insert-only).
 *
 * <p>{@code result} 는 운영자가 보는 진짜 사유다. 사용자에게 돌려주는 메시지는 언제나
 * 일반화("아이디 또는 비밀번호가 올바르지 않습니다") — 계정 존재 여부·IP 등록 여부가
 * 응답 차이로 새면 그 자체가 정보 노출이다.
 */
@Getter
@Setter
@Builder
// MyBatis 결과 매핑에는 무인자 생성자가 필요하다 — @Builder 만 두면 전인자 생성자만 남아
// 생성자 auto-mapping 으로 넘어가고, 컬럼 수가 다르면 조회가 실패한다(실측).
@NoArgsConstructor
@AllArgsConstructor
public class LoginHistory {

    /** SUCCESS | FAIL_NOT_FOUND | FAIL_PASSWORD | FAIL_LOCKED | FAIL_DISABLED | FAIL_IP | FAIL_2FA | FAIL_CAPTCHA */
    public static final String SUCCESS = "SUCCESS";
    public static final String FAIL_NOT_FOUND = "FAIL_NOT_FOUND";
    public static final String FAIL_PASSWORD = "FAIL_PASSWORD";
    public static final String FAIL_LOCKED = "FAIL_LOCKED";
    public static final String FAIL_DISABLED = "FAIL_DISABLED";
    public static final String FAIL_IP = "FAIL_IP";
    public static final String FAIL_2FA = "FAIL_2FA";
    public static final String FAIL_CAPTCHA = "FAIL_CAPTCHA";
    public static final String FAIL_EXPIRED = "FAIL_EXPIRED";

    private String loginHistoryId;
    private String userType;
    private String userId;
    private String loginId;
    private String siteId;
    private String siteCode;
    private String result;
    private String failReason;
    private String clientIp;
    private String userAgent;
    private String sessionId;

    /** 시도 시각 — 적재는 DB DEFAULT 가 채우고, 조회(직전 로그인 표시) 때만 쓴다. */
    private LocalDateTime attemptedAt;
}
