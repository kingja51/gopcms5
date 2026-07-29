package com.gonet.common.file.viewer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** LibreOffice 변환 설정 — {@code gopcms.file.viewer.office.*}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.file.viewer.office")
public class OfficeConverterProperties {

    /**
     * 기본이 꺼짐인 이유: LibreOffice 가 없는 서버에서 켜 두면 미리보기를 누를 때마다
     * 실패한다. 있는 곳에서만 명시적으로 켠다.
     */
    private boolean enabled = false;

    /** soffice 실행 파일 경로. */
    private String binary = "soffice";

    /**
     * 변환 제한 시간(초). 넘으면 프로세스를 강제 종료한다 —
     * 악성 문서든 그냥 큰 문서든, 인스턴스를 붙잡고 있게 두지 않는다.
     */
    private int timeoutSeconds = 60;

    /** 변환 결과 캐시 디렉터리. 재변환을 막아 반복 요청이 서비스 거부가 되지 않게 한다. */
    private String cacheDir = "./data/gopcms/preview";
}
