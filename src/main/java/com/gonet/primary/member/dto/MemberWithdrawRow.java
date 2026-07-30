package com.gonet.primary.member.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 탈퇴 원장 한 줄 — {@code tb_member_withdraw}.
 *
 * <p>여기서 <b>추가로 마스킹할 것은 없다</b>. 원장은 해시({@code login_id_hash},
 * {@code di_hash})와 <b>이미 마스킹된 이름</b>만 들고 있어 사람을 식별할 수 없다.
 * 재가입 제한·중복가입 차단·분쟁 대응에 쓰이는 대조용 값이다(PLAN §P10-7).
 */
@Getter
@Setter
public class MemberWithdrawRow {

    private String memberId;
    private String siteCode;
    /**
     * 마스킹된 이름(홍*동) — 저장 시점에 이미 가려진 값이다(V11).
     *
     * <p>다시 {@code Mask.name()} 을 거치게 하지 말 것. 별 문자를 또 가려 뜻이 사라진다.
     * 구 데이터는 원본이 이미 파기돼 NULL 이다.
     */
    private String memberName;
    private LocalDateTime withdrawAt;
    private String withdrawReason;
    /** 상태가 아니라 <b>유형</b>이다 — USER_REQUEST | ADMIN_FORCE | DORMANT_EXPIRED. */
    private String withdrawStatus;
    private LocalDateTime retentionExpireAt;
    private String legalBasis;
    private String diHash;

    /** 보존기간이 지나 완전 삭제 대상인가 — 배치가 지울 행이다. */
    public boolean isExpired() {
        return retentionExpireAt != null && retentionExpireAt.isBefore(LocalDateTime.now());
    }

    /** 해시는 통째로 보여 줄 이유가 없다 — 같은 값인지 대조할 만큼만. */
    public String getDiHashShort() {
        if (diHash == null || diHash.isBlank()) {
            return "-";
        }
        return diHash.length() <= 12 ? diHash : diHash.substring(0, 12) + "…";
    }
}
