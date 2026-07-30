package com.gonet.config.web;

import com.gonet.logging.audit.service.AuditTrailRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 확장 등록.
 *
 * <p>{@code @EnableWebMvc} 를 붙이지 <b>않는다</b> — 붙이면 Boot 의 MVC 자동구성이
 * 통째로 꺼지고 뷰 리졸버·메시지 컨버터·정적 자원 매핑을 직접 세워야 한다.
 * {@code WebMvcConfigurer} 만 구현하면 자동구성에 얹히는 방식이 된다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuditTrailRecorder auditTrailRecorder;

    /**
     * 감사 로그 인터셉터 — {@code /adm/**} 만.
     *
     * <p>대상을 관리자 네임스페이스로 좁힌 근거는 테이블 주석이다:
     * {@code log_audit} = "관리자 CUD 전수". 사용자 화면의 쓰기(게시글·댓글)는
     * 도메인 데이터 자체가 감사컬럼 6종을 갖고 있어 별도 기록이 중복이 된다.
     *
     * <p>제외 경로
     * <ul>
     *   <li>{@code /adm/login} — 로그인 시도는 {@code tb_login_history} 가 성공·실패·
     *       사유·잠금까지 담당한다. 여기 또 남기면 같은 사실이 두 테이블에 갈린다</li>
     *   <li>{@code /adm/logout} — 같은 이유</li>
     * </ul>
     * 새 관리자 화면은 <b>등록이 필요 없다</b> — 패턴이 {@code /adm/**} 이라 자동으로 걸린다.
     * 그것이 "전수" 를 코드 규율이 아니라 구조로 보장하는 방법이다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuditTrailInterceptor(auditTrailRecorder))
                .addPathPatterns("/adm/**")
                .excludePathPatterns("/adm/login", "/adm/logout");
    }
}
