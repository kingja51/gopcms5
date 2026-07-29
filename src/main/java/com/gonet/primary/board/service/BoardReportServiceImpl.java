package com.gonet.primary.board.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.common.web.PageResult;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.board.dto.BbsCommentDto;
import com.gonet.primary.board.dto.BbsReportDto;
import com.gonet.primary.board.dto.BbsReportSearch;
import com.gonet.primary.board.dto.ReactionTarget;
import com.gonet.primary.board.dto.ReportReason;
import com.gonet.primary.board.mapper.BbsArticleMapper;
import com.gonet.primary.board.mapper.BbsCommentMapper;
import com.gonet.primary.board.mapper.BbsReportMapper;
import com.gonet.primary.file.service.FileAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 서비스.
 *
 * <p>임계 개수에 도달하면 대상을 {@code REPORTED} 로 <b>자동 전환</b>해 화면에서 내린다.
 * 사람이 볼 때까지 방치하면 심야·주말에 올라온 문제 글이 그대로 노출되기 때문이다.
 * 대신 <b>삭제하지 않는다</b> — 관리자가 검토해 되돌릴 수 있어야 하고, 자동 조치가
 * 곧 최종 판단이 되면 조직적 신고로 멀쩡한 글을 지울 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
@Slf4j
public class BoardReportServiceImpl extends AbstractCmsService implements BoardReportService {

    /** 이 개수에 도달하면 자동으로 숨긴다. 0 이하로 두면 자동 전환을 끈다. */
    @Value("${gopcms.board.report-threshold:5}")
    private int threshold;

    private final BbsReportMapper bbsReportMapper;
    private final BbsCommentMapper bbsCommentMapper;
    private final BbsArticleMapper bbsArticleMapper;
    private final FileAccessGuard accessGuard;

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public int report(String targetType, String targetId, String reasonCode,
            String reasonText, String sourceUrl) {
        LoginPrincipal user = accessGuard.currentPrincipal();
        if (user == null) {
            throw new InsufficientAuthenticationException("로그인이 필요합니다.");
        }
        if (!ReactionTarget.isValid(targetType)) {
            throw new IllegalArgumentException("알 수 없는 대상 유형입니다.");
        }
        if (targetId == null || !targetId.matches(Uid.PATTERN)) {
            throw new IllegalArgumentException("잘못된 대상 식별자입니다.");
        }
        if (!ReportReason.isValid(reasonCode)) {
            throw new IllegalArgumentException("신고 사유를 선택해 주세요.");
        }
        // UNIQUE 제약이 있지만 먼저 물어본다 — 500 대신 "이미 신고했습니다" 를 보여주려는 것
        if (bbsReportMapper.countByReporter(targetType, targetId, user.userId()) > 0) {
            throw new IllegalStateException("이미 신고한 글입니다. 처리 결과를 기다려 주세요.");
        }

        BbsReportDto report = new BbsReportDto();
        report.setReportId(Uid.next(UidPrefix.RPT));
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReporterUserId(user.userId());
        report.setReporterUserType(user.userType());
        report.setReasonCode(reasonCode);
        report.setReasonText(reasonText);
        report.setSourceUrl(sourceUrl);
        report.setCreatedBy(user.userId());
        report.setCreatedIp(AuditorContext.currentIp());
        bbsReportMapper.insert(report);

        refreshCounter(targetType, targetId);
        int count = bbsReportMapper.countActive(targetType, targetId);
        applyThreshold(targetType, targetId, count);
        return count;
    }

    @Override
    public PageResult<BbsReportDto> getPage(BbsReportSearch cond) {
        return new PageResult<>(bbsReportMapper.findPage(cond), bbsReportMapper.countPage(cond),
                cond.getPage(), cond.getSize());
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void review(String reportId, String status, String reviewNote, boolean hideTarget) {
        if (!"REVIEWED".equals(status) && !"REJECTED".equals(status)) {
            throw new IllegalArgumentException("알 수 없는 처리 상태입니다.");
        }
        BbsReportDto report = bbsReportMapper.findById(reportId);
        if (report == null) {
            throw new IllegalArgumentException("신고 내역을 찾을 수 없습니다.");
        }
        String actor = AuditorContext.currentUserId();
        String ip = AuditorContext.currentIp();
        bbsReportMapper.review(reportId, status, reviewNote, actor, ip);

        // 기각하면 유효 신고 수가 줄어든다 — 임계로 자동 숨김된 글이 되살아나야 한다
        refreshCounter(report.getTargetType(), report.getTargetId());
        String targetStatus = resolveTargetStatus(report, status, hideTarget);
        updateTargetStatus(report.getTargetType(), report.getTargetId(), targetStatus, actor, ip);

        log.info("신고 검토 report={} status={} target={}:{} → {} actor={}",
                reportId, status, report.getTargetType(), report.getTargetId(),
                targetStatus, actor);
    }

    /**
     * 검토 결과에 따른 대상 상태.
     *
     * <p>기각은 <b>무조건 복원</b>이다. 임계 자동 숨김은 잠정 조치일 뿐이고, 사람이
     * "문제 없음" 이라고 판단했는데도 숨겨진 채로 남으면 자동 조치가 최종 판단이 된다.
     */
    private String resolveTargetStatus(BbsReportDto report, String status, boolean hideTarget) {
        if ("REJECTED".equals(status)) {
            return "PUBLISHED";
        }
        return hideTarget ? "HIDDEN" : "PUBLISHED";
    }

    /** 임계 도달 시 자동 숨김 — 이미 숨겨져 있으면 그대로 둔다(UPDATE 는 멱등하다). */
    private void applyThreshold(String targetType, String targetId, int count) {
        if (threshold <= 0 || count < threshold) {
            return;
        }
        updateTargetStatus(targetType, targetId, "REPORTED", "SYSTEM", AuditorContext.currentIp());
        log.warn("신고 임계 도달 — 자동 숨김 target={}:{} count={} threshold={}",
                targetType, targetId, count, threshold);
    }

    private void updateTargetStatus(String targetType, String targetId, String status,
            String actor, String ip) {
        if (ReactionTarget.ARTICLE.equals(targetType)) {
            bbsReportMapper.updateArticleStatus(targetId, status, actor, ip);
        } else if (ReactionTarget.COMMENT.equals(targetType)) {
            bbsReportMapper.updateCommentStatus(targetId, status, actor, ip);
            // 댓글이 숨겨지면 화면에서 사라지므로 글의 댓글 수도 따라가야 한다
            // (P9-3: 세는 대상은 PUBLISHED 뿐)
            BbsCommentDto comment = bbsCommentMapper.findById(targetId);
            if (comment != null) {
                bbsArticleMapper.refreshCommentCount(comment.getArticleId());
            }
        }
    }

    private void refreshCounter(String targetType, String targetId) {
        if (ReactionTarget.ARTICLE.equals(targetType)) {
            bbsReportMapper.refreshArticleReportCount(targetId);
        } else if (ReactionTarget.COMMENT.equals(targetType)) {
            bbsReportMapper.refreshCommentReportCount(targetId);
        }
    }
}
