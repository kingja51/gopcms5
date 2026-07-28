package com.gonet.primary.auth.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.auth.dto.LoginUser;
import com.gonet.primary.auth.mapper.PasswordMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 변경.
 *
 * <p>재사용 검사는 BCrypt 특성상 <b>해시 비교가 아니라 대조</b>다 — 같은 평문도 salt 가
 * 달라 해시가 다르므로, 최근 이력 해시 각각에 {@code matches()} 를 돌린다(직전 3개 + 현재).
 * 이력이 늘어도 비교 대상은 상수 개수로 묶어 비용을 고정한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class PasswordServiceImpl extends AbstractCmsService implements PasswordService {

    private final PasswordMapper passwordMapper;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    /** 쓰기 — writable override (트랜잭션 함정 규약). 교체·이력 적재를 원자로 묶는다. */
    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void change(String userType, String userId, String currentRaw, String newRaw) {
        boolean admin = "ADMIN".equals(userType);
        LoginUser user = currentUser(userType, userId);
        if (user == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }
        if (!passwordEncoder.matches(currentRaw, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        String violation = PasswordPolicy.violation(newRaw);
        if (violation != null) {
            throw new IllegalArgumentException(violation);
        }
        if (isReused(admin, userId, user.getPassword(), newRaw)) {
            throw new IllegalArgumentException(
                    "최근 사용한 비밀번호는 다시 사용할 수 없습니다.");
        }

        String encoded = passwordEncoder.encode(newRaw);
        String historyId = Uid.next(admin ? UidPrefix.APH : UidPrefix.MPH);
        // 이력에 남기는 것은 <b>물러나는</b> 비밀번호다. 새 비밀번호를 넣으면 방금 버린 값이
        // 어디에도 남지 않아 곧바로 되돌릴 수 있다(P6-3 실측 결함). 새 값은 "현재"로서
        // isReused 가 따로 대조하므로, 이 방식이 지나온 비밀번호 전부를 덮는다.
        if (admin) {
            passwordMapper.insertAdminHistory(historyId, userId, user.getPassword());
            passwordMapper.updateAdminPassword(userId, encoded, PasswordPolicy.VALID_DAYS);
        } else {
            passwordMapper.insertMemberHistory(historyId, userId, user.getPassword());
            passwordMapper.updateMemberPassword(userId, encoded, PasswordPolicy.VALID_DAYS);
        }
    }

    /** 인증 원천은 vw_user_login 단일 조회 — 계정 테이블을 직접 보지 않는다(P6 계약). */
    private LoginUser currentUser(String userType, String userId) {
        return authService.findLoginUserById(userType, userId);
    }

    private boolean isReused(boolean admin, String userId, String currentHash, String newRaw) {
        List<String> candidates = new ArrayList<>();
        candidates.add(currentHash);
        candidates.addAll(admin
                ? passwordMapper.findRecentAdminHashes(userId, PasswordPolicy.HISTORY_DEPTH)
                : passwordMapper.findRecentMemberHashes(userId, PasswordPolicy.HISTORY_DEPTH));
        return candidates.stream().anyMatch(hash -> passwordEncoder.matches(newRaw, hash));
    }
}
