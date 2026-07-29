package com.gonet.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 검색용 해시 규칙 고정 — 중복 검사가 이 정규화에 기대고 있다. */
class PiiHashTest {

    private static final String KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private final PiiHash hash = new PiiHash(KEY);

    @Test
    @DisplayName("같은 값은 같은 해시 — 정확 매칭 검색의 전제")
    void deterministic() {
        assertThat(hash.hash("user@example.com")).isEqualTo(hash.hash("user@example.com"));
        assertThat(hash.hash("user@example.com")).hasSize(64);
    }

    @Test
    @DisplayName("대소문자·공백을 정규화한다 — 안 하면 같은 이메일이 중복 검사를 통과한다")
    void normalizes() {
        String base = hash.hash("user@example.com");

        assertThat(hash.hash("User@Example.COM")).isEqualTo(base);
        assertThat(hash.hash("  user@example.com  ")).isEqualTo(base);
    }

    @Test
    @DisplayName("다른 값은 다른 해시")
    void distinct() {
        assertThat(hash.hash("a@example.com")).isNotEqualTo(hash.hash("b@example.com"));
    }

    @Test
    @DisplayName("키가 다르면 해시도 다르다 — 사전 대입을 막는 것이 HMAC 을 쓰는 이유")
    void keyMatters() {
        String other = Base64.getEncoder()
                .encodeToString("ffffffffffffffffffffffffffffffff".getBytes());

        assertThat(new PiiHash(other).hash("user@example.com"))
                .isNotEqualTo(hash.hash("user@example.com"));
    }

    @Test
    @DisplayName("빈 값은 null — 해시 컬럼도 비운다")
    void blankIsNull() {
        assertThat(hash.hash(null)).isNull();
        assertThat(hash.hash("   ")).isNull();
    }

    @Test
    @DisplayName("짧은 키는 기동 시점에 거부한다(fail-fast)")
    void rejectsShortKey() {
        String shortKey = Base64.getEncoder().encodeToString("tooshort".getBytes());

        assertThatThrownBy(() -> new PiiHash(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }
}
