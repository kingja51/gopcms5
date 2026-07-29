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

    /** 정리 배치 정책. */
    private Purge purge = new Purge();

    /** 백신 재검사 정책. */
    private Rescan rescan = new Rescan();

    @Getter
    @Setter
    public static class Purge {

        /**
         * soft delete 후 물리 삭제까지의 유예(일).
         *
         * <p>바로 지우지 않는 이유: 오삭제를 되돌릴 창이 필요하고, 무결성 대조(file_hash)를
         * 해야 할 사건이 뒤늦게 드러나기도 한다.
         */
        private int retentionDays = 180;

        /** 고아 그룹(파일 없는 묶음) 정리 유예(일) — 작성 중인 폼을 지우지 않을 만큼. */
        private int orphanGroupDays = 7;

        /** 1회 실행 상한 — 되돌릴 수 없는 작업이라 한 번에 몰아서 하지 않는다. */
        private int batchSize = 500;

        /**
         * 실제로 지우지 않고 대상만 로그로 남긴다.
         *
         * <p><b>기본값이 true 인 것은 의도다.</b> 배치를 처음 켜는 순간 오래된 파일이
         * 한꺼번에 사라지는 사고가 가장 흔하다. 운영자가 로그로 대상을 확인한 뒤
         * 명시적으로 false 로 바꾸게 한다.
         */
        private boolean dryRun = true;
    }

    @Getter
    @Setter
    public static class Rescan {

        /** 결과 미확정 상태로 이 시간(분)을 넘긴 파일을 재검사 대상으로 본다. */
        private int staleMinutes = 30;

        private int batchSize = 200;
    }
}
