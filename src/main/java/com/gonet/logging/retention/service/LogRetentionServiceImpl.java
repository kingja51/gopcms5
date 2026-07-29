package com.gonet.logging.retention.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.retention.RetentionProperties;
import com.gonet.logging.retention.dto.RetentionTarget;
import com.gonet.logging.retention.dto.RetentionTarget.RetentionKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 로그 파기 — 등록부({@link RetentionTarget})를 돌며 테이블마다 처리한다.
 *
 * <p><b>여기에 트랜잭션이 없다.</b> 실제 삭제는 {@link LogRetentionWorker} 가 테이블마다
 * 독립 트랜잭션으로 한다. 이 클래스가 트랜잭션을 잡으면 두 DB 가 한 트랜잭션에 묶이거나
 * (불가능), 한 테이블 실패로 전체가 롤백된다 — 둘 다 원하는 동작이 아니다.
 *
 * <p>자기호출로는 {@code @Transactional} 이 걸리지 않으므로 워커는 <b>별도 빈</b>이다
 * (선행 프로젝트 실장애 유형 — CLAUDE.md 트랜잭션 함정).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogRetentionServiceImpl extends AbstractCmsService implements LogRetentionService {

    private final RetentionProperties properties;
    private final LogRetentionWorker worker;

    @Override
    public List<PurgeResult> purgeAll() {
        List<PurgeResult> results = new ArrayList<>();
        for (RetentionTarget target : RetentionTarget.loggingTargets()) {
            if (!target.purgeable()) {
                // 건너뛴 이유를 남긴다 — 로그만 보고도 "왜 이 테이블은 안 줄지" 를 알 수 있게
                log.info("로그 파기 제외 table={} 사유={}", target.table(), target.note());
                continue;
            }
            results.add(purgeLogging(target));
        }
        for (RetentionTarget target : RetentionTarget.primaryTargets()) {
            results.add(purgePrimary(target));
        }
        return results;
    }

    private PurgeResult purgeLogging(RetentionTarget target) {
        int months = monthsOf(target.retentionKey());
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(months);
        try {
            int expired = worker.countLogging(target.table(), cutoff);
            if (expired == 0 || properties.getPurge().isDryRun()) {
                return new PurgeResult(target.table(), months, expired, 0, false, false);
            }
            int limit = properties.getPurge().getBatchSize();
            int deleted = worker.deleteLogging(target.table(), cutoff, limit);
            return new PurgeResult(target.table(), months, expired, deleted,
                    expired > deleted, false);
        } catch (RuntimeException e) {
            // 한 테이블이 실패해도 나머지는 진행한다 — 다음 회차에 재시도된다
            log.error("로그 파기 실패 table={} : {}", target.table(), e.toString());
            return new PurgeResult(target.table(), months, 0, 0, false, true);
        }
    }

    /** primary_db 대상 — 같은 흐름이지만 다른 DataSource·TxManager 를 탄다. */
    private PurgeResult purgePrimary(RetentionTarget target) {
        int months = monthsOf(target.retentionKey());
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(months);
        try {
            int expired = worker.countLoginHistory(cutoff);
            if (expired == 0 || properties.getPurge().isDryRun()) {
                return new PurgeResult(target.table(), months, expired, 0, false, false);
            }
            int limit = properties.getPurge().getBatchSize();
            int deleted = worker.deleteLoginHistory(cutoff, limit);
            return new PurgeResult(target.table(), months, expired, deleted,
                    expired > deleted, false);
        } catch (RuntimeException e) {
            log.error("로그인 이력 파기 실패 : {}", e.toString());
            return new PurgeResult(target.table(), months, 0, 0, false, true);
        }
    }

    /**
     * 보존기간 조회 — 설정에서 읽는다.
     *
     * <p>{@code NONE} 이 여기까지 오면 등록부와 이 메서드가 어긋난 것이다. 0 을 돌려
     * 조용히 전부 지우는 대신 터뜨린다.
     */
    private int monthsOf(RetentionKey key) {
        return switch (key) {
            case PRIVACY -> properties.getPrivacyLogMonths();
            case LOG -> properties.getLogMonths();
            case LOGIN_HISTORY -> properties.getLoginHistoryMonths();
            case NONE -> throw new IllegalStateException(
                    "파기 대상이 아닌 테이블에 보존기간을 물었습니다 — 등록부를 확인하세요.");
        };
    }
}
