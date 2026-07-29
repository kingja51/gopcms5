package com.gonet.common.crypto;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PII 암복호화 정적 진입점.
 *
 * <p>MyBatis TypeHandler 는 스프링 빈이 아니라 MyBatis 가 직접 만드는 객체라 주입을 받을 수
 * 없다. 그래서 기동 시 한 번 {@link Aes256Gcm} 을 여기에 걸어 두고 TypeHandler 가 꺼내 쓴다.
 *
 * <p>정적 상태를 두는 것은 마지막 수단이지만, 대안(TypeHandler 를 빈으로 등록하는
 * ObjectFactory 교체)은 MyBatis 내부에 더 깊이 손을 대야 해서 이쪽이 덜 위험하다.
 */
@Component
@RequiredArgsConstructor
public final class PiiCrypto {

    private static volatile Aes256Gcm cipher;

    private final Aes256Gcm aes256Gcm;

    @PostConstruct
    void bind() {
        cipher = aes256Gcm;
    }

    /** 평문 → {@code {AG}}+base64. null·빈 값은 그대로. 이미 암호문이면 이중 암호화하지 않는다. */
    static String encrypt(String plain) {
        if (plain == null || plain.isEmpty() || Aes256Gcm.isEncrypted(plain)) {
            return plain;
        }
        return require().encrypt(plain);
    }

    /**
     * 저장값 → 평문. {@code {AG}} 가 없으면 평문으로 간주해 그대로 돌려준다(이행기 정책).
     *
     * <p>복호화가 실패하면 <b>예외를 그대로 던진다</b>. 키가 바뀐 상태를 조용히 넘기면
     * 화면에는 빈 값이 보이고 그대로 저장돼 원본이 사라진다.
     */
    static String decrypt(String stored) {
        if (stored == null || !Aes256Gcm.isEncrypted(stored)) {
            return stored;
        }
        return require().decrypt(stored);
    }

    private static Aes256Gcm require() {
        Aes256Gcm current = cipher;
        if (current == null) {
            throw new IllegalStateException(
                    "PII 암호화가 초기화되지 않았습니다 — GOPCMS_PII_MASTER_KEY 주입을 확인하세요");
        }
        return current;
    }
}
