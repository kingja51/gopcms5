package com.gonet.common.file.service;

import com.gonet.common.file.dto.UploadCategory;
import com.gonet.common.file.dto.UploadCommit;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 엔진 — 다중 방어 파이프라인의 단일 진입점. <b>DB 를 모른다.</b>
 *
 * <p>도메인(게시판·컨텐츠·회원 프로필)이 늘어나도 방어 로직은 여기 한 벌만 존재해야 한다.
 * 도메인마다 업로드 경로가 생기면 그중 하나만 허술해도 전체가 뚫린다.
 */
public interface FileUploadService {

    /**
     * 파이프라인 실행 — 확장자 → 매직바이트 교차검증 → 재인코딩 → 해시 → 격리→정식 이동.
     *
     * @param storedName 저장 파일명(호출자가 발급한 ID 기반) — 원본명은 저장에 쓰지 않는다
     * @throws com.gonet.common.file.security.UploadValidationException 정책 위반
     */
    UploadCommit pipeline(MultipartFile file, UploadCategory category, String storedName);
}
