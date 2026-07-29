package com.gonet.config.retention;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 보존기간 정책 — {@code application.yml} 의 {@code gopcms.retention.*}.
 *
 * <p><b>왜 한 곳에 모으는가</b>: 보존기간이 코드 상수로 흩어지면 정책이 바뀔 때 반드시
 * 하나를 빠뜨린다. 빠뜨린 쪽은 조용히 옛 기간으로 계속 돌아가고, 그 사실은 감사 때
 * 드러난다. 값이 한 곳에 있으면 "지금 우리 정책이 무엇인가" 를 파일 하나로 답할 수 있다.
 *
 * <p>정책 요약(2026-07-29 사용자 확정, PLAN §P10-7):
 * <table border="1">
 *   <caption>대상별 보존기간</caption>
 *   <tr><th>대상</th><th>보존</th><th>근거</th></tr>
 *   <tr><td>회원 PII 본체</td><td>즉시 파기</td><td>보유량이 적을수록 유출 피해가 작다</td></tr>
 *   <tr><td>개인정보 접근·파기 이력</td><td>5년</td><td>파기했다는 사실 자체의 증빙</td></tr>
 *   <tr><td>탈퇴 원장</td><td>36개월</td><td>재가입 제한·분쟁 대응</td></tr>
 *   <tr><td>나머지 로그</td><td>36개월</td><td>사고 추적</td></tr>
 *   <tr><td>통계 {@code stat_*}</td><td>영구</td><td>식별정보 없는 집계값 — 파기 대상이 아니다</td></tr>
 * </table>
 *
 * <p>{@code stat_*} 에 대응하는 필드가 없는 것은 누락이 아니다. 영구 보존이라 기간이라는
 * 개념 자체가 없고, 값을 두면 누군가 그것을 파기 기준으로 쓰게 된다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.retention")
public class RetentionProperties {

    /** 탈퇴 원장({@code tb_member_withdraw}) — 36개월. */
    private int withdrawMonths = 36;

    /**
     * 개인정보 접근·파기 이력 — 5년.
     *
     * <p>다른 로그보다 길다. 이 둘은 "우리가 개인정보를 어떻게 다뤘는가" 를 증명하는
     * 자료라서, 사고가 늦게 드러나도 소명할 수 있어야 한다.
     */
    private int privacyLogMonths = 60;

    /** 나머지 {@code log_*} — 36개월. */
    private int logMonths = 36;

    /**
     * 로그인 이력({@code tb_login_history}) — 36개월.
     *
     * <p>성격은 로그인데 <b>primary_db</b> 에 있다(P6-3). 그래서 파기 배치가 다른
     * DataSource·TxManager 를 탄다 — 같은 36개월이라도 별도 키로 둔 이유다.
     */
    private int loginHistoryMonths = 36;

    private Purge purge = new Purge();

    /**
     * 파기 배치 안전장치.
     *
     * <p>되돌릴 수 없는 배치의 기본값은 전부 "아무것도 지우지 않는" 쪽이다. 배치를 처음
     * 켜는 순간 오래된 기록이 한꺼번에 사라지는 것이 가장 흔한 사고다.
     */
    @Getter
    @Setter
    public static class Purge {

        /** 배치 자체를 끌 수 있다 — 스케줄만 멈춘다. */
        private boolean enabled = true;

        /** 기본 켜짐 — 대상 건수만 로그에 남기고 실제로 지우지 않는다. */
        private boolean dryRun = true;

        /** 테이블 1개당 1회 삭제 상한. 잘못 돌아도 피해가 한 배치로 제한된다. */
        private int batchSize = 1000;

        private String cron = "0 20 4 * * *";
    }
}
