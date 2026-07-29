package com.gonet.primary.member.service;

import com.gonet.common.crypto.PiiHash;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이디 찾기.
 *
 * <p>이메일은 암호문이라 {@code =} 를 걸 수 없어 <b>해시로</b> 찾는다. 이름은 확인용으로만
 * 쓰는데, 이것도 암호문이라 SQL 로는 비교할 수 없다 — 후보를 해시로 좁힌 뒤 복호화된 값을
 * 자바에서 대조한다(이메일이 사이트 안에서 유일하므로 후보는 0~1건이다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
@Slf4j
public class MemberFindServiceImpl extends AbstractCmsService implements MemberFindService {

    private final MemberMapper memberMapper;
    private final PiiHash piiHash;

    @Override
    public String findLoginIdMasked(String siteId, String memberName, String email) {
        if (siteId == null || memberName == null || memberName.isBlank()
                || email == null || email.isBlank()) {
            return null;
        }
        var found = memberMapper.findByEmailHash(siteId, piiHash.hash(email));
        if (found == null) {
            return null;
        }
        // 이름은 복호화된 값으로 대조한다 — 공백·대소문자 차이는 무시
        String stored = found.getMemberName() == null ? "" : found.getMemberName().trim();
        if (!stored.equalsIgnoreCase(memberName.trim())) {
            return null;
        }
        log.info("아이디 찾기 성공 member={} site={}", found.getMemberId(), siteId);
        return mask(found.getLoginId());
    }

    /**
     * 아이디 마스킹 — 앞 세 글자만 남긴다.
     *
     * <p>전체를 보여주면 이름·이메일만 알면 아이디를 수집할 수 있다. 본인은 앞자리만 봐도
     * 무엇인지 알아보지만, 남의 계정을 수집하는 데는 쓸모가 없을 만큼만 준다.
     */
    private String mask(String loginId) {
        if (loginId == null || loginId.isEmpty()) {
            return null;
        }
        if (loginId.length() <= 3) {
            return loginId.charAt(0) + "*".repeat(loginId.length() - 1);
        }
        return loginId.substring(0, 3) + "*".repeat(loginId.length() - 3);
    }
}
