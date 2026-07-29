package com.gonet.logging.file.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * log_file_download 1건 — 누가 언제 무엇을 내려받았는가.
 *
 * <p>append-only 다. {@code updated_*} 3컬럼이 없는 것은 의도된 설계 —
 * 지나간 사건의 기록은 고쳐지지 않아야 감사 근거가 된다.
 *
 * <p>파일이 지워진 뒤에도 <b>무엇을 받았는지</b> 알 수 있어야 하므로
 * 파일명·확장자·크기를 스냅샷으로 함께 남긴다. file_id 만 두면 파일이 사라진 순간
 * 기록이 "어떤 파일인지 알 수 없음" 이 된다.
 */
@Getter
@Setter
public class FileDownloadLog {

    private Long logFileDownloadId;
    private String fileId;
    private String fileGroupId;
    /** SINGLE(단일) / THUMB(썸네일) / ADMIN(관리자 강제). */
    private String downloadType;
    private String actorUserId;
    private String actorUserType;
    private String actorLoginId;
    private String originalName;
    private String extension;
    private Long sizeBytes;
    private String requestUri;
    private String clientIp;
    private String userAgent;
    private String traceId;
    /** SUCCESS / DENIED — 거부된 시도도 남긴다(반복 거부는 그 자체로 신호다). */
    private String result;
    private LocalDateTime downloadedAt;
}
