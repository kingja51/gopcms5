package com.gonet.primary.member.oauth2.dto;

import com.gonet.common.audit.Auditable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** {@code tb_member_oauth} — 회원과 외부 계정의 연결. */
@Getter
@Setter
public class MemberOAuthDto extends Auditable {

    private String memberOauthId;
    private String memberId;
    /** NAVER | KAKAO | GOOGLE. */
    private String provider;
    private String providerUserId;

    /**
     * 연결 시점의 provider 이메일·이름 — 감사용 스냅샷이다.
     *
     * <p>회원 식별에는 쓰지 않는다. provider 쪽에서 값이 바뀌어도 여기는 그대로 두어야
     * "연결할 때 어떤 계정이었는지" 를 나중에 되짚을 수 있다.
     */
    private String emailAtLink;
    private String nameAtLink;

    private LocalDateTime linkedAt;
    private LocalDateTime lastLoginAt;
    private String useYn;
    private String deleteYn;
}
