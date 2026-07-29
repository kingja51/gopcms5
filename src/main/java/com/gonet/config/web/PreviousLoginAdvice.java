package com.gonet.config.web;

import com.gonet.common.web.LoginPrincipal;
import com.gonet.primary.auth.dto.LoginHistory;
import com.gonet.primary.auth.mapper.LoginHistoryMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 관리자 화면 상단의 "이전 로그인 일시" 공급 — 계정 도용을 <b>본인이</b> 알아채는 장치.
 *
 * <p>서버가 아무리 잘 막아도 자격증명이 새면 정상 로그인으로 보인다. 그때 남는 유일한
 * 단서가 "내가 접속한 적 없는 시각"이라, 이 값은 보안 기능이지 장식이 아니다.
 *
 * <p><b>세션에 한 번만 캐시한다.</b> 값이 세션 동안 변하지 않는데(직전 로그인은 고정)
 * 관리자 페이지마다 조회하면 이력 테이블에 불필요한 부하가 쌓인다.
 *
 * <p>조회가 실패해도 화면은 떠야 한다 — 부가 정보 때문에 관리자 화면 전체가 500 이 되면
 * 손해가 더 크다. 그래서 예외를 삼키고 null 을 내보낸다(화면은 값이 없으면 감춘다).
 */
@ControllerAdvice(basePackages = "com.gonet")
@RequiredArgsConstructor
@Slf4j
public class PreviousLoginAdvice {

    /** 세션 캐시 키 — 값이 없다는 사실까지 캐시하려고 별도 플래그를 함께 둔다. */
    static final String SESSION_KEY = "GOPCMS_PREV_LOGIN";
    static final String SESSION_LOADED = "GOPCMS_PREV_LOGIN_LOADED";

    private final LoginHistoryMapper loginHistoryMapper;

    @ModelAttribute("previousLogin")
    public LoginHistory previousLogin(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/adm")) {
            return null;                       // 관리자 화면 전용 — 사용자 사이트는 조회조차 하지 않는다
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginPrincipal principal)) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        if (Boolean.TRUE.equals(session.getAttribute(SESSION_LOADED))) {
            return (LoginHistory) session.getAttribute(SESSION_KEY);
        }
        LoginHistory previous = null;
        try {
            previous = loginHistoryMapper.findPreviousSuccess(principal.userId());
        } catch (RuntimeException e) {
            log.warn("이전 로그인 조회 실패 — 화면은 계속 진행한다: {}", e.toString());
        }
        session.setAttribute(SESSION_KEY, previous);
        session.setAttribute(SESSION_LOADED, Boolean.TRUE);
        return previous;
    }
}
