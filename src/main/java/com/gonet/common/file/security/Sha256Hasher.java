package com.gonet.common.file.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * 방어 ④ — SHA-256 해시.
 *
 * <p>두 가지 용도다. ①무결성 점검(FIM): 저장 후 파일이 바뀌었는지 나중에 대조할 수 있다.
 * ②중복 판정: 같은 내용이 반복 업로드되는 것을 값싸게 찾는다.
 *
 * <p>전체를 메모리에 올리지 않고 스트리밍으로 계산한다 — 200MB 업로드가 동시에 여러 건이면
 * 힙이 먼저 터진다.
 */
@Component
public class Sha256Hasher {

    public String hash(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // DigestInputStream 이 읽는 동안 해시를 갱신한다
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new UploadValidationException("파일 검증에 실패했습니다.");
        }
    }
}
