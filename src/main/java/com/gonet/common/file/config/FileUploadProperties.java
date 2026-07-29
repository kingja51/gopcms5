package com.gonet.common.file.config;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 업로드 엔진 설정 — {@code gopcms.file.upload.*}.
 *
 * <p>저장 경로는 <b>웹루트 밖</b>이어야 한다. 정적 자원 경로 안에 두면 톰캣이 파일을 그대로
 * 내보내므로, 업로드된 스크립트가 곧바로 실행 가능한 URL 을 갖게 된다(웹쉘의 고전적 경로).
 * 다운로드는 반드시 컨트롤러를 거친다.
 *
 * <p>격리 디렉터리를 따로 두는 이유: 방어 단계를 다 통과하기 <b>전</b>의 파일이 정식 저장소에
 * 잠깐이라도 존재하면 그 순간이 창이 된다. 검사를 마친 뒤에만 정식 경로로 옮긴다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.file.upload")
public class FileUploadProperties {

    /** 정식 저장소 — 검사를 통과한 파일의 최종 위치. */
    private String baseDir;

    /** 격리 저장소 — 검사 중인 파일이 머무는 곳. */
    private String quarantineDir;

    /** 썸네일 저장소. */
    private String thumbDir;

    /** 단일 파일 상한. Spring multipart 한도와 별개로 엔진에서 한 번 더 본다. */
    private DataSize maxFileSize = DataSize.ofMegabytes(200);

    /** 썸네일 긴 변 픽셀. */
    private int thumbnailSize = 400;

    /**
     * 카테고리별 허용 확장자(소문자). 키: any · document · image · video.
     *
     * <p><b>화이트리스트만 쓴다.</b> 블랙리스트는 새 확장자가 나올 때마다 뚫린다.
     */
    private Map<String, List<String>> allowedExtensions = Map.of();

    /** 카테고리 키로 허용 목록 조회 — 미정의 카테고리는 빈 목록(=전부 거부). */
    public List<String> extensionsOf(String category) {
        return allowedExtensions.getOrDefault(category, List.of());
    }
}
