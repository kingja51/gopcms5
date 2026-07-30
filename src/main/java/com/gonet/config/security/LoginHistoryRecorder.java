package com.gonet.config.security;

import com.gonet.common.web.ClientIpResolver;
import com.gonet.logging.error.service.ErrorLogger;
import com.gonet.primary.auth.dto.LoginHistory;
import com.gonet.primary.auth.service.LoginHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 로그인 이력 기록 창구 — 인증 Provider 는 이 클래스만 호출한다.
 *
 * <p>두 가지를 맡는다:
 * <ol>
 *   <li><b>요청 정보 보강</b> — IP({@link ClientIpResolver} 단일 경로)·User-Agent·세션 ID</li>
 *   <li><b>실패 격리</b> — 이력 적재 예외를 삼키고 WARN 만 남긴다. 서비스 메서드를 그대로
 *       try 로 감싸면 트랜잭션이 rollback-only 로 남으므로, 격리는 트랜잭션 <b>바깥</b>인
 *       이 별도 빈에서 한다(자기호출 프록시 우회 함정도 함께 피한다)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginHistoryRecorder {

    private final LoginHistoryService loginHistoryService;
    private final ClientIpResolver clientIpResolver;
    private final ErrorLogger errorLogger;

    public void success(String userType, String userId, String loginId, String siteId,
            String siteCode) {
        write(LoginHistory.builder()
                .userType(userType).userId(userId).loginId(loginId)
                .siteId(siteId).siteCode(siteCode)
                .result(LoginHistory.SUCCESS));
    }

    /**
     * @param result     {@code LoginHistory.FAIL_*} 상수
     * @param failReason 운영자용 상세 — 사용자 응답 메시지는 항상 일반화된 문구를 쓴다
     */
    public void failure(String userType, String userId, String loginId, String siteId,
            String siteCode, String result, String failReason) {
        write(LoginHistory.builder()
                .userType(userType).userId(userId).loginId(loginId)
                .siteId(siteId).siteCode(siteCode)
                .result(result).failReason(failReason));
    }

    private void write(LoginHistory.LoginHistoryBuilder builder) {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            HttpSession session = request.getSession(false);
            builder.clientIp(clientIpResolver.resolve(request))
                    .userAgent(cut(request.getHeader("User-Agent"), 500))
                    .sessionId(session == null ? null : tail(session.getId(), 8));
        }
        LoginHistory row = builder.build();
        try {
            loginHistoryService.record(row);
        } catch (Exception e) {
            log.warn("로그인 이력 적재 실패 — 인증 흐름은 계속: {}", e.getMessage());
            // 이력은 primary_db, 에러 로그는 logging_db — 한쪽이 죽어도 다른 쪽이 받는다
            errorLogger.logRecordFailure("LOGIN_HISTORY",
                    "loginId=%s result=%s".formatted(row.getLoginId(), row.getResult()), e);
        }
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder
                .getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    private String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private String tail(String value, int length) {
        return value == null || value.length() <= length
                ? value : value.substring(value.length() - length);
    }
}
