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

    /** 본인확인 중복가입 차단 — 같은 사람이 한 사이트에 두 계정을 만들 수 없다. */
    int countByDiHash(@Param("siteId") String siteId, @Param("diHash") String diHash);

    /** 동의 이력 — UPDATE 가 아니라 INSERT 누적(언제 무엇에 동의했는지가 증빙이다). */
    int insertConsent(MemberConsentDto consent);
}
