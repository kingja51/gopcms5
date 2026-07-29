package com.gonet.primary.mail.service;

import java.util.Map;

/** 템플릿 기반 메일 발송. */
public interface MailService {

    /**
     * 템플릿 코드로 렌더링해 보낸다 — <b>비동기</b>.
     *
     * <p>호출한 트랜잭션과 분리된다. SMTP 는 외부 호출이라 느리고 실패할 수 있는데,
     * 그것 때문에 회원 가입이나 비밀번호 재설정이 통째로 롤백되면 안 된다
     * (CLAUDE.md 트랜잭션 함정 — 긴 외부호출은 격리한다).
     *
     * @param templateCode tb_mail_template.template_code
     * @param to           수신 주소(평문)
     * @param model        템플릿 변수 — variables_hint 에 적힌 이름들
     */
    void sendAsync(String templateCode, String to, Map<String, Object> model);
}
