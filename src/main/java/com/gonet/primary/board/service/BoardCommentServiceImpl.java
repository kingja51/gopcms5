package com.gonet.primary.board.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.board.dto.BbsArticleAdmDto;
import com.gonet.primary.board.dto.BbsCommentDto;
import com.gonet.primary.board.mapper.BbsArticleMapper;
import com.gonet.primary.board.mapper.BbsCommentMapper;
import com.gonet.primary.file.service.FileAccessGuard;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 서비스.
 *
 * <p>대댓글은 <b>2단까지만</b> 만든다. 더 깊어지면 화면에서 들여쓰기가 무너지고 대화 맥락도
 * 오히려 읽기 어려워진다. 3단을 시도하면 거절하는 대신 <b>상위 댓글의 자식으로 평탄화</b>한다 —
 * 사용자는 "답글을 눌렀는데 실패" 하는 대신 같은 대화 줄기 안에 답글을 남기게 된다.
 *
 * <p><b>권한 판정은 전부 여기서 한다.</b> 댓글에는 site_id 가 없고 글을 거쳐야만 사이트를 알 수
 * 있어서, 컨트롤러가 확인해 주겠거니 하고 두면 ID 하나로 다른 사이트의 댓글에 손대는 길이 열린다
 * (실측 발견: {@code delete} 가 소유자·사이트 확인 없이 ID 만으로 지우고 있었다).
 * 글 소속 확인 → 권한 확인 순서를 서비스가 스스로 밟는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
@Slf4j
public class BoardCommentServiceImpl extends AbstractCmsService implements BoardCommentService {

    private static final String MANAGE_ROLE = "ROLE_STAFF";
    private static final int MAX_DEPTH = 2;

    private final BbsCommentMapper bbsCommentMapper;
    private final BbsArticleMapper bbsArticleMapper;
    private final FileAccessGuard accessGuard;

    @Override
    public List<BbsCommentDto> getByArticle(String articleId) {
        return bbsCommentMapper.findByArticle(articleId);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void write(BbsCommentDto comment) {
        LoginPrincipal writer = accessGuard.currentPrincipal();
        if (writer == null) {
            throw new InsufficientAuthenticationException("로그인이 필요합니다.");
        }
        if (comment.getContent() == null || comment.getContent().isBlank()) {
            throw new IllegalArgumentException("댓글 내용을 입력해 주세요.");
        }
        // 대상 글이 실제로 존재하고 공개 상태인지 — 숨김·삭제된 글에 댓글이 붙으면
        // 화면에는 안 보이는 채로 댓글 수만 오르고, 없는 articleId 로도 행이 쌓인다
        requirePublishedArticle(comment.getArticleId());

        applyThreadPosition(comment);

        comment.setCommentId(Uid.next(UidPrefix.BBC));
        comment.setWriterUserId(writer.userId());
        comment.setWriterUserType(writer.userType());
        comment.setWriterName(writer.displayName() != null ? writer.displayName() : writer.loginId());
        comment.setSecretYn("Y".equals(comment.getSecretYn()) ? "Y" : "N");
        comment.setStatus("PUBLISHED");
        comment.setClientIp(AuditorContext.currentIp());

        bbsCommentMapper.insert(comment);
        bbsArticleMapper.refreshCommentCount(comment.getArticleId());
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void delete(String commentId, String articleId) {
        BbsCommentDto comment = bbsCommentMapper.findById(commentId);
        if (comment == null) {
            return;
        }
        // ① 스코프 — 이 글의 댓글인가. 컨트롤러가 글의 사이트·게시판 소속을 이미 확인했으므로,
        //    여기까지 통과하면 현재 사이트의 댓글임이 보장된다.
        //    "없는 댓글" 과 "남의 글 댓글" 을 구분해 주지 않는다 — 구분해 주면 존재 여부가 샌다.
        if (!articleId.equals(comment.getArticleId())) {
            throw new AccessDeniedException("이 글의 댓글이 아닙니다.");
        }
        // ② 권한 — 담당자이거나 본인 댓글
        if (!canManage(comment)) {
            throw new AccessDeniedException("본인이 쓴 댓글만 삭제할 수 있습니다.");
        }
        String actor = AuditorContext.currentUserId();
        String ip = AuditorContext.currentIp();
        // 부모를 지우면 자식도 함께 — 부모 없는 대댓글이 떠 있으면 무슨 말에 대한
        // 답인지 알 수 없다
        bbsCommentMapper.softDeleteChildren(commentId, actor, ip);
        bbsCommentMapper.softDelete(commentId, actor, ip);
        bbsArticleMapper.refreshCommentCount(comment.getArticleId());
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void moderate(String commentId, String status) {
        if (!List.of("PUBLISHED", "HIDDEN", "REPORTED").contains(status)) {
            throw new IllegalArgumentException("알 수 없는 상태입니다.");
        }
        // 모더레이션은 남의 글을 다루는 일이라 소유권으로 열 수 없다 — 담당자 이상 고정.
        // 관리 화면(/adm/**)에서만 부르지만 판정을 URL 규칙에만 맡기지 않는다
        // (규칙 한 줄이 느슨해지면 그대로 뚫린다).
        if (!accessGuard.hasRole(MANAGE_ROLE)) {
            throw new AccessDeniedException("댓글 모더레이션은 담당자 이상만 가능합니다.");
        }
        BbsCommentDto comment = bbsCommentMapper.findById(commentId);
        if (comment == null) {
            return;
        }
        bbsCommentMapper.updateStatus(commentId, status,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
        log.info("댓글 모더레이션 comment={} status={} actor={}",
                commentId, status, AuditorContext.currentUserId());
        // 숨김도 화면에서는 사라지므로 댓글 수가 따라가야 한다.
        // 증분이 아니라 재계산 — 모더레이션·삭제가 섞이면 증감으로는 반드시 어긋난다.
        bbsArticleMapper.refreshCommentCount(comment.getArticleId());
    }

    @Override
    public boolean canRead(BbsCommentDto comment, String articleWriterUserId) {
        if (comment == null) {
            return false;
        }
        if (!comment.isSecret()) {
            return true;
        }
        LoginPrincipal principal = accessGuard.currentPrincipal();
        if (principal == null) {
            return false;
        }
        if (accessGuard.hasRole(MANAGE_ROLE)) {
            return true;
        }
        String me = principal.userId();
        // 비밀댓글은 '나와 글쓴이' 사이의 대화다 — 둘 다 볼 수 있어야 성립한다
        return me != null
                && (me.equals(comment.getWriterUserId()) || me.equals(articleWriterUserId));
    }

    @Override
    public boolean canManage(BbsCommentDto comment) {
        if (comment == null) {
            return false;
        }
        if (accessGuard.hasRole(MANAGE_ROLE)) {
            return true;                              // 담당자 이상은 모더레이션 권한으로
        }
        LoginPrincipal principal = accessGuard.currentPrincipal();
        // writerUserId 가 비어 있으면(과거 익명 행) 소유자로 인정하지 않는다 —
        // null == null 로 통과시키면 비로그인이 남의 댓글을 지운다
        return principal != null && principal.userId() != null
                && principal.userId().equals(comment.getWriterUserId());
    }

    /** 댓글이 붙을 수 있는 글인지 — 존재하고 공개 상태여야 한다. */
    private BbsArticleAdmDto requirePublishedArticle(String articleId) {
        BbsArticleAdmDto article = articleId == null || articleId.isBlank()
                ? null : bbsArticleMapper.findById(articleId);
        if (article == null || !"PUBLISHED".equals(article.getStatus())) {
            throw new IllegalArgumentException("댓글을 달 수 없는 글입니다.");
        }
        return article;
    }

    /**
     * 스레드 위치 결정 — 상위 댓글이 있으면 depth+1, 없으면 1.
     *
     * <p>{@link #MAX_DEPTH} 를 넘으면 거절하지 않고 <b>상위 댓글의 형제</b>로 붙인다.
     * 사용자 입장에서는 답글이 사라지지 않고 같은 줄기에 남는다.
     */
    private void applyThreadPosition(BbsCommentDto comment) {
        String parentId = comment.getParentCommentId();
        if (parentId == null || parentId.isBlank()) {
            comment.setParentCommentId(null);
            comment.setDepth(1);
            return;
        }
        BbsCommentDto parent = bbsCommentMapper.findById(parentId);
        if (parent == null) {
            throw new IllegalArgumentException("상위 댓글을 찾을 수 없습니다.");
        }
        if (!parent.getArticleId().equals(comment.getArticleId())) {
            throw new IllegalArgumentException("다른 글의 댓글에는 답글을 달 수 없습니다.");
        }
        int parentDepth = parent.getDepth() == null ? 1 : parent.getDepth();
        if (parentDepth >= MAX_DEPTH) {
            // 평탄화 — 손자가 될 자리를 조부모의 자식으로 옮긴다
            comment.setParentCommentId(parent.getParentCommentId());
            comment.setDepth(MAX_DEPTH);
            return;
        }
        comment.setDepth(parentDepth + 1);
    }
}
