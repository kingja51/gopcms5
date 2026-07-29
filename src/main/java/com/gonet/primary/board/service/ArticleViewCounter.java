package com.gonet.primary.board.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 조회수 중복 방지 — 같은 브라우저가 30분 안에 다시 열면 세지 않는다.
 *
 * <p>세션이 아니라 쿠키를 쓰는 이유: 비로그인 열람이 대부분인데 세션을 만들면 조회 한 번에
 * 세션이 하나씩 생긴다. 쿠키 한 장에 읽은 글 ID 를 모아 두면 그 비용이 없다.
 *
 * <p>정확한 집계 수단이 아니다(쿠키를 지우면 다시 센다). 목적은 새로고침 한 번에 조회수가
 * 계속 오르는 것을 막는 것이며, 통계는 별도의 접근 로그가 담당한다.
 */
@Component
@RequiredArgsConstructor
public class ArticleViewCounter {

    private static final String COOKIE = "GOPCMS_BBS_VIEW";
    private static final int TTL_SECONDS = 30 * 60;
    /** 쿠키 4KB 한계를 넘지 않도록 — 41자×80 ≈ 3.2KB. 넘치면 오래된 것부터 버린다. */
    private static final int MAX_IDS = 80;

    private final BoardArticleService boardArticleService;

    /**
     * 필요하면 조회수를 올리고 쿠키를 갱신한다.
     *
     * @return 실제로 올렸으면 true
     */
    public boolean countOnce(String articleId, HttpServletRequest request,
            HttpServletResponse response) {
        String read = read(request);
        if (contains(read, articleId)) {
            return false;
        }
        boardArticleService.increaseViewCount(articleId);
        write(response, append(read, articleId));
        return true;
    }

    private String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return "";
        }
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName())) {
                return c.getValue() == null ? "" : c.getValue();
            }
        }
        return "";
    }

    /** 구분자로 감싸 비교한다 — 부분 문자열이 우연히 맞는 것을 막는다. */
    private boolean contains(String value, String articleId) {
        return ("|" + value + "|").contains("|" + articleId + "|");
    }

    private String append(String value, String articleId) {
        String merged = value.isEmpty() ? articleId : value + "|" + articleId;
        String[] ids = merged.split("\\|");
        if (ids.length <= MAX_IDS) {
            return merged;
        }
        return String.join("|", java.util.Arrays.copyOfRange(ids, ids.length - MAX_IDS, ids.length));
    }

    private void write(HttpServletResponse response, String value) {
        Cookie cookie = new Cookie(COOKIE, value);
        cookie.setPath("/");
        cookie.setMaxAge(TTL_SECONDS);
        cookie.setHttpOnly(true);          // JS 가 읽을 이유가 없다
        response.addCookie(cookie);
    }
}
