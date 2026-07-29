package com.gonet.primary.member.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 가입 폼 — 화면이 보내는 값만 담는다.
 *
 * <p>{@link MemberDto} 를 폼에 그대로 쓰지 않는 이유: 상태·역할·잠금 카운트처럼
 * <b>서버가 정하는 값</b>이 폼에 실려 오면 그대로 저장될 위험이 있다. 받을 것만 받는다.
 */
@Getter
@Setter
public class MemberJoinForm {

    private String siteCode;

    private String loginId;
    private String password;
    private String passwordConfirm;

    private String memberName;
    private String nickname;
    private String email;
    private String phone;
    private String birthDate;
    private String gender;

    private String addressZipcode;
    private String address;
    private String addressDetail;

    /** 필수 동의 — 둘 다 Y 여야 가입이 된다. */
    private String termsAgreeYn;
    private String privacyAgreeYn;
    /** 선택 동의. */
    private String marketingAgreeYn;
    private String smsAgreeYn;
    private String emailAgreeYn;
}
