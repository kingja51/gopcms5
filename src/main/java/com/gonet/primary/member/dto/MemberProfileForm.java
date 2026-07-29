package com.gonet.primary.member.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 마이페이지 수정 폼 — <b>고칠 수 있는 것만</b> 담는다.
 *
 * <p>여기 없는 값은 화면에서 못 바꾼다: {@code loginId}(계정 식별자),
 * {@code birthDate}·{@code gender}·{@code di}·{@code parentDi}(본인확인 근간값).
 * 폼 DTO 에 아예 넣지 않는 것이 가장 확실한 차단이다 — 컨트롤러가 걸러 주기를
 * 기대하면 언젠가 빠뜨린다.
 */
@Getter
@Setter
public class MemberProfileForm {

    private String nickname;
    private String email;
    private String phone;

    private String addressZipcode;
    private String address;
    private String addressDetail;

    /** 선택 동의 — 마이페이지에서 언제든 바꿀 수 있어야 한다. */
    private String marketingAgreeYn;
    private String smsAgreeYn;
    private String emailAgreeYn;
}
