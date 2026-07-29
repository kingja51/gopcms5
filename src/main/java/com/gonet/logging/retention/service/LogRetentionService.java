package com.gonet.logging.retention.service;

import java.util.List;

/**
 * 보존기간이 지난 로그 파기.
 *
 * <p>한 배치가 두 DB 를 오간다 — {@code logging_db} 의 {@code log_*} 와 {@code primary_db}
 * 의 {@code tb_login_history}. 크로스 DB 트랜잭션은 만들지 않는다(규약 §3): 테이블마다
 * 독립 트랜잭션으로 처리하고, 한 테이블이 실패해도 나머지는 진행한다.
 */
public interface LogRetentionService {

    /**
     * 테이블 1개의 처리 결과.
     *
     * @param table     테이블명
     * @param months    적용된 보존기간(개월) — 5년/36개월이 섞여 있어 결과에 함께 남긴다
     * @param expired   보존기간이 지난 전체 건수
     * @param deleted   이번 회차에 실제로 지운 건수(dry-run 이면 0)
     * @param truncated 상한에 걸려 남은 것이 있는가 — 다음 회차가 이어서 지운다
     * @param failed    실패 여부
     */
    record PurgeResult(String table, int months, int expired, int deleted,
            boolean truncated, boolean failed) {
    }

    /**
     * 전체 대상 파기.
     *
     * <p>{@code dry-run} 이면 건수만 센다. 결과 요약은 호출부(잡)가 로그로 남긴다 —
     * 서비스가 로그 형식까지 정하면 관리 화면에서 같은 값을 쓰기 어려워진다.
     */
    List<PurgeResult> purgeAll();
}
