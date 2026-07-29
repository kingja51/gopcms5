package com.gonet.primary.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NICE CheckPlus 안심본인인증 설정값. {@code application.yml} 의 {@code gopcms.nice.*}
 * 키와 1:1 매핑.
 *
 * <ul>
 *   <li>{@code site-code} / {@code site-password} — NICE 가입 시 부여받는 사이트 자격</li>
 *   <li>{@code return-url} — 인증 성공 시 NICE 가 POST 콜백할 절대 URL (콘솔 등록 URL과 동일)</li>
 *   <li>{@code error-url} — 인증 실패 시 NICE 가 POST 콜백할 절대 URL</li>
 *   <li>{@code auth-type} — {@code ""}(기본 선택화면) / M / X / U / F / S / C</li>
 *   <li>{@code customize} — {@code ""}(PC 기본) / "Mobile"</li>
 *   <li>{@code popup-url} — NICE 안심본인인증 팝업 진입 URL (변경 거의 없음)</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gopcms.nice")
public class NiceCheckProperties {

    /**
     * 본인인증 전역 토글.
     *
     * <p>{@code false} 면 가입 절차에서 본인인증 단계를 건너뛴다 — 계약 전 개발·시연용이다.
     * 운영에서 끄면 실명 확인 없이 가입이 열린다는 뜻이므로 기본값은 켜짐이다.
     */
    private boolean enabled = true;

    private String siteCode = "";
    private String sitePassword = "";
    private String returnUrl = "";
    private String errorUrl = "";
    private String authType = "";
    private String customize = "";
    private String popupUrl = "https://nice.checkplus.co.kr/CheckPlusSafeModel/checkplus.cb";
}
