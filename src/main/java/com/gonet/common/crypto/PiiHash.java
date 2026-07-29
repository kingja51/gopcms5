package com.gonet.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PII 검색용 해시 — HMAC-SHA256.
 *
 * <p>암호화 컬럼에는 {@code LIKE} 도 {@code =} 도 걸 수 없다(같은 평문이라도 IV 가 달라
 * 암호문이 매번 다르다). 그래서 <b>정확 매칭이 필요한 값만</b> 별도 해시 컬럼에 함께 담는다
 * — {@code email_hash}, {@code phone_hash}, {@code di_hash}, {@code login_id_hash}.
 *
 * <p>단순 SHA-256 이 아니라 <b>HMAC</b> 인 이유: 이메일·전화번호는 값의 공간이 좁아
 * 해시만 유출되면 사전 대입으로 원문을 복원할 수 있다. 키를 섞으면 키 없이는 못 맞춘다.
 * 그래서 이 키({@code GOPCMS_PII_HMAC_KEY})는 AES 마스터키와 <b>분리</b>한다 —
 * 용도별 키 분리 원칙이며, 한쪽이 새도 다른 쪽이 남는다.
 *
 * <p>정규화를 여기서 한다: 대소문자·앞뒤 공백이 다르면 같은 이메일도 다른 해시가 되어
 * 중복 검사가 통과해 버린다.
 */
@Component
public class PiiHash {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public PiiHash(@Value("${GOPCMS_PII_HMAC_KEY}") String hmacKeyBase64) {
        byte[] raw = Base64.getDecoder().decode(hmacKeyBase64.trim());
        if (raw.length < 32) {
            // 짧은 키는 HMAC 강도를 떨어뜨린다 — 기동 시점에 끊는다(fail-fast 규약)
            throw new IllegalStateException(
                    "GOPCMS_PII_HMAC_KEY 는 base64 32바이트 이상이어야 합니다 (현재 "
                            + raw.length + "바이트)");
        }
        this.key = new SecretKeySpec(raw, ALGORITHM);
    }

    /**
     * 검색용 해시 — 소문자·trim 정규화 후 HMAC-SHA256(hex 64자).
     *
     * @return null 은 null (해시 컬럼도 비운다)
     */
    public String hash(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(
                    mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("검색 해시를 만들지 못했습니다", e);
        }
    }
}
