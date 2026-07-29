package com.gonet.primary.mail.dto;

import lombok.Getter;
import lombok.Setter;

/** tb_mail_template — 코드로 조회해 Thymeleaf 로 렌더링한다. */
@Getter
@Setter
public class MailTemplate {

    private String mailTemplateId;
    private String templateCode;
    private String templateName;
    private String subject;
    /** Thymeleaf 문법 HTML — {@code ${memberName}} 같은 모델 변수를 쓴다. */
    private String bodyHtml;
    private String senderEmail;
    private String senderName;
    private String variablesHint;
}
