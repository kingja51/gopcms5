package com.gonet.common.file.viewer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MS 오피스 문서 → PDF 변환 (LibreOffice headless).
 *
 * <p><b>여기가 이 프로젝트에서 서버가 신뢰할 수 없는 문서를 실제로 여는 유일한 지점이다.</b>
 * 오피스 파서는 원격코드실행 이력이 길고, 우리가 여는 문서는 전부 외부에서 들어온 것이다.
 * 그래서 방어를 겹겹이 둔다:
 *
 * <ul>
 *   <li><b>별도 프로세스</b> — 변환이 터져도 애플리케이션은 살아 있다(JVM 안에서 파싱하지 않는다)</li>
 *   <li><b>타임아웃 + 강제 종료</b> — 무한 루프·거대 문서로 인스턴스를 묶어 두지 못하게</li>
 *   <li><b>변환마다 새 프로파일 디렉터리</b> — 문서가 남긴 매크로·설정이 다음 변환에 영향을
 *       주지 못한다. LibreOffice 는 프로파일을 공유하면 상태가 누적된다</li>
 *   <li><b>결과 캐시</b> — 같은 파일을 다시 열 때 재변환하지 않는다. 변환은 비싸고,
 *       비쌀수록 반복 요청이 곧 서비스 거부가 된다</li>
 *   <li><b>기본 꺼짐</b> — LibreOffice 가 없는 서버에서 조용히 실패하는 대신 아예 켜지 않는다</li>
 * </ul>
 *
 * <p>남은 위험은 문서화한다: 변환 프로세스의 <b>네트워크 차단·파일시스템 권한 축소</b>는
 * OS 레벨에서 해야 한다(전용 계정 + 방화벽). 앱이 대신해 줄 수 없는 부분이다.
 */
@Component
@ConditionalOnProperty(name = "gopcms.file.viewer.office.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class OfficeConverter {

    private final OfficeConverterProperties properties;

    /**
     * 변환 실행 — 성공하면 결과 PDF 경로.
     *
     * @param source 원본 (정식 저장소의 파일)
     * @param target 결과를 놓을 경로 (캐시 위치)
     * @throws DocumentConversionException 변환 실패·시간 초과
     */
    public void convertToPdf(Path source, Path target) {
        Path work = null;
        Process process = null;
        try {
            // 변환마다 새 작업 디렉터리 — 프로파일과 산출물이 섞이지 않게
            work = Files.createTempDirectory("gopcms-office-");
            Path profile = work.resolve("profile");
            Files.createDirectories(profile);

            List<String> command = List.of(
                    properties.getBinary(),
                    "--headless",
                    "--norestore",              // 이전 세션 복구 안 함 — 상태를 물려받지 않는다
                    "--nolockcheck",
                    "--nodefault",
                    "--nofirststartwizard",
                    // 전용 프로파일 — 문서가 남긴 설정이 다음 변환에 영향을 주지 못한다
                    "-env:UserInstallation=file:///" + profile.toAbsolutePath()
                            .toString().replace('\\', '/'),
                    "--convert-to", "pdf:writer_pdf_Export",
                    "--outdir", work.toAbsolutePath().toString(),
                    source.toAbsolutePath().toString());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();

            if (!process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS)) {
                // 시간 초과 = 악성 문서일 수도, 그냥 큰 문서일 수도 있다. 어느 쪽이든 끊는다
                process.destroyForcibly();
                throw new DocumentConversionException("변환 시간이 초과되었습니다.");
            }
            if (process.exitValue() != 0) {
                throw new DocumentConversionException("문서를 변환하지 못했습니다.");
            }

            Path produced = work.resolve(stripExtension(source.getFileName().toString()) + ".pdf");
            if (!Files.isReadable(produced)) {
                throw new DocumentConversionException("변환 결과를 찾지 못했습니다.");
            }
            Files.createDirectories(target.getParent());
            Files.move(produced, target, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {
            throw new DocumentConversionException("문서를 변환하지 못했습니다.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DocumentConversionException("변환이 중단되었습니다.");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteQuietly(work);
        }
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** 작업 디렉터리 정리 — 실패해도 변환 결과에는 영향이 없다. */
    private void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignore) {
                    // 개별 파일 정리 실패는 무시 — 임시 디렉터리는 OS 가 결국 회수한다
                }
            });
        } catch (IOException e) {
            log.warn("변환 작업 디렉터리 정리 실패 {}: {}", dir, e.toString());
        }
    }
}
