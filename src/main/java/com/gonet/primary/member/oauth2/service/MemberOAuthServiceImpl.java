package com.gonet.primary.member.oauth2.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.mapper.MemberMapper;
import com.gonet.primary.member.oauth2.dto.ExternalProfile;
import com.gonet.primary.member.oauth2.dto.MemberOAuthDto;
import com.gonet.primary.member.oauth2.mapper.MemberOAuthMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 계정 연결 — 조회는 읽기 전용, 연결/해제만 쓰기다.
 *
 * <p>클래스 레벨이 {@code readOnly=true} 라 쓰기 메서드는 반드시 개별로 덮어쓴다
 * (설정을 물려받아 조용히 실패하는 것이 선행 프로젝트의 실장애 유형이었다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class MemberOAuthServiceImpl extends AbstractCmsService implements MemberOAuthService {

    private final MemberOAuthMapper oauthMapper;
    private final MemberMapper memberMapper;

    @Override
    public MemberOAuthDto findLink(String provider, String providerUserId) {
        if (provider == null || providerUserId == null || providerUserId.isBlank()) {
            return null;
        }
        return oauthMapper.findByProviderUser(provider, providerUserId);
    }

    @Override
    public List<MemberOAuthDto> findLinks(String memberId) {
        return oauthMapper.findByMember(memberId);
    }

    @Override
    public MemberDto findMember(String memberId) {
        return memberMapper.findById(memberId);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public String link(String memberId, ExternalProfile profile) {
        if (memberId == null || profile == null || !profile.hasIdentity()) {
            throw new IllegalArgumentException("연결할 계정 정보가 없습니다.");
        }
        MemberOAuthDto oauth = new MemberOAuthDto();
        oauth.setMemberOauthId(Uid.next(UidPrefix.MBO));
        oauth.setMemberId(memberId);
        oauth.setProvider(profile.getProvider().name());
        oauth.setProviderUserId(profile.getProviderUserId());
        // 연결 당시 스냅샷 — provider 에서 값이 바뀌어도 여기는 그대로 둔다
        oauth.setEmailAtLink(profile.getEmail());
        oauth.setNameAtLink(profile.getName());
        oauth.setCreatedBy(memberId);
        oauth.setCreatedIp(AuditorContext.currentIp());
        oauthMapper.insert(oauth);

        log.info("소셜 계정 연결 member={} provider={}", memberId, profile.getProvider());
        return oauth.getMemberOauthId();
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void recordLogin(String memberOauthId) {
        oauthMapper.updateLastLogin(memberOauthId);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void unlink(String memberOauthId) {
        oauthMapper.deactivate(memberOauthId);
        log.info("소셜 계정 연결 해제 oauth={}", memberOauthId);
    }
}
