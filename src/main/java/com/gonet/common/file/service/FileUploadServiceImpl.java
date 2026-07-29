package com.gonet.common.file.service;

import com.gonet.common.file.config.FileUploadProperties;
import com.gonet.common.file.dto.UploadCategory;
import com.gonet.common.file.dto.UploadCommit;
import com.gonet.common.file.security.FileExtensionValidator;
import com.gonet.common.file.security.FileStorage;
import com.gonet.common.file.security.ImageIntegrityValidator;
import com.gonet.common.file.security.ImageReencoder;
import com.gonet.common.file.security.Sha256Hasher;
import com.gonet.common.file.security.TikaMimeDetector;
import com.gonet.common.file.security.UploadValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 다중 방어 파이프라인.
 *
 * <p><b>순서가 방어의 일부다.</b> 값싼 검사(확장자)를 먼저 해서 대부분을 걸러내고, 파일을
 * 실제로 읽어야 하는 검사(매직바이트·재인코딩·해시)를 뒤에 둔다. 그리고 이 모든 과정을
 * <b>격리 디렉터리</b>에서 수행한 다음에야 정식 저장소로 옮긴다 — 통과하지 못한 파일이
 * 정식 경로에 한 순간도 존재하지 않게 하려는 것이다.
 *
 * <p>어느 단계에서 실패하든 격리본은 지운다. 실패한 업로드의 잔해가 디스크에 쌓이면
 * 그 자체가 나중의 사고 지점이 된다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    private final FileUploadProperties properties;
    private final FileExtensionValidator extensionValidator;
    private final TikaMimeDetector mimeDetector;
    private final ImageReencoder imageReencoder;
    private final ImageIntegrityValidator imageIntegrity;
    private final Sha256Hasher hasher;
    private final FileStorage storage;

    @Override
    public UploadCommit pipeline(MultipartFile file, UploadCategory category, String storedName) {
        if (file == null || file.isEmpty()) {
            throw new UploadValidationException("빈 파일은 올릴 수 없습니다.");
        }
        long limit = properties.getMaxFileSize().toBytes();
        if (file.getSize() > limit) {
            throw new UploadValidationException(
                    "파일이 너무 큽니다. (최대 %dMB)".formatted(limit / 1048576));
        }

        String originalName = file.getOriginalFilename();
        // ① 확장자 — 널바이트·경로·이중확장자·화이트리스트
        String extension = extensionValidator.validate(originalName, category);

        Path quarantined = null;
        try (InputStream in = file.getInputStream()) {
            quarantined = storage.receiveToQuarantine(in, storedName + "." + extension);

            // ② 매직바이트 — 확장자·카테고리와 교차 검증
            String mime = mimeDetector.detectAndVerify(quarantined, extension, category);

            // ③ 이미지 처리 — 재인코딩이 최선이고, 안 되는 형식은 구조를 검사한다.
            //    GIF89a 뒤에 코드를 이어 붙인 폴리글롯이 매직바이트만으로는 통과하므로
            //    "형식이 선언한 끝 == 실제 파일의 끝" 을 확인한다(실측으로 잡은 구멍).
            boolean image = mime.startsWith("image/");
            boolean reencoded = false;
            if (image) {
                if (imageReencoder.isReencodable(extension)) {
                    reencoded = imageReencoder.reencode(quarantined, extension);
                } else if (imageIntegrity.handles(extension)) {
                    imageIntegrity.validate(quarantined, extension);
                }
            }

            // ④ 해시 — 재인코딩 뒤의 최종 내용 기준이어야 한다
            String sha256 = hasher.hash(quarantined);
            long size = Files.size(quarantined);

            // ⑤ 정식 저장소로 이동
            String relative = storage.promote(quarantined, storedName + "." + extension);
            quarantined = null;                // 이동 완료 — finally 에서 지울 대상이 아니다

            return new UploadCommit(originalName, storedName + "." + extension, relative,
                    extension, mime, file.getContentType(), size, sha256, image, reencoded);

        } catch (IOException e) {
            log.error("업로드 처리 실패: {}", e.toString());
            throw new UploadValidationException("파일을 처리하지 못했습니다.");
        } finally {
            storage.discard(quarantined);
        }
    }
}
