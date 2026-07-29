package com.gonet.primary.member.dto;

import com.gonet.common.util.Mask;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 목록 한 줄.
 *
 * <p>PII 는 <b>복호화된 평문</b>으로 담긴다(TypeHandler 를 거치므로). 화면·CSV 는
 * {@code masked*} 게터만 쓴다 — 평문 게터를 직접 부르는 곳이 곧 유출 지점이라
 * 코드에서 눈에 띄어야 한다.
 */
@Getter
@Setter
public class MemberAdmRow {

    private String memberId;
    private String siteId;
    private String siteCode;
    private String loginId;
    private String nickname;

    private String memberName;
    private String email;
    private String phone;
    private String birthYear;
    private String gender;

    private String status;
    private String joinType;
    private String emailVerifiedYn;
    /** DI 유무만 — 값 자체는 목록에 싣지 않는다. */
    private String diSetYn;

    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime lastAccessAt;
    private LocalDateTime createdAt;

    /* ── 화면·CSV 가 쓰는 표시값 ────────────────────────────────────────── */

    public String getMaskedName() {
        return Mask.name(memberName);
    }

    public String getMaskedEmail() {
        return Mask.email(email);
    }

    public String getMaskedPhone() {
        return Mask.phone(phone);
    }

    /** 잠금 중인가 — 해제 버튼 노출 판단. */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public boolean isVerified() {
        return "Y".equals(diSetYn);
    }
}
