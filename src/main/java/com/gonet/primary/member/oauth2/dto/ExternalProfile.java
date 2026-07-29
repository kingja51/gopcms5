package com.gonet.primary.member.oauth2.dto;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * provider userinfo 응답을 우리 모양으로 정규화한 값.
 *
 * <p>세션에 잠시 머무르므로 {@link Serializable} 이다. 이메일·이름은 평문이지만
 * 가입이 끝나면 즉시 세션에서 지운다 — 저장소에 쌓이지 않는다.
 *
 * <p>{@code providerUserId} 가 회원 식별의 유일한 기준이다. 이메일은 provider 에서
 * 바뀔 수 있고 사용자가 제공을 거부할 수도 있어 키로 쓸 수 없다.
 */
@Getter
@Setter
public class ExternalProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private OAuth2Provider provider;
    private String providerUserId;
    private String email;
    private String name;
    private String nickname;

    public boolean hasIdentity() {
        return provider != null && providerUserId != null && !providerUserId.isBlank();
    }
}
