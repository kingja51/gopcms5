package com.gonet.common.file.security;

import com.gonet.common.file.config.FileUploadProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 방어 ⑤ — 저장소. 격리 → 검사 → 정식 이동의 물리 경로를 책임진다.
 *
 * <p>원본 파일명을 저장 이름으로 쓰지 않는 것이 이 클래스의 핵심 규칙이다. 이름을 우리가
 * 정하면 경로 조작·확장자 위장·중복 덮어쓰기가 <b>구조적으로</b> 불가능해진다.
 *
 * <p>날짜 폴더로 쪼개는 이유: 한 디렉터리에 파일이 수십만 개 쌓이면 파일시스템 조회가
 * 급격히 느려지고 백업·정리도 어려워진다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileStorage {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileUploadProperties properties;

    /** 격리 디렉터리에 받아 둔다 — 검사를 통과하기 전에는 정식 저장소에 두지 않는다. */
    public Path receiveToQuarantine(InputStream in, String storedName) {
        Path dir = Path.of(properties.getQuarantineDir());
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(storedName);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            log.error("격리 저장 실패: {}", e.toString());
            throw new UploadValidationException("파일을 저장하지 못했습니다.");
        }
    }

    /**
     * 검사를 통과한 파일을 정식 저장소로 옮기고 상대 경로를 돌려준다.
     * 이동(move)이라 격리본이 남지 않는다.
     */
    public String promote(Path quarantined, String storedName) {
        String relative = LocalDate.now().format(DATE_DIR) + "/" + storedName;
        Path target = Path.of(properties.getBaseDir()).resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.move(quarantined, target, StandardCopyOption.REPLACE_EXISTING);
            return relative;
        } catch (IOException e) {
            log.error("정식 저장소 이동 실패: {}", e.toString());
            throw new UploadValidationException("파일을 저장하지 못했습니다.");
        }
    }

    /** 정식 저장소의 절대 경로. */
    public Path resolve(String relativePath) {
        return contain(Path.of(properties.getBaseDir()), relativePath);
    }

    /** 썸네일 절대 경로. */
    public Path resolveThumb(String relativePath) {
        return contain(Path.of(properties.getThumbDir()), relativePath);
    }

    /**
     * 조립한 경로가 뿌리 밖으로 나가지 않는지 확인한다.
     *
     * <p>{@code storedPath} 는 우리가 만든 값이라 지금은 안전하다. 그래도 확인하는 이유:
     * 이 메서드는 앞으로 다른 호출자가 생길 수 있고, 경로 탈출은 한 번 뚫리면 임의 파일
     * 읽기가 된다. 값이 어디서 왔는지에 의존하지 않는 방어가 오래 간다.
     */
    private Path contain(Path root, String relativePath) {
        Path base = root.toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            log.error("저장소 밖 경로 접근 차단 root={} relative={}", base, relativePath);
            throw new UploadValidationException("잘못된 파일 경로입니다.");
        }
        return target;
    }

    /** 실패 시 격리본 정리 — 삭제 실패는 업로드 결과에 영향을 주지 않는다. */
    public void discard(Path quarantined) {
        if (quarantined == null) {
            return;
        }
        try {
            Files.deleteIfExists(quarantined);
        } catch (IOException e) {
            log.warn("격리 파일 정리 실패 {}: {}", quarantined, e.toString());
        }
    }

    /** 물리 삭제 — soft delete 후 보존기간이 지난 파일만 대상으로 한다. */
    public boolean deletePhysical(String relativePath) {
        try {
            return Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            log.warn("물리 삭제 실패 {}: {}", relativePath, e.toString());
            return false;
        }
    }

    /** 썸네일 삭제 — 원본을 지웠는데 축소본이 남으면 내용이 계속 노출된다. */
    public boolean deleteThumbnail(String relativePath) {
        try {
            return Files.deleteIfExists(resolveThumb(relativePath));
        } catch (IOException e) {
            log.warn("썸네일 삭제 실패 {}: {}", relativePath, e.toString());
            return false;
        }
    }
}
