package com.gonet.primary.member.dto;

import com.gonet.common.audit.Auditable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 인증번호 — 코드 원문은 담지 않는다(해시만).
 *
 * <p>관리자 2FA(TOTP)와 다르다: TOTP 는 앱이 시간으로 만들고 서버는 시크릿만 갖지만,
 * 이 OTP 는 서버가 만들어 메일로 보내고 한 번 쓰면 끝난다.
 */
@Getter
@Setter
public class MemberOtp extends Auditable {

    /** 용도 — 교차 사용을 막는다(휴면 복원 코드로 이메일 인증을 통과하면 안 된다). */
    public static final String PURPOSE_DORMANT_RESTORE = "DORMANT_RESTORE";
    public static final String PURPOSE_EMAIL_VERIFY = "EMAIL_VERIFY";

    private String otpId;
    private String memberId;
    private String siteId;
    private String purpose;
    private String codeHash;
    private LocalDateTime expiresAt;
    private Integer attemptCount;
    private LocalDateTime verifiedAt;
    private String clientIp;
    private LocalDateTime createdAtView;
}
