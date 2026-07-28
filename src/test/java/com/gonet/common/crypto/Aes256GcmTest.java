package com.gonet.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** conventions §6 저장 형식 계약 — {AG} 프리픽스·IV 랜덤·이행기 평문 통과. */
class Aes256GcmTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final Aes256Gcm cipher = new Aes256Gcm(KEY);

    @Test
    @DisplayName("왕복: 암호화 후 복호화하면 원문이 나오고 저장값은 {AG} 로 시작한다")
    void roundTrip() {
        String encrypted = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(encrypted).startsWith(Aes256Gcm.PREFIX);
        assertThat(cipher.decrypt(encrypted)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    @DisplayName("같은 평문도 매번 다른 암호문 — IV 가 건당 난수(검색 불가의 근거)")
    void randomIv() {
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    @DisplayName("이미 암호문이면 다시 암호화하지 않는다 (이중 암호화 방지)")
    void doesNotDoubleEncrypt() {
        String once = cipher.encrypt("value");

        assertThat(cipher.encrypt(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("프리픽스 없는 값은 이행기 평문으로 간주해 그대로 통과")
    void plaintextPassthrough() {
        assertThat(cipher.decrypt("legacy-plain")).isEqualTo("legacy-plain");
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("마스터키 길이 오류는 기동 시점에 드러난다 (fail-fast)")
    void rejectsShortKey() {
        String shortKey = Base64.getEncoder().encodeToString("tooshort".getBytes());

        assertThatThrownBy(() -> new Aes256Gcm(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }
}
