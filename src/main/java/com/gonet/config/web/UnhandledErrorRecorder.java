package com.gonet.config.web;

import com.gonet.logging.error.service.ErrorLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * 미처리 예외를 {@code log_error} 에 적어 두는 관찰자 — <b>처리는 하지 않는다</b>.
 *
 * <p>{@code null} 을 돌려주므로 오류 응답은 기존 흐름(Boot 의 기본 오류 처리)이 그대로
 * 만든다. 응답을 바꾸지 않고 기록만 얹기 위해 {@code @ControllerAdvice} 대신
 * {@link HandlerExceptionResolver} 를 골랐다 — {@code @ExceptionHandler} 를 두면
 * 그 순간부터 오류 화면의 주인이 바뀐다.
 *
 * <p>{@code detectAllHandlerExceptionResolvers} 기본값(true) 덕분에 이 빈은
 * {@code HandlerExceptionResolverComposite}(order 0)보다 앞서 호출된다.
 *
 * <h3>무엇을 남기지 않는가</h3>
 * <ul>
 *   <li>{@link ErrorResponse} 계열 — 잘못된 요청·없는 경로 등 Spring MVC 가 4xx 로
 *       규정한 예외다. 남기면 봇의 404 탐색만으로 표가 가득 찬다</li>
 *   <li>{@link AccessDeniedException} — 인가 거부는 정상 동작이고, 그 기록은
 *       접근 로그({@code log_access})의 상태코드가 이미 갖고 있다</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class UnhandledErrorRecorder implements HandlerExceptionResolver {

    private final ErrorLogger errorLogger;

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        if (!(ex instanceof ErrorResponse) && !(ex instanceof AccessDeniedException)) {
            // ErrorLogger 가 스스로 삼킨다 — 기록이 오류 응답 흐름을 깨지 않는다
            errorLogger.logError(ex, 500);
        }
        return null;
    }
}
