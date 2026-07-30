package com.gonet.logging.error.service;

import com.gonet.common.web.ClientIpResolver;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.common.web.SiteContextHolder;
import com.gonet.logging.error.dto.ErrorLog;
import com.gonet.primary.site.dto.SiteContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 실패를 {@code log_error} 로 끌어올린다 — <b>조용히 삼킨 실패를 드러내기 위한 단일 창구</b>.
 *
 * <p>이 프로젝트는 부가 기록(접근·개인정보취급·파기 이력)의 적재 실패를 삼키고 본 업무를
 * 계속한다. 로그 하나 때문에 사용자 업무가 멈추면 손해가 더 크기 때문이다. 그런데 삼킨
 * 실패가 애플리케이션 로그 파일에만 남으면 아무도 보지 않는다 — 그래서 여기로 모아
 * 관리자 화면에서 확인할 수 있게 한다({@code /adm/error-log}).
 *
 * <h3>이 클래스는 자기 실패도 감당해야 한다</h3>
 * 부가 기록이 깨진 이유가 <b>logging_db 자체의 장애</b>인 경우가 흔하다. 그러면 그 실패를
 * 같은 DB 의 {@code log_error} 에 적으려는 시도도 함께 실패한다. 그래서 두 겹으로 싼다:
 * <ol>
 *   <li>DB 적재 시도 — 성공하면 관리자 화면에서 보인다</li>
 *   <li>실패하면 <b>파일 로그</b>에만 남긴다 — 최후 수단(logback-spring.xml 의 gopcms-error.log)</li>
 * </ol>
 * 절대 예외를 밖으로 던지지 않는다. 던지면 "로그를 남기려다 업무를 깨뜨리는" 원래 문제로
 * 되돌아간다.
 *
 * <h3>삼키면 안 되는 것</h3>
 * <b>법정 기록은 이 창구를 쓰지 않는다.</b> 탈퇴 원장({@code tb_member_withdraw})과 동의
 * 이력({@code tb_member_consent}) 적재가 실패하면 그 트랜잭션은 <b>반드시 롤백</b>돼야 한다 —
 * 근거 없이 PII 를 파기하거나 동의 없이 가입을 성립시키면 안 되기 때문이다. 삼킬 대상은
 * "있으면 좋은 기록" 이고, 없으면 안 되는 기록은 실패를 그대로 전파한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorLogger {

    /** 스택트레이스 절단 — DDL 권고(32KB). 원문 보관이 목적이 아니다. */
    private static final int STACK_MAX = 32_000;
    private static final int MESSAGE_MAX = 2_000;
    private static final int URI_MAX = 500;
    private static final int QUERY_MAX = 500;
    private static final int UA_MAX = 500;

    /**
     * 쿼리스트링에서 가릴 파라미터 이름.
     *
     * <p>이 프로젝트의 쿼리스트링에는 실제로 민감한 값이 실린다 — 마스킹 해제 사유
     * ({@code ?reason=}), 소셜 로그인 state, 본인인증 EncodeData. 에러 로그가 그것을
     * 그대로 보관하면 기록이 새 유출 경로가 된다.
     */
    private static final Set<String> MASKED_PARAMS = Set.of(
            "reason", "state", "code", "encodedata", "token", "password",
            "_csrf", "email", "phone", "next");

    /** 고빈도 채널의 DB 적재 최소 간격. */
    private static final long THROTTLE_MS = 60_000L;

    private final ErrorLogService errorLogService;
    private final ClientIpResolver clientIpResolver;

    /** 채널별 스로틀 상태 — {@link #logRecordFailureThrottled} 만 사용한다. */
    private final ConcurrentHashMap<String, Gate> gates = new ConcurrentHashMap<>();

    private record Gate(AtomicLong lastAt, AtomicLong suppressed) {
        Gate() {
            this(new AtomicLong(0L), new AtomicLong(0L));
        }
    }

    /**
     * 부가 기록 적재가 실패했다 — 업무는 계속 진행된 상태다.
     *
     * @param channel 실패한 기록 채널(예: {@code PII_PURGE_LOG}) — 분류 키가 된다
     * @param detail  무엇을 남기려다 실패했는지(회원 ID 등 식별자 수준)
     */
    public void logRecordFailure(String channel, String detail, Throwable cause) {
        write("RECORD_FAILURE:" + channel, detail, cause, null);
    }

    /**
     * 같은 뜻이지만 채널별로 {@value #THROTTLE_MS}ms 에 한 건만 적재한다.
     *
     * <p><b>요청마다 불리는 자리</b>(접근 로그 필터)를 위한 것이다. 거기서 그대로
     * 적재하면 logging_db 장애 한 번에 요청 수만큼 {@code log_error} 행이 쌓인다 —
     * 스택트레이스가 mediumtext 라 장애 대응 중에 디스크를 밀어버릴 수 있다.
     *
     * <p>억제한 건수는 버리지 않고 다음에 적재되는 행의 상세에 함께 적는다 —
     * "1건만 보이는데 실제 규모는 얼마인가" 를 알 수 있어야 한다.
     */
    public void logRecordFailureThrottled(String channel, String detail, Throwable cause) {
        Gate gate = gates.computeIfAbsent(channel, k -> new Gate());
        long now = System.currentTimeMillis();
        long prev = gate.lastAt().get();
        if (now - prev < THROTTLE_MS || !gate.lastAt().compareAndSet(prev, now)) {
            gate.suppressed().incrementAndGet();
            return;
        }
        long suppressed = gate.suppressed().getAndSet(0L);
        logRecordFailure(channel,
                suppressed == 0 ? detail : detail + " (직전 구간 억제 " + suppressed + "건)",
                cause);
    }

    /** 미처리 예외 등 일반 오류. */
    public void logError(Throwable cause, Integer statusCode) {
        write(cause == null ? "UnknownError" : cause.getClass().getName(),
                cause == null ? null : cause.getMessage(), cause, statusCode);
    }

    private void write(String errorClass, String message, Throwable cause, Integer statusCode) {
        ErrorLog row = null;
        try {
            row = build(errorClass, message, cause, statusCode);
            errorLogService.write(row);
        } catch (RuntimeException e) {
            // logging_db 가 죽어 있는 상황이 대표적이다 — 파일 로그가 최후 수단이다.
            // 여기서 다시 DB 를 두드리지 않는다(무한 재귀).
            log.error("에러 로그 적재 실패 — 파일에만 남긴다. class={} message={} 원인={}",
                    errorClass, message, e.toString());
            if (cause != null) {
                log.error("적재하지 못한 원본 예외", cause);
            }
        } catch (Throwable t) {
            // Error 계열까지 막는다 — 로깅이 프로세스를 끌고 내려가면 안 된다
            log.error("에러 로그 적재 중 치명적 오류 class={}", errorClass, t);
        }
    }

    private ErrorLog build(String errorClass, String message, Throwable cause,
            Integer statusCode) {
        ErrorLog row = new ErrorLog();
        row.setErrorClass(trim(errorClass, 255));
        row.setErrorMessage(trim(message, MESSAGE_MAX));
        row.setStackTrace(stackTrace(cause));
        row.setStatusCode(statusCode);
        row.setTraceId(MDC.get("traceId"));

        SiteContext site = SiteContextHolder.get();
        if (site != null) {
            row.setSiteId(site.getSiteId());
            row.setSiteCode(site.getSiteCode());
        }

        HttpServletRequest request = currentRequest();
        if (request != null) {
            row.setRequestUri(trim(request.getRequestURI(), URI_MAX));
            row.setHttpMethod(request.getMethod());
            row.setQueryString(trim(maskQuery(request.getQueryString()), QUERY_MAX));
            row.setClientIp(clientIpResolver.resolve(request));
            row.setUserAgent(trim(request.getHeader("User-Agent"), UA_MAX));
            HttpSession session = request.getSession(false);
            if (session != null) {
                // 세션 ID 전체를 남기지 않는다 — 유출 시 세션 탈취 재료가 된다
                String id = session.getId();
                row.setSessionId(id.length() <= 8 ? id : id.substring(id.length() - 8));
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginPrincipal principal) {
            row.setActorUserId(principal.userId());
            row.setActorUserType(principal.userType());
            row.setActorLoginId(principal.loginId());
            row.setCreatedBy(principal.userId());
        } else {
            row.setActorUserType("ANONYMOUS");
        }
        row.setCreatedIp(row.getClientIp());
        return row;
    }

    /** 요청 밖(배치·스케줄러)에서 불릴 수 있다 — 없으면 null 이고 그대로 진행한다. */
    private HttpServletRequest currentRequest() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            return attrs instanceof ServletRequestAttributes servlet
                    ? servlet.getRequest() : null;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /**
     * 민감 파라미터 값을 가린다 — 이름은 남기고 값만 지운다.
     *
     * <p>이름을 남기는 이유: "어떤 파라미터가 실려 있었나" 는 원인 파악에 필요하고,
     * 이름 자체는 민감하지 않다.
     */
    private String maskQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return null;
        }
        StringBuilder out = new StringBuilder(queryString.length());
        for (String pair : queryString.split("&")) {
            if (!out.isEmpty()) {
                out.append('&');
            }
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                out.append(pair);
                continue;
            }
            String name = pair.substring(0, eq);
            out.append(name).append('=');
            out.append(MASKED_PARAMS.contains(name.toLowerCase()) ? "***"
                    : pair.substring(eq + 1));
        }
        return out.toString();
    }

    private String stackTrace(Throwable cause) {
        if (cause == null) {
            return null;
        }
        StringWriter buffer = new StringWriter();
        cause.printStackTrace(new PrintWriter(buffer));
        return trim(buffer.toString(), STACK_MAX);
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
