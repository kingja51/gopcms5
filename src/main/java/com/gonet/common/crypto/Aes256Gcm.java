package com.gonet.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM 암복호화 — conventions.md §6 저장 형식의 구현체.
 *
 * <p>저장 형식: <b>{@code {AG}} + base64(IV‖암호문‖태그)</b>. {@code {AG}} 프리픽스가
 * "이 값은 암호문"이라는 표식이라 평문 이행기 데이터와 섞여 있어도 구분·이중암호화 방지가 된다.
 * IV 는 건당 12바이트 난수 — 같은 평문도 매번 다른 암호문이 되므로 <b>암호화 컬럼은 검색 불가</b>다
 * (검색이 필요하면 {@code *_hash} 컬럼 병행).
 *
 * <p>마스터키는 {@code GOPCMS_PII_MASTER_KEY}(base64 32바이트) — 미주입·길이 오류 시
 * <b>기동 실패</b>가 의도된 동작이다(비밀값 없이 뜨는 것을 막는다).
 *
 * <p>현재 사용처는 관리자 TOTP 시크릿. MyBatis {@code @Encrypt} TypeHandler 는 이 클래스를
 * 감싸는 형태로 얹는다(회원 PII 이행 시).
 */
@Component
public class Aes256Gcm {

    /** 암호문 식별 프리픽스 — 키 회전 시 {@code {AG2}} 로 증설해 신·구 병행 복호화. */
    public static final String PREFIX = "{AG}";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public Aes256Gcm(@Value("${GOPCMS_PII_MASTER_KEY}") String masterKeyBase64) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(masterKeyBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "GOPCMS_PII_MASTER_KEY 가 base64 가 아닙니다 — 기동 중단", e);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "GOPCMS_PII_MASTER_KEY 는 base64 인코딩된 32바이트여야 합니다 (현재 "
                            + decoded.length + "바이트) — 기동 중단");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    /** 평문 → {@code {AG}}base64. null 은 null, 이미 암호문이면 그대로(이중 암호화 방지). */
    public String encrypt(String plain) {
        if (plain == null || isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    /** {@code {AG}}base64 → 평문. 프리픽스가 없으면 이행기 평문으로 간주해 그대로 돌려준다. */
    public String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("복호화 실패 — 키 회전·데이터 손상 여부 확인", e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
