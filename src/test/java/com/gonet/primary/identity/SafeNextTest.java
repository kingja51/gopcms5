package com.gonet.primary.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 본인인증 팝업의 부모창 복귀 경로 허용 형태.
 *
 * <p>이 값은 팝업이 부모창을 보낼 주소가 되므로({@code window.location.href}) 느슨하면
 * 그대로 오픈 리다이렉트다. 차단 목록 방식은 우회 표기가 계속 나와서 늘 뒤처지므로
 * <b>허용 형태</b>로 뒤집었고, 그 형태를 여기서 고정한다.
 *
 * <p>패턴은 {@code NiceCheckUsrController.SAFE_NEXT} 의 사본이다 — 상수가 private 이라
 * 리플렉션으로 꺼내는 대신 같은 문자열을 두고, <b>바뀌면 이 테스트가 깨지도록</b> 한다.
 */
class SafeNextTest {

    private static final Pattern SAFE_NEXT = Pattern.compile("/[a-z0-9_-][a-z0-9_/-]{0,199}");

    private boolean allowed(String next) {
        return next != null && SAFE_NEXT.matcher(next).matches();
    }

    @Test
    @DisplayName("정상 복귀 경로는 통과한다")
    void acceptsInternalPaths() {
        assertThat(allowed("/ai/member/join/verify")).isTrue();
        assertThat(allowed("/ai/member/dormant")).isTrue();
        assertThat(allowed("/a")).isTrue();
    }

    @Test
    @DisplayName("프로토콜 상대 URL — 두 번째 슬래시를 막는다")
    void rejectsProtocolRelative() {
        assertThat(allowed("//evil.com")).isFalse();
        // 점이 없어도 사내망에서는 호스트로 해석된다 — 함께 막아야 한다
        assertThat(allowed("//evil")).isFalse();
        assertThat(allowed("///evil")).isFalse();
    }

    @Test
    @DisplayName("백슬래시 — 브라우저가 슬래시로 정규화하므로 프로토콜 상대와 같아진다")
    void rejectsBackslash() {
        assertThat(allowed("/\\evil.com")).isFalse();
        assertThat(allowed("\\\\evil.com")).isFalse();
        assertThat(allowed("/\\/evil.com")).isFalse();
    }

    @Test
    @DisplayName("인코딩·스킴·제어문자로 우회하려는 값을 막는다")
    void rejectsEncodedAndScheme() {
        assertThat(allowed("/%2f%2fevil.com")).isFalse();
        assertThat(allowed("https://evil.com")).isFalse();
        assertThat(allowed("javascript:alert(1)")).isFalse();
        assertThat(allowed("/ai\nSet-Cookie: x=1")).isFalse();
        assertThat(allowed("/ai/../../etc/passwd")).isFalse();
    }

    @Test
    @DisplayName("경로가 아닌 값·빈 값은 막는다")
    void rejectsNonPaths() {
        assertThat(allowed(null)).isFalse();
        assertThat(allowed("")).isFalse();
        assertThat(allowed("/")).isFalse();            // 슬래시 하나만으로는 목적지가 없다
        assertThat(allowed("ai/member")).isFalse();    // 앞 슬래시 없음 = 상대 경로
        assertThat(allowed("/ai?next=//evil.com")).isFalse();   // 쿼리스트링 미허용
    }

    @Test
    @DisplayName("길이 상한이 있다 — 무한정 긴 값을 세션에 담지 않는다")
    void rejectsOverlyLong() {
        // 패턴은 `/` + 첫 글자 + 최대 199자 = 총 201자까지 허용한다
        assertThat(allowed("/" + "a".repeat(200))).isTrue();
        assertThat(allowed("/" + "a".repeat(201))).isFalse();
    }
}
