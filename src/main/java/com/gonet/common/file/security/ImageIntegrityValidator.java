package com.gonet.common.file.security;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 재인코딩할 수 없는 이미지의 <b>구조 검사</b> — 폴리글롯(붙임 파일) 차단.
 *
 * <p>재인코딩(픽셀만 다시 쓰기)은 붙어 있던 것을 통째로 버리므로 가장 확실하다. 하지만
 * GIF(애니메이션)·WEBP 는 재인코딩하면 원본 특성이 손상되거나 JDK 가 인코더를 갖고 있지
 * 않다. 그래서 이 둘은 <b>형식이 선언한 끝과 실제 파일의 끝이 같은지</b>를 본다.
 *
 * <p>막으려는 것은 구체적이다: {@code GIF89a…<?php …?>} 처럼 정상 헤더 뒤에 코드를 이어
 * 붙인 파일. 매직바이트만 보면 이미지로 통과하는데, 형식의 종료 지점 뒤에 남은 바이트가
 * 있다는 사실은 숨길 수 없다.
 *
 * <p>실행 경로가 없어도 막는 이유: 이 저장소는 파일을 웹루트 밖에 두고 옥텟 스트림으로만
 * 내보내므로 지금은 실행되지 않는다. 그러나 이런 파일이 쌓여 있으면 <b>다른 취약점 하나가
 * 생겼을 때</b> 곧바로 무기가 된다. 들이지 않는 편이 싸다.
 */
@Component
@Slf4j
public class ImageIntegrityValidator {

    /** GIF 는 0x3B(트레일러)로 끝난다. */
    private static final byte GIF_TRAILER = 0x3B;

    /** 검사 대상 — 재인코딩으로 처리하지 못하는 이미지 형식. */
    public boolean handles(String extension) {
        return "gif".equals(extension) || "webp".equals(extension);
    }

    /**
     * @throws UploadValidationException 형식이 깨졌거나 선언된 끝 뒤에 데이터가 남아 있을 때
     */
    public void validate(Path file, String extension) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UploadValidationException("이미지를 확인할 수 없습니다.");
        }
        switch (extension) {
            case "gif" -> validateGif(file, bytes);
            case "webp" -> validateWebp(bytes);
            default -> { /* 대상 아님 */ }
        }
    }

    private void validateGif(Path file, byte[] bytes) {
        // ① 실제로 디코딩되는 이미지인가 — 헤더만 흉내 낸 파일을 여기서 떨군다
        try {
            if (ImageIO.read(file.toFile()) == null) {
                throw new UploadValidationException("이미지로 읽을 수 없는 파일입니다.");
            }
        } catch (IOException e) {
            throw new UploadValidationException("이미지로 읽을 수 없는 파일입니다.");
        }
        // ② 마지막 바이트가 트레일러여야 한다 — 뒤에 뭔가 이어 붙었다면 여기서 걸린다
        if (bytes.length == 0 || bytes[bytes.length - 1] != GIF_TRAILER) {
            log.warn("GIF 종료 표시 뒤 잔여 데이터 — 업로드 거부");
            throw new UploadValidationException("이미지 파일이 올바르지 않습니다.");
        }
    }

    /**
     * WEBP 는 RIFF 컨테이너다: {@code "RIFF" + <크기 4바이트 LE> + "WEBP" …}.
     * 선언된 크기는 그 필드 뒤부터 파일 끝까지의 길이와 같아야 한다 —
     * 뒤에 코드를 이어 붙이면 실제 길이가 더 커져 어긋난다.
     */
    private void validateWebp(byte[] bytes) {
        if (bytes.length < 12
                || bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F' || bytes[3] != 'F'
                || bytes[8] != 'W' || bytes[9] != 'E' || bytes[10] != 'B' || bytes[11] != 'P') {
            throw new UploadValidationException("이미지 파일이 올바르지 않습니다.");
        }
        long declared = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
                & 0xFFFFFFFFL;
        long actual = bytes.length - 8L;
        if (declared != actual) {
            log.warn("WEBP 선언 크기 불일치 declared={} actual={} — 업로드 거부", declared, actual);
            throw new UploadValidationException("이미지 파일이 올바르지 않습니다.");
        }
    }
}
