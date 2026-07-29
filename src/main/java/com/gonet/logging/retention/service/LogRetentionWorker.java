package com.gonet.logging.retention.service;

import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.logging.retention.dto.RetentionTarget;
import com.gonet.logging.retention.mapper.LogRetentionMapper;
import com.gonet.primary.auth.mapper.LoginHistoryMapper;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테이블 1개 단위의 독립 트랜잭션 — <b>별도 빈</b>이어야 하는 이유가 여기 있다.
 *
 * <p>같은 클래스 안에서 {@code this.deleteXxx()} 로 부르면 프록시를 우회해
 * {@code @Transactional} 이 통째로 무시된다(CLAUDE.md 트랜잭션 함정). 서비스와 워커를
 * 나눠 두면 그 실수를 구조적으로 막는다.
 *
 * <p>두 DB 를 각자의 TxManager 로 잡는다. 한 트랜잭션으로 묶으려는 시도 자체를 하지
 * 않는다 — 크로스 DB 는 규약상 금지다.
 */
@Component
@RequiredArgsConstructor
public class LogRetentionWorker {

    /**
     * 등록부에 있는 이름만 매퍼로 넘긴다.
     *
     * <p>매퍼 XML 이 {@code <choose>} 로 미리 적어 둔 문장을 고르므로 이름이 SQL 이 되지는
     * 않는다. 그래도 한 번 더 거르는 것은, 등록부 밖의 이름이 오면 <b>아무 분기에도 안
     * 걸려 문법 오류가 나는</b> 대신 여기서 원인이 분명한 예외로 끝내기 위해서다.
     */
    private static final Set<String> ALLOWED = RetentionTarget.loggingTargets().stream()
            .filter(RetentionTarget::purgeable)
            .map(RetentionTarget::table)
            .collect(Collectors.toUnmodifiableSet());

    private final LogRetentionMapper logRetentionMapper;
    private final LoginHistoryMapper loginHistoryMapper;

    @Transactional(readOnly = true, transactionManager = MyBatisConfig.LOGGING_TX)
    public int countLogging(String table, LocalDateTime cutoff) {
        return logRetentionMapper.countExpired(check(table), cutoff);
    }

    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX,
            propagation = Propagation.REQUIRES_NEW)
    public int deleteLogging(String table, LocalDateTime cutoff, int limit) {
        return logRetentionMapper.deleteExpired(check(table), cutoff, limit);
    }

    @Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
    public int countLoginHistory(LocalDateTime cutoff) {
        return loginHistoryMapper.countExpired(cutoff);
    }

    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX,
            propagation = Propagation.REQUIRES_NEW)
    public int deleteLoginHistory(LocalDateTime cutoff, int limit) {
        return loginHistoryMapper.deleteExpired(cutoff, limit);
    }

    private String check(String table) {
        if (!ALLOWED.contains(table)) {
            throw new IllegalArgumentException("파기 등록부에 없는 테이블입니다: " + table);
        }
        return table;
    }
}
