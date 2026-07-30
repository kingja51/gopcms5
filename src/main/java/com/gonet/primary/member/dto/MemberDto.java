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

    /**
     * 이름 — <b>평문 저장</b>이다(DDL 주석도 '평문'). 개인정보이지만 암호화 대상은 아니다.
     *
     * <p>「개인정보의 안전성 확보조치 기준」이 저장 암호화를 <b>의무</b>로 정한 것은
     * 고유식별정보·비밀번호·생체인식정보뿐이고, 이름은 접근통제·전송구간 암호화 대상이다.
     * 반대로 <b>이름 검색은 실무에서 반드시 필요</b>한데, 이 프로젝트의 암호화는 건당
     * 난수 IV 라 같은 이름도 매번 다른 암호문이 되어 검색·정렬·UNIQUE 가 전부 깨진다
     * (2026-07-30 사용자 확정 — conventions.md §6).
     *
     * <p>암호화하지 않는다고 보호를 놓는 것은 아니다. 마스킹({@code Mask.name()})·
     * 개인정보 접근이력·파기 의무는 그대로 적용된다.
     */
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
    /** 법정대리인 이름 — {@link #memberName} 과 같은 이유로 평문이다. */
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
