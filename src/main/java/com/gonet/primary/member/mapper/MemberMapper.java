package com.gonet.primary.member.mapper;

import com.gonet.primary.member.dto.MemberConsentDto;
import com.gonet.primary.member.dto.MemberDto;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** tb_member — 가입·조회. PII 컬럼은 매퍼 XML 이 TypeHandler 를 지정한다. */
@EgovMapper
public interface MemberMapper {

    int insert(MemberDto member);

    MemberDto findById(@Param("memberId") String memberId);

    /** 로그인 ID 중복 — 사이트 안에서만 유일하면 된다(같은 아이디가 다른 사이트에 있을 수 있다). */
    int countByLoginId(@Param("siteId") String siteId, @Param("loginId") String loginId);

    /**
     * 이메일 중복 — 암호문에는 {@code =} 를 걸 수 없어 해시로 찾는다.
     *
     * <p>사이트 스코프인 이유는 loginId 와 같다 — 사이트가 다르면 별개 회원이다.
     */
    int countByEmailHash(@Param("siteId") String siteId, @Param("emailHash") String emailHash);

    /** 아이디 찾기 — 이메일 해시로 후보를 찾는다(이메일은 사이트 안에서 유일). */
    MemberDto findByEmailHash(@Param("siteId") String siteId, @Param("emailHash") String emailHash);

    /** 본인확인 중복가입 차단 — 같은 사람이 한 사이트에 두 계정을 만들 수 없다. */
    int countByDiHash(@Param("siteId") String siteId, @Param("diHash") String diHash);

    /**
     * 임시 비밀번호 적용 — 만료 시각을 <b>과거로</b> 둔다.
     *
     * <p>로그인은 되지만 인증 Provider 가 만료로 막아 변경 화면으로 보낸다(P6-3).
     * 임시 비밀번호를 계속 쓰는 상태를 만들지 않는 장치다.
     */
    int updateTemporaryPassword(@Param("memberId") String memberId,
                                @Param("password") String password,
                                @Param("passwordExpireAt") java.time.LocalDateTime passwordExpireAt);

    /** 동의 이력 — UPDATE 가 아니라 INSERT 누적(언제 무엇에 동의했는지가 증빙이다). */
    int insertConsent(MemberConsentDto consent);
}
