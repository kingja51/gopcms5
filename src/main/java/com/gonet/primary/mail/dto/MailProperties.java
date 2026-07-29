package com.gonet.primary.mail.dto;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 메일 발송 설정 — {@code gopcms.mail.*}.
 *
 * <p>{@code enabled=false} 가 기본이다. SMTP 계정 없이 기동해도 서비스가 죽지 않아야 하고,
 * 개발 중에 실제 메일이 나가는 사고를 막는다 — 꺼져 있으면 발송 대신 <b>로그에 남긴다</b>.
 */
@Component
@ConfigurationProperties(prefix = "gopcms.mail")
public class MailProperties {

    /** 실제 발송 여부. false 면 내용만 로그로 남긴다(개발 기본값). */
    private boolean enabled = false;

    /** 발신 주소 — 템플릿에 sender_email 이 없을 때 쓰는 기본값. */
    private String from = "no-reply@gopcms.local";

    /** 발신자 표시명 기본값. */
    private String fromName = "GOPCMS";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }
}
