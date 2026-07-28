package com.gonet.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TOTP 검증 계약 — 인증 앱이 만들 코드와 같은 값을 받아들이는지. */
class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    @Test
    @DisplayName("현재 시각 코드는 통과, 틀린 코드는 거부")
    void verifiesCurrentCode() {
        String secret = totpService.newSecret();
        int current = new GoogleAuthenticator().getTotpPassword(secret);

        assertThat(totpService.verify(secret, String.valueOf(current))).isTrue();
        assertThat(totpService.verify(secret, "000000")).isFalse();
    }

    @Test
    @DisplayName("빈 값·숫자 아닌 입력은 예외 없이 검증 실패로 처리")
    void rejectsMalformedInput() {
        String secret = totpService.newSecret();

        assertThat(totpService.verify(secret, null)).isFalse();
        assertThat(totpService.verify(secret, "  ")).isFalse();
        assertThat(totpService.verify(secret, "abcdef")).isFalse();
        assertThat(totpService.verify(null, "123456")).isFalse();
    }

    @Test
    @DisplayName("otpauth URI 는 issuer·secret 을 담고, QR 은 외부 요청 없는 data: URI")
    void buildsEnrollmentArtifacts() {
        String secret = totpService.newSecret();
        String uri = totpService.otpAuthUri("admin", secret);

        assertThat(uri).startsWith("otpauth://totp/")
                .contains("secret=" + secret).contains("issuer=GOPCMS");
        assertThat(totpService.qrDataUri(uri)).startsWith("data:image/png;base64,");
    }
}
