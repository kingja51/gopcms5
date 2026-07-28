package com.gonet.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UidTest {

    @Test
    @DisplayName("형식: 접두어(대문자3)+_+UUIDv7 = 정확히 40자, conventions §1 정규식 일치")
    void format() {
        String id = Uid.next(UidPrefix.SIT);
        assertThat(id).hasSize(40).startsWith("SIT_").matches(Uid.PATTERN);
    }

    @Test
    @DisplayName("전 접두어가 형식을 만족한다 (enum ↔ 레지스트리 가드)")
    void allPrefixes() {
        for (UidPrefix prefix : UidPrefix.values()) {
            assertThat(Uid.next(prefix)).matches(Uid.PATTERN).startsWith(prefix.name() + "_");
        }
    }

    @Test
    @DisplayName("유일성: 대량 생성 시 중복 없음")
    void uniqueness() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            assertThat(seen.add(Uid.uuidV7())).isTrue();
        }
    }

    @Test
    @DisplayName("시간순: ms 가 다르면 사전순도 증가 (UUIDv7 타임스탬프 상위 배치)")
    void timeOrdered() throws InterruptedException {
        String first = Uid.uuidV7();
        Thread.sleep(3);
        String second = Uid.uuidV7();
        assertThat(first.compareTo(second)).isLessThan(0);
    }
}
