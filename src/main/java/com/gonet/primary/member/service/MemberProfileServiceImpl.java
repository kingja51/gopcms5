package com.gonet.primary.member.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.crypto.PiiHash;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.member.dto.MemberConsentDto;
import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.dto.MemberProfileForm;
import com.gonet.primary.member.mapper.MemberMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 서비스.
 *
 * <p>수정 가능한 값은 폼 DTO 가 정한다({@link MemberProfileForm}). 본인확인 근간값은
 * 그 DTO 에 아예 없어서 어떤 경로로도 들어올 수 없다.
 *
 * <p>동의 변경도 <b>이력으로 쌓는다</b> — 가입 때와 같은 규칙이다. 마케팅 수신을 껐다는
 * 사실은 나중에 증명해야 할 수 있고, 현재 값만 남기면 언제 껐는지 알 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
@Slf4j
public class MemberProfileServiceImpl extends AbstractCmsService implements MemberProfileService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$");

    @Value("${gopcms.member.consent-version:1.0}")
    private String consentVersion;

    private final MemberMapper memberMapper;
    private final PiiHash piiHash;

    @Override
    public MemberDto get(String memberId) {
        return memberMapper.findById(memberId);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void update(String memberId, MemberProfileForm form, String userAgent) {
        MemberDto current = memberMapper.findById(memberId);
        if (current == null) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }
        if (form.getEmail() == null || !EMAIL.matcher(form.getEmail().trim()).matches()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }

        String newEmail = form.getEmail().trim().toLowerCase();
        String newEmailHash = piiHash.hash(newEmail);
        boolean emailChanged = !newEmailHash.equals(current.getEmailHash());
        if (emailChanged
                && memberMapper.countByEmailHashExcept(current.getSiteId(), newEmailHash, memberId) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        MemberDto update = new MemberDto();
        update.setMemberId(memberId);
        update.setNickname(trim(form.getNickname()));
        update.setEmail(newEmail);
        update.setEmailHash(newEmailHash);
        // 이메일이 바뀌면 인증을 다시 받아야 한다 — 이전 인증은 다른 주소에 대한 것이다
        update.setEmailVerifiedYn(emailChanged ? "N" : current.getEmailVerifiedYn());
        update.setPhone(digitsOnly(form.getPhone()));
        update.setPhoneHash(piiHash.hash(digitsOnly(form.getPhone())));
        update.setAddressZipcode(trim(form.getAddressZipcode()));
        update.setAddress(trim(form.getAddress()));
        update.setAddressDetail(trim(form.getAddressDetail()));
        update.setMarketingAgreeYn(yn(form.getMarketingAgreeYn()));
        update.setSmsAgreeYn(yn(form.getSmsAgreeYn()));
        update.setEmailAgreeYn(yn(form.getEmailAgreeYn()));

        memberMapper.updateProfile(update);
        recordConsentChanges(current, update, userAgent);

        log.info("회원 정보 수정 member={} emailChanged={}", memberId, emailChanged);
    }

    /**
     * 동의 변경분만 이력에 남긴다.
     *
     * <p>바뀌지 않은 항목까지 매번 쌓으면 이력이 의미를 잃는다 — "언제 바꿨는가" 를
     * 찾으려고 보는 표인데 저장 횟수만큼 행이 늘어나면 그걸 알 수 없다.
     */
    private void recordConsentChanges(MemberDto before, MemberDto after, String userAgent) {
        Map<String, String[]> changes = new LinkedHashMap<>();
        changes.put("MARKETING",
                new String[] {before.getMarketingAgreeYn(), after.getMarketingAgreeYn()});
        changes.put("SMS", new String[] {before.getSmsAgreeYn(), after.getSmsAgreeYn()});
        changes.put("EMAIL", new String[] {before.getEmailAgreeYn(), after.getEmailAgreeYn()});

        String ip = AuditorContext.currentIp();
        changes.forEach((type, pair) -> {
            if (java.util.Objects.equals(pair[0], pair[1])) {
                return;
            }
            MemberConsentDto consent = new MemberConsentDto();
            consent.setMemberConsentId(Uid.next(UidPrefix.MBC));
            consent.setMemberId(before.getMemberId());
            consent.setConsentType(type);
            consent.setConsentVersion(consentVersion);
            consent.setAgreeYn(pair[1]);
            consent.setClientIp(ip);
            consent.setUserAgent(userAgent == null || userAgent.length() <= 500
                    ? userAgent : userAgent.substring(0, 500));
            consent.setCreatedBy(before.getMemberId());
            consent.setCreatedIp(ip);
            memberMapper.insertConsent(consent);
        });
    }

    private String digitsOnly(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String yn(String value) {
        return "Y".equals(value) ? "Y" : "N";
    }
}
