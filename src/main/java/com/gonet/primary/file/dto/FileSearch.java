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
}
