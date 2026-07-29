package com.gonet.primary.member.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.crypto.PiiHash;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.mail.service.MailService;
import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.dto.MemberOtp;
import com.gonet.primary.member.mapper.MemberLifecycleMapper;
import com.gonet.primary.member.mapper.MemberOtpMapper;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴면 복원 — 이메일 인증번호 경로.
 *
 * <p>코드는 <b>평문으로 저장하지 않는다</b>. HMAC 해시만 남기고 대조도 해시끼리 한다 —
 * DB 가 유출돼도 진행 중인 인증이 통째로 뚫리지 않는다.
 *
 * <p>시도 횟수는 <b>행에</b> 둔다. 세션에 두면 세션을 새로 잡아 무제한 대입할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
@Slf4j
public class DormantRestoreServiceImpl extends AbstractCmsService
        implements DormantRestoreService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PURPOSE = MemberOtp.PURPOSE_DORMANT_RESTORE;

    /** 코드 유효시간(분) — 짧을수록 대입 창이 좁다. */
    @Value("${gopcms.member.otp.ttl-minutes:5}")
    private int ttlMinutes;

    /** 검증 시도 상한 — 넘으면 코드가 죽는다(재발급해야 한다). */
    @Value("${gopcms.member.otp.max-attempts:5}")
    private int maxAttempts;

    /** 재발송 쿨다운(초) — 메일 폭탄과 코드 갈아치우기를 막는다. */
    @Value("${gopcms.member.otp.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    private final MemberOtpMapper otpMapper;
    private final MemberLifecycleMapper lifecycleMapper;
    private final PiiHash piiHash;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Override
    public String findDormantMemberId(String siteId, String loginId, String rawPassword) {
        if (siteId == null || loginId == null || rawPassword == null) {
            return null;
        }
        MemberDto dormant = lifecycleMapper.findDormantByLoginId(siteId, loginId.trim());
        // 비밀번호까지 맞아야 휴면으로 인정한다 — 아이디만으로 알려 주면 계정 존재가 샌다
        if (dormant == null || !passwordEncoder.matches(rawPassword, dormant.getPassword())) {
            return null;
        }
        return dormant.getMemberId();
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void issueCode(String memberId) {
        MemberOtp previous = otpMapper.findLatest(memberId, PURPOSE);
        if (previous != null && previous.getCreatedAtView() != null
                && previous.getCreatedAtView()
                        .plusSeconds(resendCooldownSeconds).isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "잠시 후 다시 요청해 주세요. (%d초 간격)".formatted(resendCooldownSeconds));
        }
        MemberDto dormant = lifecycleMapper.findDormantById(memberId);
        if (dormant == null) {
            log.info("휴면 복원 코드 발급 — 대상 없음 member={}", memberId);
            return;
        }

        // 이전 코드를 즉시 만료 — 살아 있는 코드가 둘이면 시도 제한이 무의미해진다
        otpMapper.expireAll(memberId, PURPOSE, LocalDateTime.now());

        String code = generateCode();
        MemberOtp otp = new MemberOtp();
        otp.setOtpId(Uid.next(UidPrefix.MOT));
        otp.setMemberId(memberId);
        otp.setSiteId(dormant.getSiteId());
        otp.setPurpose(PURPOSE);
        otp.setCodeHash(piiHash.hash(code));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
        otp.setClientIp(AuditorContext.currentIp());
        otp.setCreatedBy(memberId);
        otp.setCreatedIp(AuditorContext.currentIp());
        otpMapper.insert(otp);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("memberName", dormant.getMemberName());
        model.put("loginId", dormant.getLoginId());
        model.put("authCode", code);
        model.put("ttlMinutes", ttlMinutes);
        model.put("sentAt", LocalDateTime.now());
        // 코드는 메일에만 실린다 — 로그에는 남기지 않는다
        mailService.sendAsync("ACCOUNT_DORMANT_RESTORED", dormant.getEmail(), model);
        log.info("휴면 복원 코드 발급 member={} ttl={}분", memberId, ttlMinutes);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public boolean verifyAndRestore(String memberId, String code) {
        MemberOtp otp = otpMapper.findLatest(memberId, PURPOSE);
        if (otp == null || code == null || code.isBlank()) {
            return false;
        }
        // 시도는 성공·실패와 무관하게 먼저 센다 — 실패 후 예외로 빠져나가면 세지 못한다
        otpMapper.increaseAttempt(otp.getOtpId());

        if (otp.getAttemptCount() != null && otp.getAttemptCount() >= maxAttempts) {
            log.warn("휴면 복원 코드 시도 초과 member={} attempts={}",
                    memberId, otp.getAttemptCount());
            return false;
        }
        if (otp.getExpiresAt() == null || otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        if (!piiHash.hash(code.trim()).equals(otp.getCodeHash())) {
            return false;
        }

        otpMapper.markVerified(otp.getOtpId());
        restore(memberId);
        return true;
    }

    /**
     * 복원 처리 — 어느 수단으로 확인했든 여기를 탄다.
     *
     * <p>역이관 후 안내 이력을 지운다. 남겨 두면 다시 휴면이 될 때 안내가 발송되지 않는다
     * ({@code (member_id, stage)} UNIQUE 때문).
     */
    private void restore(String memberId) {
        int restored = lifecycleMapper.restoreToMember(memberId);
        if (restored == 0) {
            throw new IllegalStateException("휴면 계정을 복원하지 못했습니다.");
        }
        lifecycleMapper.markRestored(memberId);
        lifecycleMapper.deleteNoticesByMember(memberId);
        log.info("휴면 복원 완료 member={}", memberId);
    }

    /** 6자리 — 앞자리 0 이 잘리지 않게 문자열로 만든다. */
    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
