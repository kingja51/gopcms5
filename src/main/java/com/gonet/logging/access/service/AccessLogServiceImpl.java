package com.gonet.logging.access.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.logging.access.dto.AccessLog;
import com.gonet.logging.access.mapper.AccessLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접근 로그 적재 — logging_db 전용 TxManager + REQUIRES_NEW 격리 (conventions §3:
 * 로그 쓰기는 주 트랜잭션과 격리 — 로그 실패가 본 트랜잭션을 롤백시키지 않는다).
 */
@Service
@RequiredArgsConstructor
public class AccessLogServiceImpl extends AbstractCmsService implements AccessLogService {

    private final AccessLogMapper accessLogMapper;

    @Override
    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX,
            propagation = Propagation.REQUIRES_NEW)
    public void write(AccessLog accessLog) {
        accessLogMapper.insert(accessLog);
    }
}
