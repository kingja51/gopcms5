package com.gonet.primary.member.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.crypto.PiiHash;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.auth.service.PasswordPolicy;
import com.gonet.primary.member.dto.JoinSession;
import com.gonet.primary.member.dto.MemberConsentDto;
import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.dto.MemberJoinForm;
import com.gonet.primary.member.mapper.MemberMapper;
import com.gonet.primary.member.oauth2.dto.ExternalProfile;
import com.gonet.primary.member.oauth2.dto.OAuth2Provider;
import com.gonet.primary.member.oauth2.service.MemberOAuthService;
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

    /** 실명인증을 마친 회원에게 추가로 붙는 역할 — ROLE_REAL. */
    @Value("${gopcms.member.real-role-id:ROL_01985a10-0000-7000-8000-000000001005}")
    private String realRoleId;

    private final MemberMapper memberMapper;
    private final MemberOAuthService memberOAuthService;
    private final PiiHash piiHash;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public String join(MemberJoinForm form, JoinSession wizard, String siteId, String userAgent) {
        validate(form, wizard, siteId);

        MemberDto member = new MemberDto();
        member.setMemberId(Uid.next(UidPrefix.MBR));
        member.setSiteId(siteId);
        member.setSiteCode(form.getSiteCode());
        member.setLoginId(form.getLoginId().trim());
        member.setPassword(passwordEncoder.encode(form.getPassword()));
        member.setPasswordChangedAt(LocalDateTime.now());
        // 실명인증을 마쳐야 ROLE_REAL 이 붙는다 — 인증 없이 가입하면 ROLE_MEMBER 만
        member.setRoleIds(wizard.isVerified()
                ? defaultRoleId + "," + realRoleId : defaultRoleId);

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

        applyVerifiedIdentity(member, wizard);

        // 소셜 가입이면 provider 를 그대로 쓴다 — CHECK 허용값이
        // EMAIL/KAKAO/NAVER/GOOGLE/APPLE/HOMEPAGE/MOBILE 이라 enum 이름이 그대로 들어맞는다.
        // ("SELF" 로 넣었다가 제약 위반 500 을 맞았다. 원전 용어와 스키마가 다른 지점)
        member.setJoinType(wizard.hasOauth() ? wizard.getOauthProvider() : "HOMEPAGE");
        member.setStatus("ACTIVE");
        // 동의는 마법사 STEP 2 가 확정한 값이다 — 마지막 폼에는 동의 항목이 없다
        member.setPrivacyAgreeYn("Y");
        member.setTermsAgreeYn("Y");
        member.setMarketingAgreeYn(wizard.getMarketingAgreeYn());
        member.setSmsAgreeYn(wizard.getSmsAgreeYn());
        member.setEmailAgreeYn(wizard.getEmailAgreeYn());

        memberMapper.insert(member);
        recordConsents(member, wizard, userAgent);
        linkOauth(member.getMemberId(), wizard);

        log.info("회원 가입 member={} site={} loginId={} type={} verified={} joinType={}",
                member.getMemberId(), form.getSiteCode(), member.getLoginId(),
                wizard.getUserType(), wizard.isVerified(), member.getJoinType());
        return member.getMemberId();
    }

    /**
     * 본인인증 결과 반영 — <b>폼 값을 덮어쓴다</b>.
     *
     * <p>이름·생년월일·성별은 인증기관이 준 값이 진실이다. 화면이 읽기 전용으로 보여
     * 주더라도 전송 값은 얼마든지 바뀌므로, 저장 직전에 서버가 다시 덮어쓴다.
     *
     * <p>14세 미만(CHILD)은 인증 주체가 법정대리인이라 결과가 parent_* 로 간다.
     * 아이 본인의 이름·생년월일은 폼 값을 그대로 쓴다.
     */
    private void applyVerifiedIdentity(MemberDto member, JoinSession wizard) {
        if (!wizard.isVerified()) {
            return;
        }
        if (wizard.isChild()) {
            member.setParentName(trim(wizard.getParentName()));
            member.setParentDi(trim(wizard.getParentDi()));
            member.setParentDiHash(piiHash.hash(wizard.getParentDi()));
            return;
        }
        member.setMemberName(trim(wizard.getVerifiedName()));
        member.setBirthDate(trim(wizard.getVerifiedBirthDate()));
        member.setBirthYear(birthYear(wizard.getVerifiedBirthDate()));
        member.setGender(gender(wizard.getVerifiedGender()));
        member.setDi(trim(wizard.getDi()));
        member.setDiHash(piiHash.hash(wizard.getDi()));
    }

    /* ── 검증 ──────────────────────────────────────────────────────────── */

    private void validate(MemberJoinForm form, JoinSession wizard, String siteId) {
        if (wizard == null || !wizard.isAgreed()) {
            throw new IllegalArgumentException("약관 동의 단계를 먼저 완료해 주세요.");
        }
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
        // 필수 동의는 마법사 STEP 2 가 확정한 값을 본다 — 마지막 폼에는 동의 항목이 없다
        if (!"Y".equals(wizard.getTermsAgreeYn()) || !"Y".equals(wizard.getPrivacyAgreeYn())) {
            throw new IllegalArgumentException("이용약관과 개인정보 수집·이용에 동의해야 가입할 수 있습니다.");
        }
        // 본인인증을 마쳤다면 같은 사람이 이미 가입했는지 DI 로 본다 — 아이디·이메일은
        // 얼마든지 새로 만들 수 있어서 중복가입은 DI 로만 걸린다.
        // CHILD 는 검사하지 않는다: 한 법정대리인이 자녀 여럿을 가입시킬 수 있어
        // parent_di 중복은 정상이다. 자녀 본인의 중복은 이름+생년월일로도 가릴 수 없어
        // (동명이인) 지금은 막지 않는다 — 막으려면 자녀 본인 인증 수단이 필요하다.
        if (wizard.isVerified() && !wizard.isChild() && !isBlank(wizard.getDi())
                && memberMapper.countByDiHash(siteId, piiHash.hash(wizard.getDi())) > 0) {
            throw new IllegalArgumentException("이미 가입된 본인인증 정보입니다. 아이디 찾기를 이용해 주세요.");
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
     * 소셜 계정 연결 — 같은 트랜잭션 안에서 만든다.
     *
     * <p>가입은 됐는데 연결이 실패하면 사용자는 "소셜로 가입했는데 소셜로 로그인이 안 되는"
     * 상태에 빠지고, 다시 시도하면 아이디 중복으로 막힌다. 그래서 부분 성공을 허용하지 않는다.
     */
    private void linkOauth(String memberId, JoinSession wizard) {
        if (!wizard.hasOauth()) {
            return;
        }
        OAuth2Provider provider = OAuth2Provider.fromCode(wizard.getOauthProvider());
        if (provider == null) {
            log.warn("알 수 없는 소셜 제공자 — 연결 생략 provider={}", wizard.getOauthProvider());
            return;
        }
        ExternalProfile profile = new ExternalProfile();
        profile.setProvider(provider);
        profile.setProviderUserId(wizard.getOauthUserId());
        profile.setEmail(wizard.getOauthEmail());
        profile.setName(wizard.getOauthName());
        memberOAuthService.link(memberId, profile);
    }

    /**
     * 동의 이력 적재 — 필수 2종 + 선택 3종을 <b>모두</b> 남긴다.
     *
     * <p>선택 항목을 'N' 으로 남기는 것도 기록이다. "동의하지 않았다" 를 나중에 증명하려면
     * 행이 있어야 한다.
     */
    private void recordConsents(MemberDto member, JoinSession wizard, String userAgent) {
        Map<String, String> consents = new LinkedHashMap<>();
        consents.put("TERMS", "Y");
        consents.put("PRIVACY", "Y");
        consents.put("MARKETING", yn(wizard.getMarketingAgreeYn()));
        consents.put("SMS", yn(wizard.getSmsAgreeYn()));
        consents.put("EMAIL", yn(wizard.getEmailAgreeYn()));

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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
