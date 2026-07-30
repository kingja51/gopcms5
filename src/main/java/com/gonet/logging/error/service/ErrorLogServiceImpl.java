package com.gonet.logging.error.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.web.PageResult;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.logging.error.dto.ErrorLog;
import com.gonet.logging.error.mapper.ErrorLogMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 에러 로그 적재 — logging_db 전용 TxManager + {@code REQUIRES_NEW} 격리 (conventions §3).
 *
 * <p>격리가 필수다. 이 기록은 <b>본 업무가 실패했거나 부가 기록이 실패한 순간</b>에 남는데,
 * 주 트랜잭션에 묶이면 그 롤백에 함께 휩쓸려 사라진다 — 정작 남겨야 할 때 없어진다.
 */
@Service
@RequiredArgsConstructor
public class ErrorLogServiceImpl extends AbstractCmsService implements ErrorLogService {

    private final ErrorLogMapper mapper;

    @Override
    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX,
            propagation = Propagation.REQUIRES_NEW)
    public void write(ErrorLog log) {
        mapper.insert(log);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX, readOnly = true)
    public PageResult<ErrorLog> getPage(ErrorLog.Search search) {
        int total = mapper.countPage(search);
        List<ErrorLog> rows = total == 0 ? List.of() : mapper.findPage(search);
        return new PageResult<>(rows, total, search.getPage(), search.getSize());
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX, readOnly = true)
    public List<ErrorLog.ClassCount> getClassCounts(int days) {
        return mapper.findClassCounts(Math.max(days, 1));
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX, readOnly = true)
    public ErrorLog get(Long logErrorId) {
        return logErrorId == null ? null : mapper.findById(logErrorId);
    }
}
