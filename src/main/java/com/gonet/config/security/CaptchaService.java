package com.gonet.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 잠금 해제 계정용 자동입력 방지 — <b>세션 바인딩 산술 문답</b>.
 *
 * <p>이미지 CAPTCHA 대신 텍스트 문답을 쓴 이유: 외부 서비스·이미지 렌더링 의존이 없고,
 * 스크린리더 사용자가 그대로 읽을 수 있어 접근성(KWCAG) 손실이 없다. 자동화 방어 강도는
 * 이미지보다 낮으므로 <b>잠금(5회 실패)을 겪은 계정에만</b> 요구한다 —
 * 무차별 대입의 다음 라운드를 늦추는 것이 목적이다.
 *
 * <p>정답은 세션에만 두고 <b>1회 사용 후 폐기</b>한다(재사용·리플레이 차단).
 * 이미지·음성 CAPTCHA 로의 승급은 공개 회원가입 도입 시 재검토.
 */
@Component
public class CaptchaService {

    /** 세션 키 — 정답(Integer) */
    private static final String ANSWER = "gopcms.captcha.answer";

    /** 세션 키 — 이 세션에 CAPTCHA 를 요구해야 하는가(로그인 폼 렌더 판단) */
    private static final String REQUIRED = "gopcms.captcha.required";

    /** 로그인 폼이 제출하는 파라미터명 */
    public static final String PARAM = "captchaAnswer";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 새 문제를 발급하고 질문 문구를 돌려준다 — 정답은 세션에 보관. */
    public String issue(HttpSession session) {
        int left = 1 + RANDOM.nextInt(9);
        int right = 1 + RANDOM.nextInt(9);
        session.setAttribute(ANSWER, left + right);
        return "%d + %d = ?".formatted(left, right);
    }

    /** 이 세션에서 로그인 폼에 CAPTCHA 를 노출해야 하는가. */
    public boolean isRequired(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(REQUIRED));
    }

    /** CAPTCHA 를 요구하도록 표시 — 대상 계정이 잠금 이력을 가진 것으로 확인됐을 때. */
    public void require(HttpServletRequest request) {
        request.getSession(true).setAttribute(REQUIRED, Boolean.TRUE);
    }

    /** 요구 해제 — 로그인 성공 시. */
    public void clear(HttpSession session) {
        if (session != null) {
            session.removeAttribute(REQUIRED);
            session.removeAttribute(ANSWER);
        }
    }

    /**
     * 제출 답 검증 — 정답은 성공·실패 무관하게 즉시 폐기한다(같은 답 재사용 차단).
     * 세션에 문제가 없으면(폼을 거치지 않은 직접 POST) 실패로 본다.
     */
    public boolean verify(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object expected = session.getAttribute(ANSWER);
        session.removeAttribute(ANSWER);
        if (expected == null) {
            return false;
        }
        try {
            return expected.equals(Integer.valueOf(request.getParameter(PARAM).trim()));
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }
}
