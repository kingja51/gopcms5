package com.gonet.primary.board.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.board.dto.BbsLikeDto;
import com.gonet.primary.board.dto.ReactionTarget;
import com.gonet.primary.board.mapper.BbsLikeMapper;
import com.gonet.primary.file.service.FileAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 서비스.
 *
 * <p>익명 좋아요는 지원하지 않는다 — 누가 눌렀는지 모르면 중복을 막을 수 없고, 그러면
 * 숫자가 의미를 잃는다(DDL 의 {@code user_id NOT NULL} 이 그 결정을 이미 반영하고 있다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class BoardLikeServiceImpl extends AbstractCmsService implements BoardLikeService {

    private final BbsLikeMapper bbsLikeMapper;
    private final FileAccessGuard accessGuard;

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public LikeResult toggle(String targetType, String targetId, String sourceUrl) {
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

        BbsLikeDto like = new BbsLikeDto();
        like.setLikeId(Uid.next(UidPrefix.LIK));
        like.setTargetType(targetType);
        like.setTargetId(targetId);
        like.setUserId(user.userId());
        like.setUserType(user.userType());
        like.setSourceUrl(sourceUrl);
        like.setCreatedBy(user.userId());
        like.setCreatedIp(AuditorContext.currentIp());

        bbsLikeMapper.toggle(like);

        boolean liked = "N".equals(bbsLikeMapper.findState(targetType, targetId, user.userId()));
        refreshCounter(targetType, targetId);
        return new LikeResult(liked, bbsLikeMapper.countActive(targetType, targetId));
    }

    /** 비정규화 컬럼 동기 — CONTENT 는 카운터 컬럼이 없어 건너뛴다. */
    private void refreshCounter(String targetType, String targetId) {
        if (ReactionTarget.ARTICLE.equals(targetType)) {
            bbsLikeMapper.refreshArticleLikeCount(targetId);
        } else if (ReactionTarget.COMMENT.equals(targetType)) {
            bbsLikeMapper.refreshCommentLikeCount(targetId);
        }
    }
}
