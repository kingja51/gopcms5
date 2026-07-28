package com.gonet.primary.auth.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.auth.dto.LoginUser;
import com.gonet.primary.auth.mapper.AuthMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class AuthServiceImpl extends AbstractCmsService implements AuthService {

    private final AuthMapper authMapper;

    @Override
    public LoginUser findLoginUser(String userType, String siteId, String loginId) {
        return authMapper.findLoginUser(userType, siteId, loginId);
    }

    @Override
    public boolean isIpAllowedForLoginForm(String clientIp) {
        return authMapper.countAllowIp(clientIp) > 0;
    }

    @Override
    public boolean isIpAllowedForAdmin(String adminId, String clientIp) {
        return authMapper.countAdminAllowIp(adminId, clientIp) > 0;
    }

    /** 쓰기 — writable override (트랜잭션 함정 규약) */
    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void loginFailed(String userType, String userId) {
        if ("ADMIN".equals(userType)) {
            authMapper.adminLoginFail(userId);
        } else {
            authMapper.memberLoginFail(userId);
        }
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void loginSucceeded(String userType, String userId, String clientIp) {
        if ("ADMIN".equals(userType)) {
            authMapper.adminLoginSuccess(userId, clientIp);
            authMapper.touchAllowIp(userId, clientIp);
        } else {
            authMapper.memberLoginSuccess(userId, clientIp);
        }
    }
}
