package com.gonet.primary.member.dto;

import com.gonet.common.audit.Auditable;
import com.gonet.common.crypto.Encrypt;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원 — 가입·조회 공용.
 *
 * <p>{@code @Encrypt} 가 붙은 필드는 DB 에 {@code {AG}} 암호문으로 저장된다. 서비스는
 * 평문만 다루고, 변환은 매퍼 XML 이 지정한 {@code PiiTypeHandler} 안에서만 일어난다
 * (conventions §6).
 *
 * <p>검색이 필요한 값은 {@code *_hash}(HMAC-SHA256)를 함께 담는다 — 암호문에는
 * {@code =} 도 {@code LIKE} 도 걸 수 없기 때문이다.
 *
 * <p>본인확인 값은 <b>DI</b> 다(원전 문서는 CI 로 갔지만 이 스키마는 DI 기준 — PLAN P10-0).
 */
@Getter
@Setter
public class MemberDto extends Auditable {

    private String memberId;
    private Long memberSeq;
    private String siteId;
    private String siteCode;

    private String loginId;
    private String password;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime passwordExpireAt;
    /** 전개된 역할 CSV — 로그인 시 권한 스냅샷으로 쓰인다. */
    private String roleIds;

    @Encrypt
    private String memberName;
    private String nickname;
    @Encrypt(hashColumn = "email_hash")
    private String email;
    private String emailHash;
    private String emailVerifiedYn;
    @Encrypt(hashColumn = "phone_hash")
    private String phone;
    private String phoneHash;

    @Encrypt
    private String birthDate;
    private Integer birthYear;
    /** M/F/N */
    private String gender;

    /** 본인확인 값 — 중복가입 차단·분쟁 대응의 근거. 평문 노출 금지. */
    @Encrypt(hashColumn = "di_hash")
    private String di;
    private String diHash;
    @Encrypt
    private String parentName;
    @Encrypt(hashColumn = "parent_di_hash")
    private String parentDi;
    private String parentDiHash;

    private String addressZipcode;
    @Encrypt
    private String address;
    @Encrypt
    private String addressDetail;

    /** SELF | NICE | OAUTH_NAVER … */
    private String joinType;
    private String status;
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
    private String captchaRequiredYn;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime lastAccessAt;
    private LocalDateTime dormantScheduledAt;

    private String privacyAgreeYn;
    private String termsAgreeYn;
    private String marketingAgreeYn;
    private String smsAgreeYn;
    private String emailAgreeYn;
}
