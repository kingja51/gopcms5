package com.gonet.common.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * PK 채번 유틸 — {@code 접두어(대문자3) + "_" + UUID v7} = 40자 (doc/conventions.md §1).
 *
 * <p>UUID v7 알고리즘은 선행 프로젝트(pcms2026-001 UuidV7Generator) 검증 구현을 통합:
 * 48-bit ms timestamp + 4-bit version(7) + 12-bit random + 2-bit variant + 62-bit random
 * — 시간순 단조 증가로 B-tree 인덱스 지역성 확보.
 *
 * <p>접두어는 {@link UidPrefix} enum 으로만 지정(레지스트리 미등록·오타를 컴파일 타임
 * 차단). 채번은 이 유틸 단일 경로 — DB 함수 채번 금지(멀티 벤더 원칙).
 * 클래스명 Egov 접두 금지·org.egovframe.rte 패키지 금지(eGov 확장 규칙) 준수.
 */
public final class Uid {

    /** 검증 정규식 — conventions.md §1 (Validator·테스트 공용). */
    public static final String PATTERN =
            "^[A-Z]{3}_[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uid() {
        // 유틸리티 클래스 인스턴스화 방지
    }

    /** 예: {@code Uid.next(UidPrefix.SIT)} → {@code SIT_01890a5d-ac96-774b-bcce-b302099a8057} (40자) */
    public static String next(UidPrefix prefix) {
        return prefix.name() + "_" + uuidV7();
    }

    /** UUID v7 문자열 (36자, 하이픈 포함·소문자) — 접두어 없는 원시 값. */
    static String uuidV7() {
        return toUuid(Instant.now().toEpochMilli()).toString();
    }

    /** 타임스탬프 기반 UUID v7 생성 (RFC 9562). */
    private static UUID toUuid(long timestamp) {
        // 48-bit timestamp (상위 48비트)
        long msb = (timestamp & 0xFFFF_FFFF_FFFFL) << 16;

        // version 7 (4비트: 0111)
        msb |= 0x7000L;

        // 12-bit random (하위 12비트)
        msb |= (RANDOM.nextLong() & 0xFFFL);

        // LSB: variant (10xx) + 62-bit random
        long lsb = RANDOM.nextLong();
        lsb = (lsb & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;

        return new UUID(msb, lsb);
    }
}
