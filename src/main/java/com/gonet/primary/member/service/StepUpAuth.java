package com.gonet.primary.member.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * step-up 재인증 — 개인정보를 <b>보거나 고치기 직전에</b> 비밀번호를 한 번 더 묻는다.
 *
 * <p>로그인 세션만으로 개인정보 화면을 열면, 자리를 비운 사이 남이 브라우저를 만지거나
 * 세션이 탈취된 경우 그대로 열린다. 로그인과 개인정보 열람 사이에 한 겹을 더 두는 것이
 * 목적이며, 그래서 <b>유효시간이 짧다</b>(5분).
 *
 * <p>실패가 쌓이면 흔적을 지운다 — 남의 세션을 잡고 비밀번호를 대입하는 상황이라
 * 재인증 상태를 유지할 이유가 없다.
 */
@Component
@Slf4j
public class StepUpAuth {

    /** 재인증 통과 시각. */
    private static final String ATTR_AT = "GOPCMS_STEPUP_AT";
    /** 재인증 실패 누계. */
    private static final String ATTR_FAIL = "GOPCMS_STEPUP_FAIL";

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_FAIL = 5;

    /** 지금 재인증이 유효한가. */
    public boolean isVerified(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object at = session.getAttribute(ATTR_AT);
        if (!(at instanceof Instant verifiedAt)) {
            return false;
        }
        if (Instant.now().isAfter(verifiedAt.plus(TTL))) {
            // 만료된 흔적은 남겨 두지 않는다 — 남아 있으면 다음 판정이 헷갈린다
            session.removeAttribute(ATTR_AT);
            return false;
        }
        return true;
    }

    /** 재인증 성공 — 시계를 다시 켜고 실패 누계를 지운다. */
    public void markVerified(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        session.setAttribute(ATTR_AT, Instant.now());
        session.removeAttribute(ATTR_FAIL);
    }

    /**
     * 재인증 실패.
     *
     * @return 한도를 넘겨 흔적을 파기했으면 true
     */
    public boolean markFailed(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        int count = session.getAttribute(ATTR_FAIL) instanceof Integer c ? c + 1 : 1;
        session.setAttribute(ATTR_FAIL, count);

        if (count >= MAX_FAIL) {
            // 남의 세션에 비밀번호를 대입하는 상황 — 재인증 상태를 통째로 버린다
            session.removeAttribute(ATTR_AT);
            session.removeAttribute(ATTR_FAIL);
            log.warn("step-up 재인증 {}회 실패 — 재인증 상태 파기", count);
            return true;
        }
        return false;
    }

    /** 로그아웃·비밀번호 변경 등 세션 성격이 바뀔 때 함께 지운다. */
    public void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(ATTR_AT);
            session.removeAttribute(ATTR_FAIL);
        }
    }
}
