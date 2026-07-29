package com.gonet.logging.privacy.service;

import com.gonet.common.web.ClientIpResolver;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.logging.privacy.dto.PrivacyAccessLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 개인정보 취급 한 건을 이력으로 남긴다 — 호출부는 "무엇을 했는지" 만 넘긴다.
 *
 * <p>취급자·IP·세션·URI 는 여기서 채운다. 호출부마다 채우게 하면 어느 화면에서는
 * IP 가 비고 어느 화면에서는 세션이 비는 식으로 이력의 품질이 갈린다.
 *
 * <p><b>적재 실패는 삼킨다.</b> 이력이 안 남는다고 관리자 업무가 멈추면 손해가 더 크다.
 * 다만 삼킨 사실은 애플리케이션 로그에 남겨, 이력이 비는 구간을 나중에 찾을 수 있게 한다.
 *
 * <p>{@code target_entity} 는 테이블명이다 — 회원은 {@code tb_member}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrivacyAccessLogger {

    public static final String ENTITY_MEMBER = "tb_member";

    /** 목록·상세에서 통상 노출되는 항목 — 무엇까지 봤는지의 기본값. */
    public static final String FIELDS_BASIC = "name,email,phone";
    public static final String FIELDS_FULL = "name,email,phone,birth,address,di";

    private final PrivacyAccessLogService logService;
    private final ClientIpResolver clientIpResolver;

    /** 한 사람의 상세를 열었다. */
    public void read(HttpServletRequest request, String targetId, String piiFields) {
        write(request, PrivacyAccessLog.ACTION_READ, targetId, 1, piiFields, null,
                PrivacyAccessLog.RESULT_SUCCESS, null);
    }

    /** 목록·검색으로 여러 건을 훑었다 — 대상이 여럿이라 targetId 는 없다. */
    public void search(HttpServletRequest request, int count) {
        write(request, PrivacyAccessLog.ACTION_SEARCH, null, count, FIELDS_BASIC, null,
                PrivacyAccessLog.RESULT_SUCCESS, null);
    }

    /** 마스킹을 풀어 원본을 봤다 — 사유가 반드시 함께 남는다. */
    public void decrypt(HttpServletRequest request, String targetId, String reason) {
        write(request, PrivacyAccessLog.ACTION_DECRYPT, targetId, 1, FIELDS_FULL, reason,
                PrivacyAccessLog.RESULT_SUCCESS, null);
    }

    /** 파일로 내보냈다 — 통제 밖으로 나간 기록이라 건수와 사유가 핵심이다. */
    public void export(HttpServletRequest request, int count, String reason) {
        write(request, PrivacyAccessLog.ACTION_EXPORT, null, count, FIELDS_BASIC, reason,
                PrivacyAccessLog.RESULT_SUCCESS, null);
    }

    /** 상태 변경·비밀번호 초기화 등 정보주체에 영향을 준 처리. */
    public void update(HttpServletRequest request, String targetId, String reason) {
        write(request, PrivacyAccessLog.ACTION_UPDATE, targetId, 1, null, reason,
                PrivacyAccessLog.RESULT_SUCCESS, null);
    }

    /** 강제 탈퇴 등 파기로 이어지는 처리. */
    public void delete(HttpServletRequest request, String targetId, String reason) {
        write(request, PrivacyAccessLog.ACTION_DELETE, targetId, 1, FIELDS_FULL, reason,
                PrivacyAccessLog.RESULT_SUCCESS, null);
    }

    /**
     * 막은 시도 — 사유 미기재·상한 초과 등.
     *
     * <p>성공만 기록하면 "사유 없이 반복해서 내려받기를 시도하는" 패턴이 이력에
     * 아예 나타나지 않는다. 거부도 신호다.
     */
    public void denied(HttpServletRequest request, String action, String targetId,
            String failReason) {
        write(request, action, targetId, 1, null, null,
                PrivacyAccessLog.RESULT_DENIED, failReason);
    }

    private void write(HttpServletRequest request, String action, String targetId, int count,
            String piiFields, String reason, String result, String failReason) {
        try {
            PrivacyAccessLog row = new PrivacyAccessLog();
            row.setTargetEntity(ENTITY_MEMBER);
            row.setTargetUserType("MEMBER");
            row.setTargetId(targetId);
            row.setTargetCount(count);
            row.setPiiFields(piiFields);
            row.setAccessAction(action);
            row.setAccessReason(trim(reason, 2000));
            row.setResult(result);
            row.setFailReason(trim(failReason, 500));
            row.setTraceId(MDC.get("traceId"));

            if (request != null) {
                row.setRequestUri(trim(request.getRequestURI(), 1000));
                row.setHttpMethod(request.getMethod());
                row.setClientIp(clientIpResolver.resolve(request));
                row.setUserAgent(trim(request.getHeader("User-Agent"), 500));
                HttpSession session = request.getSession(false);
                if (session != null) {
                    row.setSessionId(session.getId());
                }
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof LoginPrincipal principal) {
                row.setActorUserId(principal.userId());
                row.setActorUserType(principal.userType());
                row.setActorLoginId(principal.loginId());
            }
            // actor_user_id·client_ip 는 NOT NULL — 인증 밖에서 불리면 적재가 깨진다.
            // 그럴 일은 없어야 하지만, 깨지는 대신 표시를 남기는 편이 낫다.
            if (row.getActorUserId() == null) {
                row.setActorUserId("UNKNOWN");
                row.setActorUserType("SYSTEM");
            }
            if (row.getClientIp() == null) {
                row.setClientIp("-");
            }
            logService.write(row);   // 별도 빈 — 자기호출이면 REQUIRES_NEW 가 무시된다
        } catch (RuntimeException e) {
            log.warn("개인정보 접근 이력 적재 실패(업무는 계속) action={} target={}: {}",
                    action, targetId, e.toString());
        }
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
