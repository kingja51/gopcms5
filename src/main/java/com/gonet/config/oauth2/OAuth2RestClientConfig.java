package com.gonet.config.oauth2;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 소셜 로그인 전용 {@link RestClient}.
 *
 * <p>빈 이름을 한정하는 이유: 타임아웃이 이 용도에 맞춰져 있어서 다른 외부 호출이
 * 실수로 이 설정을 물려받으면 안 된다. 주입할 때 {@code @Qualifier("oauth2RestClient")}.
 *
 * <p>baseUrl 을 두지 않는다 — provider 마다 호스트가 다르므로 절대 URL 로 호출한다.
 */
@Configuration
public class OAuth2RestClientConfig {

    @Bean
    public RestClient oauth2RestClient(OAuth2Properties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        return RestClient.builder().requestFactory(factory).build();
    }
}
