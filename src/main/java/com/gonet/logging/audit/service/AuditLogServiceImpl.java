package com.gonet.logging.audit.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.web.PageResult;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.logging.audit.dto.AuditLog;
import com.gonet.logging.audit.mapper.AuditLogMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 적재 — logging_db 전용 TxManager + {@code REQUIRES_NEW} 격리 (conventions §3).
 *
 * <p>격리가 필요한 이유는 두 가지다. 하나는 로그 실패가 본 업무를 롤백시키지 않게 하는 것,
 * 다른 하나는 <b>실패한 행위도 기록에 남기는</b> 것이다 — 주 트랜잭션에 묶이면
 * {@code result='FAIL'} 인 기록이 그 롤백에 함께 휩쓸려 사라진다. 감사에서는 실패 시도가
 * 성공보다 중요할 때가 많다.
 */
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl extends AbstractCmsService implements AuditLogService {

    private final AuditLogMapper mapper;

    @Override
    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX,
            propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog log) {
        mapper.insert(log);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX, readOnly = true)
    public PageResult<AuditLog> getPage(AuditLog.Search search) {
        int total = mapper.countPage(search);
        List<AuditLog> rows = total == 0 ? List.of() : mapper.findPage(search);
        return new PageResult<>(rows, total, search.getPage(), search.getSize());
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX, readOnly = true)
    public AuditLog get(Long logAuditId) {
        return logAuditId == null ? null : mapper.findById(logAuditId);
    }
}
