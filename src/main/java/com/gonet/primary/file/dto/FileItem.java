package com.gonet.primary.file.dto;

import com.gonet.common.audit.Auditable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** tb_file 1건. */
@Getter
@Setter
public class FileItem extends Auditable {

    private String fileId;
    private String fileGroupId;
    private String originalName;
    private String storedName;
    private String storedPath;
    private String thumbnailPath;
    private String extension;
    /** Tika 판별 MIME — 방어의 기준값. */
    private String mimeDetected;
    /** 클라이언트 신고값 — 감사용, 신뢰 금지. */
    private String mimeClient;
    private long sizeBytes;
    private String fileHash;
    private String isImageYn;
    private String reencodedYn;
    private String virusScanStatus;
    private long downloadCount;
    private int sortOrder;
    private String deleteYn;

    /* 조회 편의 — 목록 화면에서 소유 그룹의 정책을 함께 보여준다 */
    private String entityType;
    private String entityId;
    private String downloadAuth;
    private LocalDateTime createdAtView;

    public boolean isImage() {
        return "Y".equals(isImageYn);
    }
}
