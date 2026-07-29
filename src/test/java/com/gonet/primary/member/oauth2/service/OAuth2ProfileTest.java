package com.gonet.primary.member.oauth2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.gonet.config.oauth2.OAuth2Properties;
import com.gonet.primary.member.oauth2.dto.ExternalProfile;
import com.gonet.primary.member.oauth2.dto.OAuth2Provider;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * provider 응답 정규화와 인가 URL 조립.
 *
 * <p>세 provider 는 userinfo 구조가 제각각이고, 그 차이를 흡수하는 곳이 한 군데뿐이라
 * 여기가 깨지면 "로그인은 되는데 아무 정보도 안 들어오는" 상태가 된다. 실제 응답 모양을
 * 그대로 박아 두어 provider 추가 시 기존 분기가 망가지는 것을 잡는다.
 */
class OAuth2ProfileTest {

    private final OAuth2Properties props = new OAuth2Properties();
    private final OAuth2ServiceImpl service =
            new OAuth2ServiceImpl(props, mock(RestClient.class));

    private ExternalProfile parse(OAuth2Provider provider, Map<String, Object> body)
            throws Exception {
        Method method = OAuth2ServiceImpl.class
                .getDeclaredMethod("parseProfile", OAuth2Provider.class, Map.class);
        method.setAccessible(true);
        return (ExternalProfile) method.invoke(service, provider, body);
    }

    @Test
    @DisplayName("네이버 — 식별자·이름·이메일이 response 하위에 있다")
    void naver() throws Exception {
        ExternalProfile profile = parse(OAuth2Provider.NAVER, Map.of(
                "resultcode", "00",
                "response", Map.of("id", "abc123", "email", "a@b.kr", "name", "홍길동")));

        assertThat(profile.getProviderUserId()).isEqualTo("abc123");
        assertThat(profile.getEmail()).isEqualTo("a@b.kr");
        assertThat(profile.getName()).isEqualTo("홍길동");
        assertThat(profile.hasIdentity()).isTrue();
    }

    @Test
    @DisplayName("카카오 — id 는 숫자로 오고, 실명이 없어 닉네임이 이름 자리를 대신한다")
    void kakao() throws Exception {
        ExternalProfile profile = parse(OAuth2Provider.KAKAO, Map.of(
                "id", 1234567890L,
                "kakao_account", Map.of(
                        "email", "k@b.kr",
                        "profile", Map.of("nickname", "길동이"))));

        // 숫자 그대로 두면 varchar 컬럼에 "1.23456789E9" 같은 값이 들어간다
        assertThat(profile.getProviderUserId()).isEqualTo("1234567890");
        assertThat(profile.getName()).isEqualTo("길동이");
        assertThat(profile.getNickname()).isEqualTo("길동이");
    }

    @Test
    @DisplayName("구글 — 식별자는 sub 다(email 이 아니다)")
    void google() throws Exception {
        ExternalProfile profile = parse(OAuth2Provider.GOOGLE, Map.of(
                "sub", "10987654321",
                "email", "g@b.kr",
                "name", "Gil Dong",
                "given_name", "Gil"));

        assertThat(profile.getProviderUserId()).isEqualTo("10987654321");
        assertThat(profile.getEmail()).isEqualTo("g@b.kr");
    }

    @Test
    @DisplayName("식별자가 없는 응답은 신원으로 인정하지 않는다")
    void missingIdentity() throws Exception {
        ExternalProfile profile = parse(OAuth2Provider.NAVER,
                Map.of("resultcode", "024", "message", "인증 실패"));

        assertThat(profile.hasIdentity()).isFalse();
    }

    @Test
    @DisplayName("scope 의 공백은 percent-encoding 된다 — 인코딩을 빼면 provider 가 거절한다")
    void authorizeUrlEncodesScope() {
        props.getGoogle().setClientId("id");
        props.getGoogle().setClientSecret("secret");

        String url = service.buildAuthorizeUrl(OAuth2Provider.GOOGLE, "st4te",
                "https://cms.example.go.kr/member/oauth2/google/callback");

        assertThat(url).contains("scope=openid%20email%20profile");
        assertThat(url).contains("state=st4te");
        assertThat(url).contains("response_type=code");
    }

    @Test
    @DisplayName("자격 판정 — 카카오만 secret 없이도 설정된 것으로 본다")
    void configured() {
        props.getNaver().setClientId("id");
        assertThat(service.isConfigured(OAuth2Provider.NAVER)).isFalse();
        props.getNaver().setClientSecret("secret");
        assertThat(service.isConfigured(OAuth2Provider.NAVER)).isTrue();

        props.getKakao().setClientId("id");
        assertThat(service.isConfigured(OAuth2Provider.KAKAO)).isTrue();

        // 전역 토글이 내려가면 자격이 있어도 쓰지 않는다
        props.setEnabled(false);
        assertThat(service.configuredProviders()).isEmpty();
    }

    @Test
    @DisplayName("콜백 URL 근거가 없으면 조용히 넘어가지 않고 실패한다")
    void callbackUrlRequiresConfig() {
        assertThatThrownBy(() -> service.callbackUrl(OAuth2Provider.NAVER))
                .isInstanceOf(OAuth2Exception.class)
                .hasMessageContaining("redirect-base-url");
    }

    @Test
    @DisplayName("모르는 provider 코드는 예외가 아니라 null — 경로 변수는 사용자 입력이다")
    void unknownProviderCode() {
        assertThat(OAuth2Provider.fromCode("bogus")).isNull();
        assertThat(OAuth2Provider.fromCode(null)).isNull();
        assertThat(OAuth2Provider.fromCode(" naver ")).isEqualTo(OAuth2Provider.NAVER);
    }
}
