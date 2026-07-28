package com.gonet.primary.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 비밀번호 구성 규칙 — 2종 10자 / 3종 8자 경계. */
class PasswordPolicyTest {

    @Test
    @DisplayName("3종 조합은 8자부터 통과")
    void threeKindsFromEight() {
        assertThat(PasswordPolicy.violation("Abc123!@")).isNull();      // 8자 4종
        assertThat(PasswordPolicy.violation("Abc123!")).isNotNull();    // 7자 — 부족
    }

    @Test
    @DisplayName("2종 조합은 10자부터 통과")
    void twoKindsFromTen() {
        assertThat(PasswordPolicy.violation("abcdefgh12")).isNull();    // 10자 2종
        assertThat(PasswordPolicy.violation("abcdefgh1")).isNotNull();  // 9자 2종 — 부족
    }

    @Test
    @DisplayName("한 종류만 쓰면 길어도 거부")
    void singleKindRejected() {
        assertThat(PasswordPolicy.violation("abcdefghijklmnop")).isNotNull();
    }

    @Test
    @DisplayName("빈 값은 안내 문구와 함께 거부")
    void blankRejected() {
        assertThat(PasswordPolicy.violation(null)).isNotNull();
        assertThat(PasswordPolicy.violation("   ")).isNotNull();
    }

    @Test
    @DisplayName("시드 계정 비밀번호(admin1234!)는 규칙을 만족한다")
    void seedPasswordPasses() {
        assertThat(PasswordPolicy.violation("admin1234!")).isNull();
    }
}
