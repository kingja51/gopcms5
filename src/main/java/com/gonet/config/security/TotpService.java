package com.gonet.config.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 관리자 2단계 인증(TOTP, RFC 6238) — 시크릿 발급·검증·QR 생성.
 *
 * <p>QR 은 <b>서버에서 직접 그려 data: URI 로 심는다</b>. googleauth 가 제공하는 QR 헬퍼는
 * 외부 차트 서비스 URL 을 만드는데, 그건 (1) 시크릿을 외부로 보내고 (2) CSP
 * {@code img-src 'self' data:} 에도 걸린다 — 둘 다 받아들일 수 없다.
 *
 * <p>기본 파라미터는 인증 앱 표준(SHA1·6자리·30초)이며 앞뒤 1구간을 허용해
 * 기기 시계 오차를 흡수한다(googleauth 기본 window).
 */
@Component
public class TotpService {

    private static final String ISSUER = "GOPCMS";
    private static final int QR_SIZE = 220;

    private final GoogleAuthenticator authenticator = new GoogleAuthenticator();

    /** 새 시크릿(base32) — 등록 확정 전까지 세션에만 둔다. */
    public String newSecret() {
        GoogleAuthenticatorKey credentials = authenticator.createCredentials();
        return credentials.getKey();
    }

    /** 6자리 코드 검증. 형식이 아니면 검증 실패로 처리(예외를 밖으로 흘리지 않는다). */
    public boolean verify(String secret, String code) {
        if (secret == null || code == null || code.isBlank()) {
            return false;
        }
        try {
            return authenticator.authorize(secret, Integer.parseInt(code.trim()));
        } catch (IllegalArgumentException e) { // 숫자 아님·base32 아님 모두 검증 실패로
            return false;
        }
    }

    /** 인증 앱 등록용 {@code otpauth://} URI — QR 의 내용이자 수동 입력 대안. */
    public String otpAuthUri(String loginId, String secret) {
        String label = URLEncoder.encode(ISSUER + ":" + loginId, StandardCharsets.UTF_8);
        return "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30"
                .formatted(label, secret, ISSUER);
    }

    /** QR PNG 를 data: URI 로 — 외부 요청 없이 페이지에 인라인된다. */
    public String qrDataUri(String otpAuthUri) {
        try {
            BitMatrix matrix = new QRCodeWriter()
                    .encode(otpAuthUri, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            return "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("QR 생성 실패", e);
        }
    }
}
