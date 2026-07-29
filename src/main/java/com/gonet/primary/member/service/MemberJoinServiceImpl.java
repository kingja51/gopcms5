package com.gonet.primary.member.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.crypto.PiiHash;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.auth.service.PasswordPolicy;
import com.gonet.primary.member.dto.MemberConsentDto;
import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.dto.MemberJoinForm;
import com.gonet.primary.member.mapper.MemberMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 가입 서비스.
 *
 * <p>PII 는 평문으로만 다룬다 — 암호화는 매퍼의 TypeHandler 가 한다. 다만 <b>검색용 해시는
 * 서비스가 만들어 넣는다</b>: 어떤 값을 검색 대상으로 삼을지는 정책이고, TypeHandler 는
 * 값 하나만 보므로 그 판단을 할 수 없다.
 *
 * <p>중복 검사는 <b>같은 트랜잭션 안에서</b> 하지만 완전한 방어는 아니다 — 동시에 두 요청이
 * 들어오면 둘 다 통과할 수 있다. 최종 방어선은 DB UNIQUE 제약이고, 여기 검사는
 * 사용자에게 안내 문구를 주기 위한 것이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
@Slf4j
public class MemberJoinServiceImpl extends AbstractCmsService implements MemberJoinService {

    /** 로그인 ID — URL·로그에 실리므로 좁게 잡는다. */
    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z][a-z0-9_-]{3,29}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$");
    /** 생년월일 8자리 — 화면은 자유 입력이라 서버가 형식을 정한다. */
    private static final Pattern BIRTH = Pattern.compile("^\\d{8}$");

    /** 가입 시점 약관 버전 — 동의 이력에 함께 남긴다(무엇에 동의했는지가 증빙이다). */
    @Value("${gopcms.member.consent-version:1.0}")
    private String consentVersion;

    /** 신규 회원의 기본 역할 — ROLE_MEMBER. 실명인증 전이라 ROLE_REAL 은 주지 않는다. */
    @Value("${gopcms.member.default-role-id:ROL_01985a10-0000-7000-8000-000000001004}")
    private String defaultRoleId;

    private final MemberMapper memberMapper;
    private final PiiHash piiHash;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public String join(MemberJoinForm form, String siteId, String userAgent) {
        validate(form, siteId);

        MemberDto member = new MemberDto();
        member.setMemberId(Uid.next(UidPrefix.MBR));
        member.setSiteId(siteId);
        member.setSiteCode(form.getSiteCode());
        member.setLoginId(form.getLoginId().trim());
        member.setPassword(passwordEncoder.encode(form.getPassword()));
        member.setPasswordChangedAt(LocalDateTime.now());
        // 실명인증 전에는 ROLE_MEMBER 만 — ROLE_REAL 은 본인확인을 거쳐야 붙는다
        member.setRoleIds(defaultRoleId);

        member.setMemberName(trim(form.getMemberName()));
        member.setNickname(trim(form.getNickname()));
        member.setEmail(normalizeEmail(form.getEmail()));
        member.setEmailHash(piiHash.hash(form.getEmail()));
        // 인증 메일 확인 전까지는 N — 이 값이 Y 여야 하는 기능(비밀번호 찾기 등)이 있다
        member.setEmailVerifiedYn("N");
        member.setPhone(digitsOnly(form.getPhone()));
        member.setPhoneHash(piiHash.hash(digitsOnly(form.getPhone())));
        member.setBirthDate(trim(form.getBirthDate()));
        member.setBirthYear(birthYear(form.getBirthDate()));
        member.setGender(gender(form.getGender()));

        member.setAddressZipcode(trim(form.getAddressZipcode()));
        member.setAddress(trim(form.getAddress()));
        member.setAddressDetail(trim(form.getAddressDetail()));

        // 홈페이지 직접 가입 — CHECK 허용값은 EMAIL/KAKAO/NAVER/GOOGLE/APPLE/HOMEPAGE/MOBILE
        // ("SELF" 로 넣었다가 제약 위반 500 을 맞았다. 원전 용어와 스키마가 다른 지점)
        member.setJoinType("HOMEPAGE");
        member.setStatus("ACTIVE");
        member.setPrivacyAgreeYn("Y");
        member.setTermsAgreeYn("Y");
        member.setMarketingAgreeYn(yn(form.getMarketingAgreeYn()));
        member.setSmsAgreeYn(yn(form.getSmsAgreeYn()));
        member.setEmailAgreeYn(yn(form.getEmailAgreeYn()));

        memberMapper.insert(member);
        recordConsents(member, form, userAgent);

        log.info("회원 가입 member={} site={} loginId={}",
                member.getMemberId(), form.getSiteCode(), member.getLoginId());
        return member.getMemberId();
    }

    /* ── 검증 ──────────────────────────────────────────────────────────── */

    private void validate(MemberJoinForm form, String siteId) {
        if (siteId == null || siteId.isBlank()) {
            throw new IllegalArgumentException("사이트를 확인할 수 없습니다.");
        }
        if (form.getLoginId() == null || !LOGIN_ID.matcher(form.getLoginId().trim()).matches()) {
            throw new IllegalArgumentException(
                    "아이디는 영문 소문자로 시작하는 4~30자(소문자·숫자·_·-)여야 합니다.");
        }
        if (form.getPassword() == null || !form.getPassword().equals(form.getPasswordConfirm())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        // 관리자와 같은 규칙을 쓴다 — 회원이라고 약한 비밀번호를 허용할 이유가 없다
        String violation = PasswordPolicy.violation(form.getPassword());
        if (violation != null) {
            throw new IllegalArgumentException(violation);
        }

        if (form.getMemberName() == null || form.getMemberName().isBlank()) {
            throw new IllegalArgumentException("이름을 입력해 주세요.");
        }
        if (form.getEmail() == null || !EMAIL.matcher(form.getEmail().trim()).matches()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }
        if (form.getBirthDate() != null && !form.getBirthDate().isBlank()
                && !BIRTH.matcher(form.getBirthDate().trim()).matches()) {
            throw new IllegalArgumentException("생년월일은 8자리 숫자(YYYYMMDD)로 입력해 주세요.");
        }
        // 필수 동의 — 폼에서 감춰도 서버가 다시 본다
        if (!"Y".equals(form.getTermsAgreeYn()) || !"Y".equals(form.getPrivacyAgreeYn())) {
            throw new IllegalArgumentException("이용약관과 개인정보 수집·이용에 동의해야 가입할 수 있습니다.");
        }

        if (memberMapper.countByLoginId(siteId, form.getLoginId().trim()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        // 암호문에는 = 를 걸 수 없어 해시로 찾는다(PiiHash 가 대소문자·공백을 정규화한다)
        if (memberMapper.countByEmailHash(siteId, piiHash.hash(form.getEmail())) > 0) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
    }

    /**
     * 동의 이력 적재 — 필수 2종 + 선택 3종을 <b>모두</b> 남긴다.
     *
     * <p>선택 항목을 'N' 으로 남기는 것도 기록이다. "동의하지 않았다" 를 나중에 증명하려면
     * 행이 있어야 한다.
     */
    private void recordConsents(MemberDto member, MemberJoinForm form, String userAgent) {
        Map<String, String> consents = new LinkedHashMap<>();
        consents.put("TERMS", "Y");
        consents.put("PRIVACY", "Y");
        consents.put("MARKETING", yn(form.getMarketingAgreeYn()));
        consents.put("SMS", yn(form.getSmsAgreeYn()));
        consents.put("EMAIL", yn(form.getEmailAgreeYn()));

        String ip = AuditorContext.currentIp();
        consents.forEach((type, agree) -> {
            MemberConsentDto consent = new MemberConsentDto();
            consent.setMemberConsentId(Uid.next(UidPrefix.MBC));
            consent.setMemberId(member.getMemberId());
            consent.setConsentType(type);
            consent.setConsentVersion(consentVersion);
            consent.setAgreeYn(agree);
            consent.setClientIp(ip);
            // UA 는 500자 컬럼이라 넘치면 저장이 통째로 실패한다 — 잘라서 담는다
            consent.setUserAgent(userAgent == null || userAgent.length() <= 500
                    ? userAgent : userAgent.substring(0, 500));
            consent.setCreatedBy(member.getMemberId());
            consent.setCreatedIp(ip);
            memberMapper.insertConsent(consent);
        });
    }

    /* ── 정규화 ────────────────────────────────────────────────────────── */

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /** 전화번호는 숫자만 남긴다 — 하이픈 유무로 같은 번호가 다르게 저장되면 검색이 어긋난다. */
    private String digitsOnly(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private Integer birthYear(String birthDate) {
        if (birthDate == null || !BIRTH.matcher(birthDate.trim()).matches()) {
            return null;
        }
        return Integer.valueOf(birthDate.trim().substring(0, 4));
    }

    private String gender(String value) {
        return "M".equals(value) || "F".equals(value) ? value : "N";
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String yn(String value) {
        return "Y".equals(value) ? "Y" : "N";
    }
}
