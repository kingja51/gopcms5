package com.gonet.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 마스킹 규칙.
 *
 * <p>관리자 화면·CSV 가 모두 이 함수를 지나므로, 여기가 느슨해지면 개인정보가 여러 출구로
 * 한꺼번에 샌다. 특히 짧은 값(한 글자 이름, 두 글자 로컬파트)에서 <b>가릴 곳이 없어
 * 원본이 그대로 나가는</b> 경우를 고정한다.
 */
class MaskTest {

    @Test
    @DisplayName("이름 — 가운데를 가리고, 두 글자는 뒤를 가린다")
    void name() {
        assertThat(Mask.name("홍길동")).isEqualTo("홍*동");
        assertThat(Mask.name("남궁민수")).isEqualTo("남**수");
        assertThat(Mask.name("김철")).isEqualTo("김*");
        // 한 글자는 가릴 자리가 없다 — 별표로 바꾸면 아무 정보도 남지 않아 화면이 무의미해진다
        assertThat(Mask.name("박")).isEqualTo("박");
        assertThat(Mask.name(null)).isEqualTo("-");
        assertThat(Mask.name("  ")).isEqualTo("-");
    }

    @Test
    @DisplayName("이메일 — 로컬파트만 가리고 도메인은 남긴다")
    void email() {
        assertThat(Mask.email("hongildong@example.kr")).isEqualTo("ho********@example.kr");
        // 로컬파트가 짧아도 최소 한 글자는 가린다
        assertThat(Mask.email("ab@x.kr")).isEqualTo("a*@x.kr");
        assertThat(Mask.email("a@x.kr")).isEqualTo("a*@x.kr");
        assertThat(Mask.email(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("이메일 형태가 아니면 일반 문자열 규칙으로 가린다")
    void emailWithoutAt() {
        // @ 가 없는 값이 이메일 칸에 들어와도 원본이 그대로 나가면 안 된다
        assertThat(Mask.email("notanemail")).isEqualTo("n********l");
        assertThat(Mask.email("@nolocal.kr")).isEqualTo("@*********r");
    }

    @Test
    @DisplayName("전화번호 — 뒤 4자리만 남긴다(본인확인 관행)")
    void phone() {
        assertThat(Mask.phone("01012345678")).isEqualTo("010-****-5678");
        assertThat(Mask.phone("010-1234-5678")).isEqualTo("010-****-5678");
        assertThat(Mask.phone("0212345678")).isEqualTo("021-****-5678");
        // 너무 짧으면 자릿수조차 알려 주지 않는다
        assertThat(Mask.phone("1234")).isEqualTo("****");
        assertThat(Mask.phone(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("생년월일 — 연도만 남긴다(연령대 확인은 되되 생일은 가린다)")
    void birthDate() {
        assertThat(Mask.birthDate("19900101")).isEqualTo("1990-**-**");
        assertThat(Mask.birthDate("1990-01-01")).isEqualTo("1990-**-**");
        assertThat(Mask.birthDate("90")).isEqualTo("****");
        assertThat(Mask.birthDate(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("DI 는 부분값도 보여 주지 않는다 — 전 기관 공통 식별자다")
    void token() {
        assertThat(Mask.token("A1B2C3D4E5F6")).isEqualTo("(설정됨)");
        assertThat(Mask.token(null)).isEqualTo("-");
    }

    @Test
    @DisplayName("주소 — 시·군·구까지만 보이고 번지는 자른다")
    void address() {
        // 10글자에서 자르되 끝 공백은 털고 말줄임을 붙인다
        assertThat(Mask.address("서울특별시 종로구 세종대로 1")).isEqualTo("서울특별시 종로구…");
        assertThat(Mask.address("짧은주소")).isEqualTo("짧은주소");
        assertThat(Mask.address(null)).isEqualTo("-");
    }
}
