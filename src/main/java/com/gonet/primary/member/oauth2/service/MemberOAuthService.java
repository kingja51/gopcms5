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
     * 연결 해제 — 행을 <b>지운다</b>.
     *
     * <p>표시만 내리면 재연결이 막힌다 —
     * {@code uk_oauth_provider_user (provider, provider_user_id, delete_yn)} 에
     * 해제된 행이 그대로 잡혀 같은 계정의 INSERT 가 중복 키로 실패하고, 그 INSERT 가
     * 가입 트랜잭션 안에 있어 가입 전체가 롤백된다.
     *
     * <p>정상 탈퇴는 이 경로를 타지 않는다 — {@code MemberLifecycleService.withdraw()} 가
     * 원장을 남기고 연결을 함께 지운다. 여기는 회원 행이 사라졌는데 연결만 남은
     * 비정상 상태를 정리하는 방어 경로다.
     */
    void unlink(String memberOauthId);
}
