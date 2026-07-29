package com.gonet.primary.file.dto;

import com.gonet.common.web.PageRequest;
import lombok.Getter;
import lombok.Setter;

/** 관리자 파일 목록 검색 조건. */
@Getter
@Setter
public class FileSearch extends PageRequest {

    private String entityType;
    private String extension;
    /** 검사 상태 — 운영자가 INFECTED/ERROR 만 골라 보는 경로. */
    private String scanStatus;

    /**
     * 삭제 표시된 것도 보기.
     *
     * <p>정리 배치가 물리 삭제하기 전까지는 되돌릴 수 있어야 하고, 되돌리려면 먼저
     * 보여야 한다. 기본은 꺼짐 — 평소 목록이 지운 것으로 어지러워지지 않게.
     */
    private boolean includeDeleted;
}
