package com.gonet.logging.privacy.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.logging.privacy.dto.PrivacyAccessLog;
import com.gonet.logging.privacy.mapper.PrivacyAccessLogMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인정보 접근 이력 적재 — logging_db 전용 TxManager + {@code REQUIRES_NEW} 격리
 * (conventions §3).
 *
 * <p>여기서 {@code REQUIRES_NEW} 가 특히 중요하다: 이력을 남기려던 대상 업무가
 * 롤백되더라도 <b>"보려고 시도했다" 는 사실은 남아야</b> 한다. 주 트랜잭션에 묶으면
 * 실패한 접근 시도가 이력에서 통째로 사라진다.
 */
@Service
@RequiredArgsConstructor
public class PrivacyAccessLogServiceImpl extends AbstractCmsService
        implements PrivacyAccessLogService {

    private final PrivacyAccessLogMapper mapper;

    @Override
    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX,
            propagation = Propagation.REQUIRES_NEW)
    public void write(PrivacyAccessLog log) {
        mapper.insert(log);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX, readOnly = true)
    public List<PrivacyAccessLog> recentByTarget(String targetEntity, String targetId,
            int limit) {
        return mapper.recentByTarget(targetEntity, targetId, limit);
    }
}
