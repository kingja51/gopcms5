package com.gonet.primary.file.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.file.dto.UploadCategory;
import com.gonet.common.file.dto.UploadCommit;
import com.gonet.common.file.security.FileStorage;
import com.gonet.common.file.security.ImageReencoder;
import com.gonet.common.file.security.UploadValidationException;
import com.gonet.common.file.security.VirusScanQueue;
import com.gonet.common.file.service.FileUploadService;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.PageResult;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.file.config.FileDomainProperties;
import com.gonet.primary.file.dto.DownloadAuth;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.primary.file.dto.FileEntityType;
import com.gonet.primary.file.dto.FileGroup;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.dto.FileSearch;
import com.gonet.primary.file.dto.VirusScanStatus;
import com.gonet.primary.file.mapper.FileGroupMapper;
import com.gonet.primary.file.mapper.FileMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 도메인 서비스.
 *
 * <p>업로드는 <b>권한 검사가 먼저</b>다. 파일을 디스크에 받은 뒤 권한을 보면, 거부된 요청도
 * 이미 저장소를 건드린 뒤가 된다(디스크 채우기 공격의 여지).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
@Slf4j
public class FileServiceImpl extends AbstractCmsService implements FileService {

    private final FileMapper fileMapper;
    private final FileGroupMapper fileGroupMapper;
    private final FileUploadService uploadEngine;
    private final FileAccessGuard guard;
    private final FileStorage storage;
    private final ImageReencoder imageReencoder;
    private final VirusScanQueue virusScanQueue;
    private final FileDomainProperties domainProperties;

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public FileItem upload(MultipartFile file, UploadCategory category,
            String entityType, String entityId, String siteId) {

        // ① 소유 도메인 화이트리스트 — 자유 문자열을 그대로 저장하지 않는다
        if (!FileEntityType.isValid(entityType)) {
            throw new UploadValidationException("알 수 없는 첨부 구분입니다.");
        }
        if (entityId == null || entityId.isBlank()) {
            throw new UploadValidationException("첨부 대상이 지정되지 않았습니다.");
        }

        // ② 권한 — 디스크를 건드리기 전에 본다
        LoginPrincipal principal = FileEntityType.EDITOR.equals(entityType)
                ? guard.requireEditorUploadPermission()
                : guard.requireUploadPermission();

        /*
         * ③ 그룹 확보 — 정책은 <b>서버가</b> 정한다.
         *
         * 업로드 요청이 download_auth 를 지정할 수 있게 두었더니, 로그인만 하면 남의
         * entityId 로 업로드하면서 그 그룹의 정책을 ANONYMOUS 로 낮출 수 있었다
         * (실측: OWNER_PRIVACY 첨부가 공개로 바뀜). 첨부 하나를 올리는 요청이 남의 자료
         * 공개 범위를 바꾸는 권한을 가져서는 안 된다.
         *
         * 그래서: 기존 그룹이면 정책을 그대로 두고, 새 그룹이면 가장 좁은 기본값으로 만든다.
         * 정책 변경은 도메인 서비스가 ensureGroup 을 직접 부르는 경로(서버 내부)로만 한다.
         */
        FileGroup existingGroup = fileGroupMapper.findByEntity(entityType, entityId);
        String groupId;
        if (existingGroup != null) {
            assertGroupWritable(existingGroup, principal);
            groupId = existingGroup.getFileGroupId();
        } else {
            String initial = FileEntityType.EDITOR.equals(entityType)
                    ? DownloadAuth.ANONYMOUS      // 본문에 박히는 이미지는 공개일 수밖에 없다
                    : DownloadAuth.ROLE_MEMBER;   // 그 외는 좁게 시작 — 넓히는 것은 도메인의 판단
            groupId = ensureGroup(entityType, entityId, siteId, initial);
        }

        // ③ 개수 상한 — 폼이 막아도 API 직접 호출이 가능하므로 서버가 다시 센다
        int existing = fileMapper.findByGroup(groupId).size();
        if (existing >= domainProperties.getMaxFilesPerGroup()) {
            throw new UploadValidationException(
                    "한 번에 %d개까지 올릴 수 있습니다.".formatted(domainProperties.getMaxFilesPerGroup()));
        }

        // ④ 다중 방어 파이프라인
        String fileId = Uid.next(UidPrefix.FIL);
        UploadCommit commit = uploadEngine.pipeline(file, category, fileId);

        FileItem item = new FileItem();
        item.setFileId(fileId);
        item.setFileGroupId(groupId);
        item.setOriginalName(commit.originalName());
        item.setStoredName(commit.storedName());
        item.setStoredPath(commit.storedPath());
        item.setExtension(commit.extension());
        item.setMimeDetected(commit.mimeDetected());
        item.setMimeClient(commit.mimeClient());
        item.setSizeBytes(commit.sizeBytes());
        item.setFileHash(commit.sha256());
        item.setIsImageYn(commit.image() ? "Y" : "N");
        item.setReencodedYn(commit.reencoded() ? "Y" : "N");
        item.setVirusScanStatus(VirusScanStatus.PENDING);
        item.setSortOrder(existing);
        fileMapper.insert(item);

        // ⑤ 썸네일 — 실패해도 업로드는 유효하다
        if (commit.image()) {
            String thumbRelative = commit.storedPath();
            if (imageReencoder.thumbnail(storage.resolve(commit.storedPath()),
                    storage.resolveThumb(thumbRelative), domainProperties.getThumbnailSize())) {
                item.setThumbnailPath(thumbRelative);
                fileMapper.updateThumbnail(fileId, thumbRelative);
            }
        }

        // ⑥ 백신 큐 — 비동기. 결과가 올 때까지 상태는 PENDING 이다
        virusScanQueue.enqueue(fileId, storage.resolve(commit.storedPath()));

        log.info("파일 업로드 file={} group={} ext={} mime={} size={} reencoded={}",
                fileId, groupId, commit.extension(), commit.mimeDetected(),
                commit.sizeBytes(), commit.reencoded());
        return item;
    }

    /**
     * 남의 첨부 묶음에 끼워 넣지 못하게 한다.
     *
     * <p>entityId 는 클라이언트가 보낸다. 그것만 알면 남의 글에 파일을 붙일 수 있으므로,
     * 그룹을 만든 사람이 아니면 거부한다. 다만 담당자(ROLE_STAFF) 이상은 운영상 남의 글을
     * 손봐야 하는 일이 있어 열어 둔다 — 그 행위는 감사 컬럼에 남는다.
     */
    private void assertGroupWritable(FileGroup group, LoginPrincipal principal) {
        String owner = group.getCreatedBy();
        if (owner == null || owner.equals(principal.userId())) {
            return;
        }
        if (guard.hasRole("ROLE_STAFF")) {
            return;
        }
        log.warn("남의 첨부 묶음 접근 차단 group={} owner={} actor={}",
                group.getFileGroupId(), owner, principal.userId());
        throw new AccessDeniedException("다른 사람의 첨부에는 올릴 수 없습니다.");
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public String ensureGroup(String entityType, String entityId, String siteId, String downloadAuth) {
        String policy = DownloadAuth.isValid(downloadAuth) ? downloadAuth : DownloadAuth.ROLE_MEMBER;
        FileGroup existing = fileGroupMapper.findByEntity(entityType, entityId);
        if (existing != null) {
            if (!policy.equals(existing.getDownloadAuth())) {
                fileGroupMapper.updateDownloadAuth(existing.getFileGroupId(), policy,
                        AuditorContext.currentUserId(), AuditorContext.currentIp());
            }
            return existing.getFileGroupId();
        }
        FileGroup group = new FileGroup();
        group.setFileGroupId(Uid.next(UidPrefix.FGR));
        group.setEntityType(entityType);
        group.setEntityId(entityId);
        group.setSiteId(siteId);
        group.setDownloadAuth(policy);
        fileGroupMapper.insert(group);
        return group.getFileGroupId();
    }

    @Override
    public List<FileItem> findByGroup(String fileGroupId) {
        return fileMapper.findByGroup(fileGroupId);
    }

    @Override
    public List<FileItem> findByEntity(String entityType, String entityId) {
        FileGroup group = fileGroupMapper.findByEntity(entityType, entityId);
        return group == null ? List.of() : fileMapper.findByGroup(group.getFileGroupId());
    }

    @Override
    public FileItem findById(String fileId) {
        return fileMapper.findById(fileId);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public int syncAttachments(String fileGroupId, List<String> keepFileIds) {
        return fileMapper.softDeleteNotIn(fileGroupId, keepFileIds,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public FileItem openForDownload(String fileId) {
        FileItem item = fileMapper.findById(fileId);
        if (item == null) {
            throw new AccessDeniedException("파일을 찾을 수 없습니다.");
        }
        FileGroup group = fileGroupMapper.findById(item.getFileGroupId());
        guard.enforceDownload(group, group == null ? null : group.getCreatedBy());

        if (!VirusScanStatus.isDownloadable(item.getVirusScanStatus())) {
            // 검사 결과가 안전하다고 확인되지 않은 파일 — 모르는 것은 열지 않는다
            log.warn("검사 미통과 파일 다운로드 차단 file={} status={}",
                    fileId, item.getVirusScanStatus());
            throw new AccessDeniedException("검사가 완료되지 않아 내려받을 수 없습니다.");
        }
        fileMapper.increaseDownloadCount(fileId);
        return item;
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public FileItem openForAdmin(String fileId) {
        FileItem item = fileMapper.findById(fileId);
        if (item == null) {
            throw new AccessDeniedException("파일을 찾을 수 없습니다.");
        }
        if (!VirusScanStatus.isDownloadable(item.getVirusScanStatus())) {
            log.warn("관리자 강제 다운로드 — 검사 미통과 file={} status={} actor={}",
                    fileId, item.getVirusScanStatus(), AuditorContext.currentUserId());
        }
        return item;
    }

    @Override
    public PageResult<FileItem> getAdmPage(FileSearch cond) {
        return new PageResult<>(fileMapper.findPage(cond), fileMapper.countPage(cond),
                cond.getPage(), cond.getSize());
    }

    @Override
    public FileItem findAnyById(String fileId) {
        return fileMapper.findAnyById(fileId);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void updateAdm(String fileId, String originalName, Integer sortOrder) {
        FileItem patch = new FileItem();
        patch.setFileId(fileId);
        // 표시명은 화면에 그대로 나가고 Content-Disposition 에도 실린다 —
        // 파일명 자체가 공격 표면이므로 업로드 때와 같은 기준으로 다시 거른다
        if (originalName != null && !originalName.isBlank()) {
            String name = originalName.trim();
            if (name.length() > 255 || name.indexOf(' ') >= 0
                    || name.contains("/") || name.contains("\\") || name.contains("..")) {
                throw new IllegalArgumentException("사용할 수 없는 파일명입니다.");
            }
            patch.setOriginalName(name);
        }
        patch.setSortOrder(sortOrder == null ? 0 : sortOrder);
        fileMapper.updateAdm(patch);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void updateScanStatusAdm(String fileId, String status) {
        if (!VirusScanStatus.isKnown(status)) {
            throw new IllegalArgumentException("알 수 없는 검사 상태입니다.");
        }
        // 오탐 해제는 되돌릴 수 없는 판단이라 흔적을 남긴다
        log.info("검사 상태 수동 변경 file={} status={} actor={}",
                fileId, status, AuditorContext.currentUserId());
        fileMapper.updateScanStatus(fileId, status,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void updateDownloadAuthAdm(String fileGroupId, String downloadAuth) {
        if (!DownloadAuth.isValid(downloadAuth)) {
            throw new IllegalArgumentException("알 수 없는 다운로드 권한입니다.");
        }
        log.info("첨부 공개 범위 변경 group={} auth={} actor={}",
                fileGroupId, downloadAuth, AuditorContext.currentUserId());
        fileGroupMapper.updateDownloadAuth(fileGroupId, downloadAuth,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void restoreAdm(String fileId) {
        fileMapper.restore(fileId, AuditorContext.currentUserId(), AuditorContext.currentIp());
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void deleteAdm(String fileId) {
        // soft delete 만 한다 — 물리 삭제는 보존기간 배치의 몫이다.
        // 여기서 바로 지우면 오삭제를 되돌릴 수 없고 무결성 대조 근거도 사라진다.
        fileMapper.softDelete(fileId, AuditorContext.currentUserId(), AuditorContext.currentIp());
    }
}
