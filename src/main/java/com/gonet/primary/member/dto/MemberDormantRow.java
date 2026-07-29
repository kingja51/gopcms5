package com.gonet.primary.member.dto;

import com.gonet.common.util.Mask;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 휴면 현황 한 줄 — {@code tb_member_dormant}. */
@Getter
@Setter
public class MemberDormantRow {

    private String memberId;
    private String siteCode;
    private String loginId;
    private String memberName;
    private String email;

    private LocalDateTime dormantAt;
    private String dormantReason;
    /** 복원된 계정은 이 값이 찬다 — 이력이라 행은 남는다. */
    private LocalDateTime restoredAt;
    private LocalDateTime lastLoginAt;

    public String getMaskedName() {
        return Mask.name(memberName);
    }

    public String getMaskedEmail() {
        return Mask.email(email);
    }

    public boolean isRestored() {
        return restoredAt != null;
    }
}
