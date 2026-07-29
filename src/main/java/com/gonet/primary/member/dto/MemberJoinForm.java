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

    // 약관 동의는 여기 없다 — 마법사 STEP 2 에서 받아 세션({@link JoinSession})이 들고 있다.
    // 마지막 폼에 동의 값을 다시 실으면 앞 단계에서 확정한 것을 뒤에서 뒤집을 수 있다.
}
