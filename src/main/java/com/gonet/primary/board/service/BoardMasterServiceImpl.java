package com.gonet.primary.board.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Csv;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.PageResult;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.board.dto.BbsMasterAdmDto;
import com.gonet.primary.board.dto.BbsMasterSearch;
import com.gonet.primary.board.dto.BbsType;
import com.gonet.primary.board.dto.BoardAuth;
import com.gonet.primary.board.mapper.BbsMasterMapper;
import com.gonet.primary.file.dto.DownloadAuth;
import com.gonet.primary.site.dto.SiteAdmDto;
import com.gonet.primary.site.service.SiteService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 마스터 서비스.
 *
 * <p>게시판 하나가 곧 하나의 <b>정책 묶음</b>이다 — 누가 읽고, 누가 쓰고, 첨부를 누가
 * 내려받는지가 전부 여기서 정해져 글과 파일로 내려간다. 그래서 저장 검증이 촘촘하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
@Slf4j
public class BoardMasterServiceImpl extends AbstractCmsService implements BoardMasterService {

    /** URL 에 그대로 나가는 값이라 좁게 잡는다 — 대소문자·인코딩 이슈를 원천 차단. */
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_-]{1,49}$");
    /** 통합 게시판 CSV 상한 — VARCHAR(1000) / (40자 + 콤마) ≈ 24개. */
    private static final int GROUP_MAX = 24;

    private final BbsMasterMapper bbsMasterMapper;
    private final SiteService siteService;

    @Override
    public PageResult<BbsMasterAdmDto> getAdmPage(BbsMasterSearch cond) {
        return new PageResult<>(bbsMasterMapper.findPage(cond), bbsMasterMapper.countPage(cond),
                cond.getPage(), cond.getSize());
    }

    @Override
    public BbsMasterAdmDto getAdm(String bbsMasterId) {
        BbsMasterAdmDto master = bbsMasterMapper.findById(bbsMasterId);
        if (master != null) {
            // 화면은 MB 만 다룬다. 저장 때 MB→byte 로 환산하므로 조회 때 되돌려 준다 —
            // 안 하면 수정 화면이 늘 빈 칸으로 열려 저장할 때마다 기본값 10MB 로 덮인다.
            master.setFileSizeMaxMb(master.getFileSizeMax() == null
                    ? 10 : (int) Math.max(1, master.getFileSizeMax() / (1024 * 1024)));
        }
        return master;
    }

    @Override
    public BbsMasterAdmDto getByCode(String siteId, String bbsCode) {
        return bbsMasterMapper.findByCode(siteId, bbsCode);
    }

    @Override
    public List<BbsMasterAdmDto> getGroupCandidates(String siteId, String selfId) {
        return bbsMasterMapper.findGroupCandidates(siteId, selfId);
    }

    @Override
    public List<BbsMasterAdmDto> getGroupedTargets(BbsMasterAdmDto master) {
        if (master == null) {
            return List.of();
        }
        if (!master.isAggregator()) {
            return List.of(master);
        }
        List<BbsMasterAdmDto> targets = new ArrayList<>();
        for (String id : Csv.toSet(master.getGroupedBoardIds())) {
            BbsMasterAdmDto target = bbsMasterMapper.findById(id);
            // CSV 는 스냅샷이라 대상이 지워지거나 중지돼도 값이 남는다 —
            // 살아 있고 사용 중인 것만 합친다(닫아 둔 게시판의 글이 계속 보이면 안 된다)
            if (target != null && "Y".equals(target.getUseYn())) {
                targets.add(target);
            }
        }
        return targets;
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void saveAdm(BbsMasterAdmDto master) {
        validate(master);
        normalize(master);

        if (master.getBbsMasterId() == null || master.getBbsMasterId().isBlank()) {
            master.setBbsMasterId(Uid.next(UidPrefix.BBM));
            bbsMasterMapper.insert(master);
            log.info("게시판 등록 board={} site={} code={} type={}",
                    master.getBbsMasterId(), master.getSiteCode(),
                    master.getBbsCode(), master.getBbsType());
        } else {
            // 정책을 바꿨는지는 덮어쓰기 전에만 알 수 있다
            BbsMasterAdmDto before = bbsMasterMapper.findById(master.getBbsMasterId());
            bbsMasterMapper.update(master);
            cascadeDownloadAuth(before, master);
        }
    }

    /**
     * 마스터의 다운로드 권한이 바뀌면 이미 올라온 글의 첨부에도 반영한다.
     *
     * <p>안 하면 정책을 조인 뒤에도 기존 첨부는 옛 권한 그대로 열려 있어, 화면이 말하는
     * 정책과 실제 접근 가능 범위가 갈린다. 공지글은 제외한다(매퍼 주석 참조).
     */
    private void cascadeDownloadAuth(BbsMasterAdmDto before, BbsMasterAdmDto after) {
        if (before == null || after.getDownloadAuth() == null
                || after.getDownloadAuth().equals(before.getDownloadAuth())) {
            return;
        }
        int changed = bbsMasterMapper.cascadeDownloadAuth(after.getBbsMasterId(),
                after.getDownloadAuth(),
                AuditorContext.currentUserId(), AuditorContext.currentIp());
        log.info("첨부 다운로드 권한 일괄 변경 board={} {} → {} groups={} (공지 제외)",
                after.getBbsMasterId(), before.getDownloadAuth(), after.getDownloadAuth(), changed);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void deleteAdm(String bbsMasterId) {
        // 글이 남아 있는 게시판은 지우지 않는다. soft delete 라 되살릴 수는 있지만,
        // 목록에서 사라진 게시판의 글이 어디에도 안 보이는 상태가 더 나쁘다.
        int articles = bbsMasterMapper.countArticles(bbsMasterId);
        if (articles > 0) {
            throw new IllegalStateException(
                    "게시글이 %d건 있어 삭제할 수 없습니다. 사용 중지로 먼저 닫아 주세요."
                            .formatted(articles));
        }
        bbsMasterMapper.softDelete(bbsMasterId,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
    }

    /* ── 검증 ──────────────────────────────────────────────────────────── */

    private void validate(BbsMasterAdmDto m) {
        if (m.getSiteId() == null || m.getSiteId().isBlank()) {
            throw new IllegalArgumentException("사이트를 선택해 주세요.");
        }
        if (m.getBbsCode() == null || !CODE.matcher(m.getBbsCode()).matches()) {
            throw new IllegalArgumentException(
                    "게시판 코드는 소문자로 시작하는 2~50자(소문자·숫자·_·-)여야 합니다.");
        }
        if (m.getBbsName() == null || m.getBbsName().isBlank()) {
            throw new IllegalArgumentException("게시판 이름을 입력해 주세요.");
        }
        if (!BbsType.isValid(m.getBbsType())) {
            throw new IllegalArgumentException("알 수 없는 게시판 유형입니다.");
        }
        if (!BoardAuth.isValidRead(m.getReadAuth())) {
            throw new IllegalArgumentException("알 수 없는 읽기 권한입니다.");
        }
        if (!BoardAuth.isValidWrite(m.getWriteAuth())) {
            throw new IllegalArgumentException("알 수 없는 쓰기 권한입니다.");
        }
        if (!DownloadAuth.isValid(m.getDownloadAuth())) {
            throw new IllegalArgumentException("알 수 없는 첨부 다운로드 권한입니다.");
        }
        // 코드는 URL 의 일부라 사이트 안에서 유일해야 한다
        if (bbsMasterMapper.countByCode(m.getSiteId(), m.getBbsCode(), m.getBbsMasterId()) > 0) {
            throw new IllegalArgumentException(
                    "이미 쓰이고 있는 게시판 코드입니다. (%s)".formatted(m.getBbsCode()));
        }
    }

    /** 저장 직전 값 정리 — 화면 입력을 DB 가 기대하는 형태로 맞춘다. */
    private void normalize(BbsMasterAdmDto m) {
        SiteAdmDto site = siteService.getAdm(m.getSiteId());
        if (site == null) {
            throw new IllegalArgumentException("사이트를 찾을 수 없습니다.");
        }
        // site_code 는 캐시 컬럼이다 — 사람이 입력하게 두면 site_id 와 어긋난다
        m.setSiteCode(site.getSiteCode());

        m.setCommentYn(yn(m.getCommentYn()));
        m.setFileYn(yn(m.getFileYn()));
        m.setAnonymousYn(yn(m.getAnonymousYn()));
        m.setNoticeTopYn(yn(m.getNoticeTopYn()));
        m.setHtmlYn(yn(m.getHtmlYn()));
        m.setCaptchaYn(yn(m.getCaptchaYn()));
        m.setUseYn(m.getUseYn() == null ? "Y" : yn(m.getUseYn()));

        if (m.getFileCountMax() == null || m.getFileCountMax() < 0) {
            m.setFileCountMax(5);
        }
        // 화면은 MB 로 받는다 — 운영자에게 바이트를 세게 하지 않는다
        int mb = m.getFileSizeMaxMb() == null || m.getFileSizeMaxMb() < 1
                ? 10 : m.getFileSizeMaxMb();
        m.setFileSizeMax((long) mb * 1024 * 1024);

        m.setGroupedBoardIds(normalizeGroupedIds(m));
    }

    /**
     * 통합 게시판 대상 정리 — 중복 제거·중첩 금지·상한.
     *
     * <p>중첩을 막는 이유: 통합 게시판이 다른 통합 게시판을 품으면 목록 질의가 재귀가 되고,
     * 어느 게시판의 글인지 추적이 불가능해진다. 한 겹까지만 허용한다.
     */
    private String normalizeGroupedIds(BbsMasterAdmDto m) {
        String raw = m.getGroupedBoardIds();
        if (raw == null || raw.isBlank()) {
            return null;                       // 일반 게시판
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String id = token.trim();
            if (id.isEmpty()) {
                continue;
            }
            BbsMasterAdmDto target = bbsMasterMapper.findById(id);
            if (target == null) {
                throw new IllegalArgumentException("존재하지 않는 게시판이 포함돼 있습니다.");
            }
            // 자기 자신은 허용한다 — 통합 게시판이 자기 글도 함께 보여주는 구성
            if (target.isAggregator() && !id.equals(m.getBbsMasterId())) {
                throw new IllegalArgumentException(
                        "통합 게시판(%s)을 다시 통합 대상으로 넣을 수 없습니다.".formatted(target.getBbsCode()));
            }
            ids.add(id);
        }
        if (ids.size() > GROUP_MAX) {
            throw new IllegalArgumentException("통합 대상은 최대 %d개까지입니다.".formatted(GROUP_MAX));
        }
        return ids.isEmpty() ? null : String.join(",", ids);
    }

    /** 체크박스는 값이 없으면 아예 오지 않는다 — 그 경우를 'N' 으로 읽는다. */
    private String yn(String value) {
        return "Y".equals(value) ? "Y" : "N";
    }
}
