package com.gonet.primary.member.oauth2.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.oauth2.OAuth2Properties;
import com.gonet.primary.member.oauth2.dto.ExternalProfile;
import com.gonet.primary.member.oauth2.dto.OAuth2Provider;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * RestClient 로 직접 도는 authorization_code 흐름.
 *
 * <p>세 provider 모두 골격은 같고 <b>userinfo 응답 구조만</b> 다르다. 그 차이를
 * {@link #parseProfile} 한 곳에 모아 두면 provider 추가가 분기 하나로 끝난다.
 *
 * <p>실패는 전부 {@link OAuth2Exception} 으로 모은다 — 컨트롤러가 provider 별 예외를
 * 알 필요가 없다.
 */
@Slf4j
@Service
public class OAuth2ServiceImpl extends AbstractCmsService implements OAuth2Service {

    private static final String CALLBACK_PATH = "/member/oauth2/%s/callback";

    private final OAuth2Properties props;
    private final RestClient client;

    public OAuth2ServiceImpl(OAuth2Properties props,
            @Qualifier("oauth2RestClient") RestClient client) {
        this.props = props;
        this.client = client;
    }

    @Override
    public boolean isConfigured(OAuth2Provider provider) {
        if (!props.isEnabled() || provider == null) {
            return false;
        }
        // 카카오는 secret 없이 쓰는 설정이 있어 client-id 만 본다
        return hasText(clientId(provider))
                && (provider == OAuth2Provider.KAKAO || hasText(clientSecret(provider)));
    }

    @Override
    public List<OAuth2Provider> configuredProviders() {
        List<OAuth2Provider> enabled = new ArrayList<>();
        for (OAuth2Provider provider : OAuth2Provider.values()) {
            if (isConfigured(provider)) {
                enabled.add(provider);
            }
        }
        return enabled;
    }

    @Override
    public String buildAuthorizeUrl(OAuth2Provider provider, String state, String redirectUri) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(provider.authorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId(provider))
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .queryParam("scope", provider.defaultScope());
        if (provider == OAuth2Provider.GOOGLE) {
            // 리프레시 토큰이 필요 없는 로그인 전용 흐름
            builder.queryParam("access_type", "online");
        }
        // encode() 없이 build(true) 를 쓰면 scope 의 공백에서 깨진다
        return builder.build().encode().toUriString();
    }

    @Override
    public String callbackUrl(OAuth2Provider provider) {
        String explicit = switch (provider) {
            case NAVER -> props.getNaver().getCallbackUrl();
            case KAKAO -> props.getKakao().getCallbackUrl();
            case GOOGLE -> props.getGoogle().getCallbackUrl();
        };
        if (hasText(explicit)) {
            return explicit;
        }
        String origin = props.getRedirectBaseUrl();
        if (!hasText(origin)) {
            throw new OAuth2Exception(
                    "gopcms.oauth2.redirect-base-url 또는 provider callback-url 이 필요합니다.");
        }
        return trimSlash(origin) + CALLBACK_PATH.formatted(provider.name().toLowerCase());
    }

    @Override
    public ExternalProfile exchangeAndFetchProfile(OAuth2Provider provider, String code,
            String redirectUri) {
        if (!isConfigured(provider)) {
            throw new OAuth2Exception("provider 자격이 설정되지 않았습니다: " + provider);
        }
        ExternalProfile profile = parseProfile(provider,
                fetchUserinfo(provider, exchangeToken(provider, code, redirectUri)));
        if (!profile.hasIdentity()) {
            throw new OAuth2Exception("provider 응답에 사용자 식별자가 없습니다: " + provider);
        }
        return profile;
    }

    /* ── provider 호출 ──────────────────────────────────────────────────── */

    private String exchangeToken(OAuth2Provider provider, String code, String redirectUri) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId(provider));
        body.add("redirect_uri", redirectUri);
        body.add("code", code);
        if (hasText(clientSecret(provider))) {
            body.add("client_secret", clientSecret(provider));
        }
        try {
            Map<?, ?> response = client.post()
                    .uri(provider.tokenUrl())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_FORM_URLENCODED_VALUE
                                    + ";charset=" + StandardCharsets.UTF_8.name())
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Object token = response == null ? null : response.get("access_token");
            if (token == null) {
                // 응답 본문에는 우리 client_secret 이 되비칠 수 있어 통째로 찍지 않는다
                throw new OAuth2Exception("access_token 이 응답에 없습니다: " + provider);
            }
            return String.valueOf(token);
        } catch (OAuth2Exception e) {
            throw e;
        } catch (Exception e) {
            log.warn("OAUTH2 토큰 교환 실패 provider={} reason={}", provider, e.getMessage());
            throw new OAuth2Exception("토큰 교환 실패: " + provider, e);
        }
    }

    private Map<?, ?> fetchUserinfo(OAuth2Provider provider, String accessToken) {
        try {
            Map<?, ?> response = client.get()
                    .uri(provider.userinfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(Map.class);
            if (response == null || response.isEmpty()) {
                throw new OAuth2Exception("userinfo 응답이 비어 있습니다: " + provider);
            }
            return response;
        } catch (OAuth2Exception e) {
            throw e;
        } catch (Exception e) {
            log.warn("OAUTH2 userinfo 실패 provider={} reason={}", provider, e.getMessage());
            throw new OAuth2Exception("사용자 정보 조회 실패: " + provider, e);
        }
    }

    /**
     * provider 별 응답 정규화 — 구조가 제각각이라 여기서 흡수한다.
     *
     * <p>카카오는 실명을 주지 않는다(비즈앱 심사 전). 그래서 이름 자리에 닉네임을 넣고,
     * 실명은 가입 절차의 본인인증(NICE)이 따로 채운다.
     */
    private ExternalProfile parseProfile(OAuth2Provider provider, Map<?, ?> body) {
        ExternalProfile profile = new ExternalProfile();
        profile.setProvider(provider);

        switch (provider) {
            case NAVER -> {
                // {"resultcode":"00","response":{"id":..,"email":..,"name":..}}
                if (body.get("response") instanceof Map<?, ?> response) {
                    profile.setProviderUserId(text(response.get("id")));
                    profile.setEmail(text(response.get("email")));
                    profile.setName(text(response.get("name")));
                    profile.setNickname(text(response.get("nickname")));
                }
            }
            case KAKAO -> {
                // {"id":123,"kakao_account":{"email":..,"profile":{"nickname":..}}}
                profile.setProviderUserId(text(body.get("id")));
                if (body.get("kakao_account") instanceof Map<?, ?> account) {
                    profile.setEmail(text(account.get("email")));
                    if (account.get("profile") instanceof Map<?, ?> detail) {
                        profile.setNickname(text(detail.get("nickname")));
                        profile.setName(text(detail.get("nickname")));
                    }
                }
            }
            case GOOGLE -> {
                // {"sub":..,"email":..,"name":..,"given_name":..}
                profile.setProviderUserId(text(body.get("sub")));
                profile.setEmail(text(body.get("email")));
                profile.setName(text(body.get("name")));
                profile.setNickname(text(body.get("given_name")));
            }
        }
        return profile;
    }

    /* ── helpers ────────────────────────────────────────────────────────── */

    private String clientId(OAuth2Provider provider) {
        return switch (provider) {
            case NAVER -> props.getNaver().getClientId();
            case KAKAO -> props.getKakao().getClientId();
            case GOOGLE -> props.getGoogle().getClientId();
        };
    }

    private String clientSecret(OAuth2Provider provider) {
        return switch (provider) {
            case NAVER -> props.getNaver().getClientSecret();
            case KAKAO -> props.getKakao().getClientSecret();
            case GOOGLE -> props.getGoogle().getClientSecret();
        };
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
