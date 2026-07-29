package com.gonet.primary.file.service;

import com.gonet.common.file.dto.UploadCategory;
import com.gonet.common.web.PageResult;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.dto.FileSearch;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** 파일 도메인 — 업로드 기록·조회·권한·삭제. 물리 처리는 공통 엔진이 맡는다. */
public interface FileService {

    /**
     * 업로드 — 권한 검사 → 다중 방어 파이프라인 → tb_file 적재.
     *
     * <p>다운로드 정책은 <b>인자로 받지 않는다.</b> 업로드 요청이 정책을 지정할 수 있으면
     * 남의 entityId 로 올리면서 그 묶음의 공개 범위를 낮출 수 있다(실측으로 확인한 결함).
     * 기존 묶음이면 정책을 그대로 두고, 새 묶음이면 가장 좁은 값으로 만든다.
     *
     * @param entityType 소유 도메인 (BBS/CONTENT/EDITOR …) — 화이트리스트 검증 대상
     * @param entityId   소유 PK — 폼 GET 시점에 미리 발급한 값
     */
    FileItem upload(MultipartFile file, UploadCategory category,
                    String entityType, String entityId, String siteId);

    /**
     * 그룹 확보 — <b>도메인 서비스가 폼 저장 전에 정책을 확정하는 단일 진입점</b>.
     *
     * <p>없으면 만들고, 있는데 정책이 다르면 맞춘다. picker 가 업로드하면서 그룹을 만들게
     * 두면 DB 기본값(ROLE_MEMBER)이 박히는 구간이 생기는데, 그 창을 없애는 것이 목적이다.
     *
     * @return file_group_id
     */
    String ensureGroup(String entityType, String entityId, String siteId, String downloadAuth);

    List<FileItem> findByGroup(String fileGroupId);

    List<FileItem> findByEntity(String entityType, String entityId);

    FileItem findById(String fileId);

    /** picker 결과 반영 — keep 에 없는 파일은 내린다. */
    int syncAttachments(String fileGroupId, List<String> keepFileIds);

    /** 다운로드 직전 검사 — 권한·검사상태를 모두 통과해야 파일을 돌려준다. */
    FileItem openForDownload(String fileId);

    /** 관리자 전용 — 검사 상태와 무관하게 원본을 확인한다(격리 검증용). */
    FileItem openForAdmin(String fileId);

    PageResult<FileItem> getAdmPage(FileSearch cond);

    /** 관리 상세 — 삭제 표시된 것도 조회한다(복구 화면이 필요로 한다). */
    FileItem findAnyById(String fileId);

    /** 관리자 수정 — 표시명·정렬. 저장 경로·해시·MIME 은 방어의 근거라 대상이 아니다. */
    void updateAdm(String fileId, String originalName, Integer sortOrder);

    /** 검사 상태 수동 변경 — 오탐 해제·재검사. 누가 바꿨는지 남는다. */
    void updateScanStatusAdm(String fileId, String status);

    /** 소유 묶음의 다운로드 정책 변경 — 공개 범위를 바꾸는 유일한 관리 경로. */
    void updateDownloadAuthAdm(String fileGroupId, String downloadAuth);

    void deleteAdm(String fileId);

    /** 복구 — 물리 삭제 전까지는 되돌릴 수 있어야 오삭제가 사고로 끝나지 않는다. */
    void restoreAdm(String fileId);
}
