package com.gonet.primary.member.oauth2.service;

/**
 * 소셜 로그인 처리 실패 — 토큰 교환·userinfo 조회·응답 파싱을 하나로 묶는다.
 *
 * <p>메시지는 <b>로그용</b>이다. 컨트롤러는 이걸 그대로 사용자에게 보여 주지 않고
 * 정해진 안내 문구로 바꾼다 — provider 응답 원문에는 우리 자격 정보가 섞여 나올 수 있다.
 */
public class OAuth2Exception extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OAuth2Exception(String message) {
        super(message);
    }

    public OAuth2Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
