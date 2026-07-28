package com.gonet.primary.auth.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.auth.dto.LoginHistory;
import com.gonet.primary.auth.mapper.LoginHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 이력 적재.
 *
 * <p>{@code REQUIRES_NEW} 인 이유: 실패 로그인 처리(잠금 카운트 증가)와 같은 트랜잭션에
 * 묶이면 한쪽이 롤백될 때 감사 기록까지 사라진다 — 인증 사건 기록은 독립적으로 남아야 한다.
 */
@Service
@RequiredArgsConstructor
public class LoginHistoryServiceImpl extends AbstractCmsService implements LoginHistoryService {

    private final LoginHistoryMapper loginHistoryMapper;

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX,
            propagation = Propagation.REQUIRES_NEW)
    public void record(LoginHistory history) {
        history.setLoginHistoryId(Uid.next(UidPrefix.LGH));
        loginHistoryMapper.insert(history);
    }
}
