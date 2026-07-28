package com.gonet.primary.auth.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.auth.dto.AdminAllowIp;
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
    public LoginUser findLoginUserById(String userType, String userId) {
        return authMapper.findLoginUserById(userType, userId);
    }

    @Override
    public boolean isIpAllowedForLoginForm(String clientIp) {
        return authMapper.findActiveAllowIps().stream().anyMatch(allow -> allow.matches(clientIp));
    }

    @Override
    public AdminAllowIp matchAllowIp(String adminId, String clientIp) {
        return authMapper.findActiveAllowIpsByAdmin(adminId).stream()
                .filter(allow -> allow.matches(clientIp))
                .findFirst().orElse(null);
    }

    @Override
    public boolean isTwoFactorRequired(String groupId) {
        return groupId != null && "Y".equals(authMapper.findTwoFactorRequired(groupId));
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void enableTwoFactor(String adminId, String encryptedSecret) {
        authMapper.enableTwoFactor(adminId, encryptedSecret);
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
    public void loginSucceeded(String userType, String userId, String clientIp, String allowIpId) {
        if ("ADMIN".equals(userType)) {
            authMapper.adminLoginSuccess(userId, clientIp);
            if (allowIpId != null) {
                authMapper.touchAllowIp(allowIpId);
            }
        } else {
            authMapper.memberLoginSuccess(userId, clientIp);
        }
    }
}
