package com.gonet.primary.file.service;

import com.gonet.common.file.security.FileStorage;
import com.gonet.common.file.security.VirusScanQueue;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.file.config.FileDomainProperties;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.mapper.FileGroupMapper;
import com.gonet.primary.file.mapper.FileMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파일 정리 — 물리 삭제 · 고아 그룹 회수 · 백신 재검사 요청.
 *
 * <p><b>삭제는 되돌릴 수 없다.</b> 그래서 이 클래스의 설계는 전부 "사고를 작게" 쪽으로 기운다:
 * <ul>
 *   <li>기본이 dry-run — 켜는 순간 대량 삭제가 나지 않는다</li>
 *   <li>1회 실행 상한 — 잘못 돌아도 피해가 한 배치로 제한된다</li>
 *   <li>단건 독립 트랜잭션 — 한 건이 실패해도 나머지는 진행하고, 실패한 건만 남는다</li>
 *   <li>디스크 먼저, DB 나중 — 순서가 뒤바뀌면 <b>참조를 잃은 파일</b>이 디스크에 영원히
 *       남는다. 반대 순서의 사고(행은 지웠는데 파일이 남음)가 더 고치기 어렵다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
@Slf4j
public class FilePurgeService extends AbstractCmsService {

    private final FileMapper fileMapper;
    private final FileGroupMapper fileGroupMapper;
    private final FilePurgeWorker worker;
    private final FileStorage storage;
    private final VirusScanQueue virusScanQueue;
    private final FileDomainProperties properties;

    /** 배치 1회 결과 — 로그와 관리 화면이 같은 값을 본다. */
    public record PurgeResult(int scanned, int purged, int failed, int orphanGroups,
                              boolean dryRun) {
    }

    /**
     * 보존기간이 지난 soft-delete 파일의 물리 삭제 + 고아 그룹 회수.
     *
     * <p>트랜잭션을 걸지 않는다 — 디스크 삭제는 롤백되지 않으므로, 하나의 큰 트랜잭션으로
     * 묶으면 중간에 실패했을 때 DB 만 되돌아가고 파일은 이미 사라진 상태가 된다.
     * 대신 <b>건당</b> 독립 트랜잭션으로 DB 를 정리한다.
     */
    public PurgeResult purge() {
        FileDomainProperties.Purge cfg = properties.getPurge();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(cfg.getRetentionDays());
        List<FileItem> targets = fileMapper.findPurgeTargets(cutoff, cfg.getBatchSize());

        int purged = 0;
        int failed = 0;
        for (FileItem item : targets) {
            if (cfg.isDryRun()) {
                log.info("[dry-run] 물리 삭제 대상 file={} name={} path={} size={}",
                        item.getFileId(), item.getOriginalName(),
                        item.getStoredPath(), item.getSizeBytes());
                continue;
            }
            try {
                // ① 디스크 먼저 — 여기서 실패하면 행을 남겨 다음 회차에 재시도한다
                storage.deletePhysical(item.getStoredPath());
                if (item.getThumbnailPath() != null) {
                    storage.deleteThumbnail(item.getThumbnailPath());
                }
                // ② 그다음 행 — 실체 없는 행이 남으면 목록이 거짓말을 한다
                worker.deleteFileRow(item.getFileId());
                purged++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("파일 정리 실패(다음 회차 재시도) file={}: {}",
                        item.getFileId(), e.toString());
            }
        }

        int orphans = purgeOrphanGroups(cfg);

        PurgeResult result = new PurgeResult(targets.size(), purged, failed, orphans,
                cfg.isDryRun());
        log.info("파일 정리 배치 — 대상 {}건 / 삭제 {}건 / 실패 {}건 / 고아그룹 {}건{}",
                result.scanned(), result.purged(), result.failed(), result.orphanGroups(),
                result.dryRun() ? " (dry-run: 실제로 지우지 않음)" : "");
        return result;
    }

    /**
     * 파일이 하나도 없는 오래된 묶음을 지운다.
     *
     * <p>폼을 열면 PK 를 미리 발급하고 그룹이 생기는데, 저장하지 않고 나가면 그대로 남는다.
     * 다형 참조라 FK CASCADE 가 없어 DB 가 대신 치워 주지 않는다.
     */
    private int purgeOrphanGroups(FileDomainProperties.Purge cfg) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(cfg.getOrphanGroupDays());
        List<String> ids = fileGroupMapper.findOrphanGroupIds(cutoff, cfg.getBatchSize());
        if (cfg.isDryRun()) {
            ids.forEach(id -> log.info("[dry-run] 고아 그룹 대상 group={}", id));
            return ids.size();
        }
        int removed = 0;
        for (String id : ids) {
            try {
                worker.deleteGroupRow(id);
                removed++;
            } catch (RuntimeException e) {
                log.warn("고아 그룹 정리 실패 group={}: {}", id, e.toString());
            }
        }
        return removed;
    }

    /**
     * 결과가 확정되지 않은 채 오래 머문 파일을 다시 검사 큐에 넣는다.
     *
     * <p>백신을 쓰지 않는 구성에서는 모든 파일이 PENDING 으로 남으므로 이 배치는
     * 의미가 없다 — 잡 쪽에서 백신이 켜졌을 때만 부른다.
     */
    public int requeueStaleScans() {
        FileDomainProperties.Rescan cfg = properties.getRescan();
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(cfg.getStaleMinutes());
        List<FileItem> targets = fileMapper.findRescanTargets(staleBefore, cfg.getBatchSize());
        for (FileItem item : targets) {
            virusScanQueue.enqueue(item.getFileId(), storage.resolve(item.getStoredPath()));
        }
        if (!targets.isEmpty()) {
            log.info("백신 재검사 요청 {}건", targets.size());
        }
        return targets.size();
    }
}
