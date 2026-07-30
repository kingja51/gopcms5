package com.gonet.config.web;

import com.gonet.logging.audit.service.AuditTrailRecorder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리자 상태 변경을 감사 로그로 남긴다 — {@code log_audit} 적재 진입점.
 *
 * <p><b>왜 필터가 아니라 인터셉터인가.</b> 감사 기록에는 인증 주체가 필요하고,
 * {@code AccessLogFilter} 처럼 시큐리티 체인 <b>바깥</b>에서 돌면 그 시점에
 * {@code SecurityContextHolder} 가 이미 비어 있다. 인터셉터는 DispatcherServlet 안,
 * 즉 시큐리티 체인 <b>안쪽</b>에서 돌아 주체를 직접 읽을 수 있다.
 *
 * <p><b>왜 {@code afterCompletion} 인가.</b> 응답 상태와 플래시 속성이 확정된 뒤여야
 * 결과(SUCCESS/FAIL/ERROR)를 판정할 수 있다. {@code postHandle} 은 뷰 렌더 전이라
 * 리다이렉트 결과가 아직 반영되지 않는다.
 *
 * <p>기록 대상은 <b>상태를 바꾸는 메서드</b>뿐이다. GET 조회를 여기 넣으면 감사 로그가
 * 접근 로그({@code log_access})의 사본이 되고, 정작 변경 이력이 조회 기록에 묻힌다.
 * 관리자 조회 기록이 필요한 영역(개인정보)은 {@code log_privacy_access} 가 담당한다.
 */
@RequiredArgsConstructor
public class AuditTrailInterceptor implements HandlerInterceptor {

    /** 상태를 바꾸지 않는 메서드 — 기록하지 않는다. */
    private static final Set<String> READ_ONLY = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final AuditTrailRecorder recorder;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        if (READ_ONLY.contains(request.getMethod())) {
            return;
        }
        int status = response.getStatus();
        // 성공 쓰기는 반드시 리다이렉트한다(PRG) — 2xx 는 폼 재표시, 즉 검증 실패다.
        // 판정 근거는 AuditTrailRecorder#looksFailed javadoc 참조.
        boolean failed = recorder.looksFailed(request, ex) || recorder.isFormRedisplay(status);
        // Recorder 가 스스로 삼킨다 — 감사 기록이 응답 흐름을 깨지 않는다
        recorder.record(request, status, failed);
    }
}
