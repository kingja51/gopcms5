package com.gonet.primary.member.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.crypto.PiiHash;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.web.PageResult;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.member.dto.MemberAdmRow;
import com.gonet.primary.member.dto.MemberAdmSearch;
import com.gonet.primary.member.dto.MemberDormantRow;
import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.dto.MemberWithdrawRow;
import com.gonet.primary.member.mapper.MemberAdmMapper;
import com.gonet.primary.member.mapper.MemberMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 관리 구현.
 *
 * <p>검색어 정규화가 여기 있는 이유: 이메일·전화는 <b>해시로 찾는다</b>. 어떤 규칙으로
 * 정규화한 뒤 해시하느냐(소문자·trim·숫자만)가 곧 정책이고, 가입 때 넣은 해시와 같은
 * 규칙이어야 맞는다. SQL 이나 화면이 각자 정하면 검색이 조용히 어긋난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class MemberAdmServiceImpl extends AbstractCmsService implements MemberAdmService {

    /** 화면에서 고를 수 있는 상태 — DDL 의 CHECK 와 1:1. */
    private static final Set<String> ALLOWED_STATUS =
            Set.of("ACTIVE", "LOCKED", "EMAIL_PENDING", "SUSPENDED");

    /**
     * 내려받기 건수 상한.
     *
     * <p>기본값을 넉넉하지만 무한하지 않게 잡는다 — 실무 요청("가입자 명단 주세요")은
     * 대체로 수천 건이고, 그보다 크면 파일이 아니라 별도 절차로 다뤄야 할 규모다.
     */
    @Value("${gopcms.member.adm.export-limit:5000}")
    private int exportLimit;

    private final MemberAdmMapper admMapper;
    private final MemberMapper memberMapper;
    private final MemberLifecycleService lifecycleService;
    private final PiiHash piiHash;

    @Override
    public PageResult<MemberAdmRow> getPage(MemberAdmSearch search) {
        normalize(search);
        int total = admMapper.countPage(search);
        List<MemberAdmRow> rows = total == 0 ? List.of() : admMapper.findPage(search);
        return new PageResult<>(rows, total, search.getPage(), search.getSize());
    }

    @Override
    public Map<String, Integer> countByStatus(String siteId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : admMapper.countByStatus(siteId)) {
            Object status = row.get("status");
            Object count = row.get("cnt");
            if (status != null && count instanceof Number number) {
                counts.put(String.valueOf(status), number.intValue());
            }
        }
        return counts;
    }

    @Override
    public MemberDto get(String memberId) {
        return memberId == null || memberId.isBlank() ? null : memberMapper.findById(memberId);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void changeStatus(String memberId, String status) {
        if (!ALLOWED_STATUS.contains(status)) {
            // 화면 select 밖의 값이 들어온 것 — DB CHECK 에 맡기면 500 이 난다
            throw new IllegalArgumentException("허용되지 않는 상태입니다: " + status);
        }
        int changed = admMapper.updateStatus(memberId, status,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
        if (changed == 0) {
            throw new IllegalArgumentException("회원을 찾을 수 없습니다.");
        }
        log.info("관리자 회원 상태 변경 member={} status={} actor={}",
                memberId, status, AuditorContext.currentUserId());
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void unlock(String memberId) {
        int changed = admMapper.unlock(memberId,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
        if (changed == 0) {
            throw new IllegalArgumentException("회원을 찾을 수 없습니다.");
        }
        log.info("관리자 잠금 해제 member={} actor={}", memberId, AuditorContext.currentUserId());
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void forceWithdraw(String memberId, String reason) {
        MemberDto member = memberMapper.findById(memberId);
        if (member == null) {
            throw new IllegalArgumentException("회원을 찾을 수 없습니다.");
        }
        if (reason == null || reason.isBlank()) {
            // 강제 탈퇴는 되돌릴 수 없다 — 사유 없는 실행을 허용하지 않는다
            throw new IllegalArgumentException("강제 탈퇴 사유를 입력해 주세요.");
        }
        // 셀프 탈퇴·배치와 같은 경로 — 원장 적재 순서와 PII 파기 범위가 갈리지 않게
        lifecycleService.withdraw(memberId, reason.trim(), "ADMIN_FORCE");
        log.info("관리자 강제 탈퇴 member={} actor={}", memberId, AuditorContext.currentUserId());
    }

    @Override
    public PageResult<MemberDormantRow> getDormantPage(MemberAdmSearch search) {
        int total = admMapper.countDormantPage(search);
        List<MemberDormantRow> rows = total == 0 ? List.of() : admMapper.findDormantPage(search);
        return new PageResult<>(rows, total, search.getPage(), search.getSize());
    }

    @Override
    public PageResult<MemberWithdrawRow> getWithdrawPage(MemberAdmSearch search) {
        int total = admMapper.countWithdrawPage(search);
        List<MemberWithdrawRow> rows =
                total == 0 ? List.of() : admMapper.findWithdrawPage(search);
        return new PageResult<>(rows, total, search.getPage(), search.getSize());
    }

    @Override
    public List<MemberAdmRow> getForExport(MemberAdmSearch search) {
        normalize(search);
        // 상한은 화면이 아니라 서버가 정한다 — 파라미터로 받으면 늘려서 부를 수 있다
        search.setExportLimit(exportLimit);
        return admMapper.findForExport(search);
    }

    @Override
    public int exportLimit() {
        return exportLimit;
    }

    /**
     * 검색어 정규화 — 이메일·전화를 <b>해시로 바꿔서</b> 조건에 넣는다.
     *
     * <p>암호문에는 {@code =} 를 걸 수 없어 해시로 찾는다. 가입 때와 같은 정규화
     * (이메일 소문자·trim / 전화 숫자만)를 거쳐야 값이 맞는다.
     *
     * <p>입력값 자리를 덮어쓰지 않는다 — 덮어쓰면 검색창에 64자 해시가 되돌아와
     * 관리자가 방금 친 값을 화면에서 잃는다.
     */
    private void normalize(MemberAdmSearch search) {
        String email = search.getEmail();
        search.setEmailHash(email == null || email.isBlank() ? null : piiHash.hash(email));

        String phone = search.getPhone();
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        search.setPhoneHash(digits.isEmpty() ? null : piiHash.hash(digits));
    }
}
