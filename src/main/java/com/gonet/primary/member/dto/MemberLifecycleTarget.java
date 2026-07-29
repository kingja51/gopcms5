package com.gonet.primary.member.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 생명주기 배치의 처리 대상 — 판단과 안내에 필요한 최소값만 담는다.
 *
 * <p>PII 를 통째로 들고 다니지 않는다. 배치 로그·큐에 개인정보가 실리면 그 자체가
 * 보관처가 된다. 메일 발송에 필요한 이름·이메일만 복호화해 담는다.
 */
@Getter
@Setter
public class MemberLifecycleTarget {

    private String memberId;
    private String siteCode;
    private String loginId;
    private String memberName;
    private String email;
    /** 기준 시각 — 휴면은 마지막 로그인(없으면 가입일), 탈퇴는 휴면 전환일. */
    private LocalDateTime baseAt;
}
