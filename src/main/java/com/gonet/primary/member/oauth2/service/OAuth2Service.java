package com.gonet.primary.member.oauth2.service;

import com.gonet.primary.member.oauth2.dto.ExternalProfile;
import com.gonet.primary.member.oauth2.dto.OAuth2Provider;
import java.util.List;

/**
 * provider 와 주고받는 부분만 담당한다 — authorization_code 를 토큰으로 바꾸고,
 * userinfo 를 우리 모양({@link ExternalProfile})으로 정규화한다.
 *
 * <p>회원 연결·가입은 {@link MemberOAuthService} 가, state 발급·검증과 리다이렉트는
 * 컨트롤러가 맡는다. 이 경계 덕분에 provider 가 늘어도 회원 도메인은 그대로다.
 */
public interface OAuth2Service {

    /** 자격이 들어와 실제로 쓸 수 있는 provider 인가 — 화면 버튼 노출 판단에도 쓴다. */
    boolean isConfigured(OAuth2Provider provider);

    /** 지금 쓸 수 있는 provider 목록 — 로그인 화면이 이 목록만 그린다. */
    List<OAuth2Provider> configuredProviders();

    /** 사용자를 보낼 인가 URL — state 와 redirect_uri 를 포함한다. */
    String buildAuthorizeUrl(OAuth2Provider provider, String state, String redirectUri);

    /**
     * 콜백 코드 → 토큰 → userinfo → 정규화까지 한 번에.
     *
     * @throws OAuth2Exception 교환 실패·응답 파싱 실패·식별자 누락
     */
    ExternalProfile exchangeAndFetchProfile(OAuth2Provider provider, String code,
            String redirectUri);

    /**
     * provider 콜백 URL — 콘솔에 등록한 값이 있으면 그걸 그대로 쓴다.
     *
     * <p>인가 요청과 토큰 교환에서 <b>같은 문자열</b>이어야 한다. 한쪽만 달라도
     * provider 가 {@code redirect_uri_mismatch} 로 거절한다.
     */
    String callbackUrl(OAuth2Provider provider);
}
