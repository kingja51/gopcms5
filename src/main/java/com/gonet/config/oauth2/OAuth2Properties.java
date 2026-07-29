package com.gonet.config.oauth2;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 로그인 설정 — {@code application.yml} 의 {@code gopcms.oauth2.*}.
 *
 * <p>authorize/token/userinfo 엔드포인트는 코드 상수({@code OAuth2Provider})다. 거의
 * 바뀌지 않고, 바뀌면 응답 파싱도 함께 손봐야 해서 설정으로 빼도 얻는 게 없다.
 * 외부화하는 것은 <b>사이트마다 달라지는 값</b>인 client-id/secret/callback 뿐이다.
 *
 * <p>{@code callback-url} 은 provider 콘솔에 등록한 문자열과 <b>정확히</b> 같아야 한다.
 * 비워 두면 {@code redirect-base-url + /member/oauth2/{provider}/callback} 으로 만든다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.oauth2")
public class OAuth2Properties {

    /** 전역 토글 — false 면 로그인 화면에 소셜 버튼이 아예 나오지 않는다. */
    private boolean enabled = true;

    /** 콜백 URL 의 origin (예: {@code https://cms.example.go.kr}). */
    private String redirectBaseUrl = "";

    /** 연결/응답 타임아웃(ms) — provider 지연이 우리 스레드를 잡아 두지 않게 짧게 둔다. */
    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 10_000;

    private Client naver = new Client();
    private Client kakao = new Client();
    private Client google = new Client();

    /**
     * provider 공통 자격.
     *
     * <p>카카오는 secret 없이도 동작하는 설정이 있어 {@code clientSecret} 은 선택이다.
     */
    @Getter
    @Setter
    public static class Client {
        private String clientId = "";
        private String clientSecret = "";
        private String callbackUrl = "";
    }
}
