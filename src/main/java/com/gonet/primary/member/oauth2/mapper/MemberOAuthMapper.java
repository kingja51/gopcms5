package com.gonet.primary.member.oauth2.mapper;

import com.gonet.primary.member.oauth2.dto.MemberOAuthDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** {@code tb_member_oauth} — 회원 소셜 계정 연결. */
@EgovMapper
public interface MemberOAuthMapper {

    int insert(MemberOAuthDto oauth);

    /**
     * 외부 계정으로 연결을 찾는다 — 로그인의 출발점.
     *
     * <p>사이트를 조건에 넣지 않는다: {@code uk_oauth_provider_user} 가 사이트를 묶지 않아
     * 같은 카카오 계정이 여러 사이트에 동시에 연결될 수 없다. 사이트별 연결이 필요해지면
     * UNIQUE 키부터 바꿔야 하므로 여기서만 사이트를 더해도 소용이 없다.
     */
    MemberOAuthDto findByProviderUser(@Param("provider") String provider,
            @Param("providerUserId") String providerUserId);

    List<MemberOAuthDto> findByMember(@Param("memberId") String memberId);

    int updateLastLogin(@Param("memberOauthId") String memberOauthId);

    /**
     * 연결 해제 — 행을 <b>지운다</b>.
     *
     * <p>표시만 내리면({@code use_yn='N'}) 재연결이 막힌다:
     * {@code uk_oauth_provider_user (provider, provider_user_id, delete_yn)} 에
     * {@code delete_yn='N'} 행이 남아 같은 계정의 INSERT 가 중복 키로 실패한다.
     */
    int delete(@Param("memberOauthId") String memberOauthId);
}
