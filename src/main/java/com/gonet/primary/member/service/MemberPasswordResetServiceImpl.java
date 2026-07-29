package com.gonet.primary.member.service;

import com.gonet.common.crypto.PiiHash;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.mail.service.MailService;
import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.mapper.MemberMapper;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 찾기.
 *
 * <p>핵심은 <b>임시 비밀번호를 즉시 만료 상태로 발급</b>하는 것이다. 로그인은 되지만
 * {@code password_expire_at} 이 과거라 인증 Provider 가 {@code FAIL_EXPIRED} 로 막고
 * 비밀번호 변경으로 유도한다(P6-3). 임시 비밀번호를 그대로 계속 쓰는 상태를 만들지 않는다.
 *
 * <p>메일로만 전달한다 — 화면에 띄우면 아이디·이메일만 아는 사람이 남의 계정을 가져간다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
@Slf4j
public class MemberPasswordResetServiceImpl extends AbstractCmsService
        implements MemberPasswordResetService {

    /**
     * 임시 비밀번호 문자 집합 — 혼동되는 글자를 뺐다.
     *
     * <p>{@code 0/O}, {@code 1/l/I} 은 메일로 받아 옮겨 적을 때 틀리기 쉽다. 재설정
     * 절차에서 오타가 나면 사용자는 "메일이 잘못 왔다" 고 생각하고 다시 요청한다.
     */
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final String SPECIALS = "!@#$%^&*";
    private static final int LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberMapper memberMapper;
    private final PiiHash piiHash;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void issueTemporaryPassword(String siteId, String loginId, String email) {
        if (siteId == null || loginId == null || loginId.isBlank()
                || email == null || email.isBlank()) {
            return;
        }
        MemberDto member = memberMapper.findByEmailHash(siteId, piiHash.hash(email));
        // 아이디까지 일치해야 한다. 없거나 어긋나면 조용히 끝낸다 —
        // 화면은 어느 경우든 같은 안내를 보여준다
        if (member == null || !loginId.trim().equals(member.getLoginId())) {
            log.info("비밀번호 찾기 — 일치하는 계정 없음 site={} loginId={}", siteId, loginId);
            return;
        }

        String temporary = generate();
        // 만료 시각을 과거로 둔다 → 로그인은 되지만 곧바로 변경 화면으로 간다
        memberMapper.updateTemporaryPassword(member.getMemberId(),
                passwordEncoder.encode(temporary), LocalDateTime.now().minusSeconds(1));

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("memberName", member.getMemberName());
        model.put("loginId", member.getLoginId());
        model.put("tempPassword", temporary);
        // 포맷하지 않고 LocalDateTime 그대로 넘긴다 — 템플릿이 #temporals.format 으로
        // 직접 찍는다(문자열을 주면 "Unable to convert String to Temporal" 로 파싱이 깨진다)
        model.put("sentAt", LocalDateTime.now());
        // 발송은 비동기 — SMTP 가 느리거나 실패해도 재설정 자체는 이미 커밋된다
        mailService.sendAsync("PASSWORD_RESET", member.getEmail(), model);

        log.info("임시 비밀번호 발급 member={} site={}", member.getMemberId(), siteId);
    }

    /**
     * 임시 비밀번호 생성 — 정책({@code PasswordPolicy})을 반드시 만족해야 한다.
     *
     * <p>무작위로 만들다 보면 특수문자가 하나도 안 들어가 2종 조합 10자 미만이 될 수 있다.
     * 그러면 사용자가 로그인한 뒤 <b>변경 화면에서 거부되는</b> 이상한 상태가 된다.
     * 그래서 특수문자·숫자를 한 자리씩 보장한다.
     */
    private String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        sb.append(SPECIALS.charAt(RANDOM.nextInt(SPECIALS.length())));
        sb.append((char) ('2' + RANDOM.nextInt(8)));
        while (sb.length() < LENGTH) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        // 보장 문자가 항상 앞에 오면 형태가 예측된다 — 섞는다
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
