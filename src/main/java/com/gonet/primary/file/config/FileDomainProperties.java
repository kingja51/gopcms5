package com.gonet.primary.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 파일 도메인 정책 — {@code gopcms.file.*} (엔진 설정은 gopcms.file.upload.*). */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.file")
public class FileDomainProperties {

    /**
     * 한 그룹(= 한 글·한 폼)에 붙일 수 있는 파일 수.
     * 화면이 막아도 API 를 직접 부를 수 있으므로 서버가 다시 센다.
     */
    private int maxFilesPerGroup = 20;

    /** 썸네일 긴 변 픽셀. */
    private int thumbnailSize = 400;
}
