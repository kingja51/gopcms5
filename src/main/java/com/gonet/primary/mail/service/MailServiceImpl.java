package com.gonet.primary.mail.service;

import com.gonet.primary.mail.dto.MailProperties;
import com.gonet.primary.mail.dto.MailTemplate;
import com.gonet.primary.mail.mapper.MailTemplateMapper;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * 메일 발송 — DB 템플릿을 Thymeleaf 로 렌더링해 보낸다.
 *
 * <p>본문을 코드가 아니라 {@code tb_mail_template} 에 두는 이유: 문구는 운영 중에 자주
 * 바뀌고, 그때마다 배포할 수는 없다.
 *
 * <p><b>전용 엔진</b>을 쓴다. 화면용 엔진은 {@code classpath:/templates/} 를 뒤지는
 * 리졸버가 달려 있어 DB 문자열을 넘길 수 없다. 여기서는 문자열 자체를 템플릿으로 읽는
 * {@link StringTemplateResolver} 만 붙인 엔진을 따로 만든다.
 *
 * <p>{@code gopcms.mail.enabled=false}(기본)면 실제로 보내지 않고 로그에 남긴다 —
 * SMTP 계정 없이도 기동하고, 개발 중 실메일 발송 사고를 막는다.
 */
@Service
@Slf4j
public class MailServiceImpl implements MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MailTemplateMapper mailTemplateMapper;
    private final MailProperties properties;
    private final TemplateEngine engine;

    public MailServiceImpl(ObjectProvider<JavaMailSender> mailSenderProvider,
            MailTemplateMapper mailTemplateMapper, MailProperties properties) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailTemplateMapper = mailTemplateMapper;
        this.properties = properties;

        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        // DB 문자열은 매번 다르므로 캐시하지 않는다(키가 곧 본문이라 캐시가 메모리만 먹는다)
        resolver.setCacheable(false);
        TemplateEngine mailEngine = new TemplateEngine();
        mailEngine.setTemplateResolver(resolver);
        this.engine = mailEngine;
    }

    /**
     * {@code NOT_SUPPORTED} — 호출측 트랜잭션에 참여하지 않는다.
     *
     * <p>@Async 로 스레드가 갈리므로 어차피 전파되지 않지만, 명시해 두는 편이 의도가 분명하다.
     * 발송 실패는 <b>삼킨다</b> — 메일이 안 갔다고 가입이나 비밀번호 재설정을 되돌릴 수는 없다.
     * 대신 로그에 남겨 추적한다.
     */
    @Override
    @Async
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendAsync(String templateCode, String to, Map<String, Object> model) {
        try {
            MailTemplate template = mailTemplateMapper.findByCode(templateCode);
            if (template == null) {
                log.error("메일 템플릿 없음 code={} to={}", templateCode, mask(to));
                return;
            }
            Context context = new Context();
            if (model != null) {
                model.forEach(context::setVariable);
            }
            String subject = render(template.getSubject(), context);
            String body = render(template.getBodyHtml(), context);

            if (!properties.isEnabled()) {
                // 발송 대신 기록 — 본문에 임시 비밀번호 같은 값이 들어 있어 전문은 남기지 않는다
                log.info("[메일 미발송(enabled=false)] code={} to={} subject={}",
                        templateCode, mask(to), subject);
                return;
            }
            JavaMailSender sender = mailSenderProvider.getIfAvailable();
            if (sender == null) {
                log.error("JavaMailSender 가 없습니다 — spring.mail.* 설정을 확인하세요 (code={})",
                        templateCode);
                return;
            }
            send(sender, template, to, subject, body);
            log.info("메일 발송 code={} to={}", templateCode, mask(to));
        } catch (Exception e) {
            // 여기서 예외가 새면 @Async 스레드가 죽을 뿐 호출측은 이미 커밋됐다 —
            // 조용히 사라지지 않도록 반드시 로그로 남긴다
            log.error("메일 발송 실패 code={} to={} : {}", templateCode, mask(to), e.toString());
        }
    }

    private void send(JavaMailSender sender, MailTemplate template, String to,
            String subject, String body) throws Exception {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false,
                StandardCharsets.UTF_8.name());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, true);
        setFrom(helper, template);
        sender.send(message);
    }

    /** 템플릿에 발신자가 있으면 그것을, 없으면 설정 기본값을 쓴다. */
    private void setFrom(MimeMessageHelper helper, MailTemplate template)
            throws jakarta.mail.MessagingException, UnsupportedEncodingException {
        String from = template.getSenderEmail() == null || template.getSenderEmail().isBlank()
                ? properties.getFrom() : template.getSenderEmail();
        String name = template.getSenderName() == null || template.getSenderName().isBlank()
                ? properties.getFromName() : template.getSenderName();
        helper.setFrom(from, name);
    }

    private String render(String source, Context context) {
        return source == null ? "" : engine.process(source, context);
    }

    /** 로그에 수신 주소를 통째로 남기지 않는다 — 로그도 개인정보 보관처다. */
    private String mask(String email) {
        if (email == null || email.isBlank()) {
            return "(없음)";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "*" + (at < 0 ? "" : email.substring(at));
        }
        return email.charAt(0) + "*".repeat(at - 1) + email.substring(at);
    }
}
