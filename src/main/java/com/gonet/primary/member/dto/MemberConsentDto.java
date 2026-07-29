package com.gonet.primary.member.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 약관 동의 이력 — <b>UPDATE 하지 않고 쌓는다</b>.
 *
 * <p>동의는 "현재 상태" 가 아니라 "언제 무엇에 어떤 버전으로 동의했는가" 가 증빙이다.
 * 최신 값만 남기면 철회·재동의 이력이 사라져 분쟁 때 근거가 없다.
 */
@Getter
@Setter
public class MemberConsentDto {

    private String memberConsentId;
    private String memberId;
    /** TERMS | PRIVACY | MARKETING | SMS | EMAIL | THIRD_PARTY */
    private String consentType;
    private String consentVersion;
    private String agreeYn;
    private LocalDateTime agreedAt;
    private String clientIp;
    private String userAgent;
    private String createdBy;
    private String createdIp;
}
