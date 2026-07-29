package com.gonet.common.file.security;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

/**
 * 방어 ③ — 이미지 재인코딩.
 *
 * <p>이미지 파일에는 픽셀 외의 것이 얼마든지 들어간다(EXIF 주석에 심은 스크립트, 다중 형식
 * 파일 등). 이걸 <b>디코딩해서 픽셀만 다시 쓰면</b> 원본에 무엇이 붙어 있었든 사라진다.
 * 서명 검사보다 확실한 것은 "원본을 버리는 것" 이다.
 *
 * <p>재인코딩에 실패하면 <b>업로드를 거부한다</b>. 디코딩되지 않는 이미지는 이미지가 아니고,
 * 그런 파일을 원본 그대로 통과시키면 이 방어가 무의미해진다.
 */
@Component
@Slf4j
public class ImageReencoder {

    /** 재인코딩 대상 — 애니메이션(gif)·벡터(svg)는 여기서 다루지 않는다. */
    private static final Set<String> REENCODABLE = Set.of("jpg", "jpeg", "png", "bmp");

    public boolean isReencodable(String extension) {
        return REENCODABLE.contains(extension);
    }

    /**
     * 제자리 재인코딩. 성공하면 true, 대상 형식이 아니면 false.
     *
     * @throws UploadValidationException 이미지로 읽히지 않는 경우
     */
    public boolean reencode(Path file, String extension) {
        if (!isReencodable(extension)) {
            return false;
        }
        String format = "jpg".equals(extension) ? "jpeg" : extension;
        Path tmp = file.resolveSibling(file.getFileName() + ".re");
        try {
            if (ImageIO.read(file.toFile()) == null) {
                throw new UploadValidationException("이미지로 읽을 수 없는 파일입니다.");
            }
            // toFile 이 아니라 스트림으로 쓴다 — Thumbnailator 는 대상 파일명이 outputFormat 과
            // 다르면 확장자를 덧붙여 다른 이름으로 저장한다(실측: .png.re → .png.re.png).
            try (OutputStream out = Files.newOutputStream(tmp)) {
                Thumbnails.of(file.toFile())
                        .scale(1.0)
                        .outputFormat(format)
                        .toOutputStream(out);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (UploadValidationException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("이미지 재인코딩 실패 — 업로드를 거부한다: {}", e.toString());
            throw new UploadValidationException("이미지로 읽을 수 없는 파일입니다.");
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {
                // 임시 파일 정리 실패는 업로드 성패와 무관하다
            }
        }
    }

    /** 썸네일 생성 — 긴 변 기준 축소. 실패해도 업로드 자체는 유효하므로 예외를 던지지 않는다. */
    public boolean thumbnail(Path source, Path target, int size) {
        try {
            Files.createDirectories(target.getParent());
            // 여기도 스트림으로 — 대상이 .png 인데 outputFormat 이 jpeg 면 파일명이 바뀐다
            try (OutputStream out = Files.newOutputStream(target)) {
                Thumbnails.of(source.toFile())
                        .size(size, size)
                        .outputFormat("jpeg")
                        .toOutputStream(out);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("썸네일 생성 실패(업로드는 유효): {}", e.toString());
            return false;
        }
    }
}
