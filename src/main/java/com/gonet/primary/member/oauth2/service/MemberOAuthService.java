package com.gonet.primary.member.oauth2.service;

import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.oauth2.dto.ExternalProfile;
import com.gonet.primary.member.oauth2.dto.MemberOAuthDto;
import java.util.List;

/** 회원 ↔ 외부 계정 연결 관리. provider 통신은 {@link OAuth2Service} 가 맡는다. */
public interface MemberOAuthService {

    /** 외부 계정에 연결된 회원 찾기 — 없으면 신규 가입 흐름으로 보낸다. */
    MemberOAuthDto findLink(String provider, String providerUserId);

    /** 회원이 연결해 둔 계정 목록 — 마이페이지 노출용. */
    List<MemberOAuthDto> findLinks(String memberId);

    MemberDto findMember(String memberId);

    /**
     * 연결 생성. 가입 직후 또는 로그인 상태에서 계정을 추가로 잇는다.
     *
     * @return member_oauth_id
     */
    String link(String memberId, ExternalProfile profile);

    void recordLogin(String memberOauthId);

    /**
     * 연결 해제 — 행을 지우지 않고 {@code use_yn='N'} 으로 내린다.
     *
     * <p>탈퇴한 회원에 매달린 연결이 남아 있을 때도 이걸 쓴다. 그래야 같은 외부 계정으로
     * 다시 가입할 수 있다.
     */
    void unlink(String memberOauthId);
}
