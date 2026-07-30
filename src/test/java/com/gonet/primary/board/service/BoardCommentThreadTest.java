package com.gonet.primary.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gonet.common.web.LoginPrincipal;
import com.gonet.primary.board.dto.BbsArticleAdmDto;
import com.gonet.primary.board.dto.BbsCommentDto;
import com.gonet.primary.board.mapper.BbsArticleMapper;
import com.gonet.primary.board.mapper.BbsCommentMapper;
import com.gonet.primary.file.service.FileAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

/**
 * 댓글 스레드 규칙 — 2단 제한과 평탄화, 그리고 삭제·모더레이션 권한.
 *
 * <p>답글을 거절하는 대신 상위 줄기로 붙이는 선택이라, 규칙이 바뀌면 사용자에게 보이는
 * 동작이 바뀐다. 여기 고정해 둔다.
 *
 * <p>권한 케이스를 함께 두는 이유: 댓글에는 site_id 가 없어 <b>글을 거쳐야만</b> 사이트를
 * 알 수 있다. 그래서 스코프·소유권 검사가 빠져도 기능은 멀쩡히 동작하고, 리뷰로는
 * 드러나지 않는다(실측: ID 하나로 남의 사이트 댓글까지 지워지고 있었다).
 */
class BoardCommentThreadTest {

    private static final String ARTICLE = "BBA_00000000-0000-7000-8000-00000000000a";
    private static final String OTHER_ARTICLE = "BBA_00000000-0000-7000-8000-0000000000ff";
    private static final String TOP = "BBC_00000000-0000-7000-8000-000000000001";
    private static final String REPLY = "BBC_00000000-0000-7000-8000-000000000002";
    private static final String ME = "MBR_1";

    private final BbsCommentMapper commentMapper = mock(BbsCommentMapper.class);
    private final BbsArticleMapper articleMapper = mock(BbsArticleMapper.class);
    private final FileAccessGuard guard = mock(FileAccessGuard.class);
    private final BoardCommentServiceImpl service =
            new BoardCommentServiceImpl(commentMapper, articleMapper, guard);

    @BeforeEach
    void login() {
        when(guard.currentPrincipal()).thenReturn(new LoginPrincipal(
                "MEMBER", ME, "user1", "홍길동", "SIT_1", "ai", "ROL_1", false));
        when(articleMapper.findById(ARTICLE)).thenReturn(publishedArticle());
    }

    private BbsArticleAdmDto publishedArticle() {
        BbsArticleAdmDto a = new BbsArticleAdmDto();
        a.setArticleId(ARTICLE);
        a.setStatus("PUBLISHED");
        return a;
    }

    private BbsCommentDto newComment(String parentId) {
        BbsCommentDto c = new BbsCommentDto();
        c.setArticleId(ARTICLE);
        c.setParentCommentId(parentId);
        c.setContent("댓글");
        return c;
    }

    /** 저장돼 있는 댓글 — 기본은 <b>내가 쓴</b> 댓글(삭제 권한 있음). */
    private BbsCommentDto stored(String id, String parentId, int depth) {
        BbsCommentDto c = new BbsCommentDto();
        c.setCommentId(id);
        c.setArticleId(ARTICLE);
        c.setParentCommentId(parentId);
        c.setDepth(depth);
        c.setWriterUserId(ME);
        return c;
    }

    private BbsCommentDto captureInserted() {
        ArgumentCaptor<BbsCommentDto> captor = ArgumentCaptor.forClass(BbsCommentDto.class);
        verify(commentMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("최상위 댓글은 depth 1, 부모 없음")
    void topLevel() {
        service.write(newComment(null));

        BbsCommentDto saved = captureInserted();
        assertThat(saved.getDepth()).isEqualTo(1);
        assertThat(saved.getParentCommentId()).isNull();
        verify(articleMapper).refreshCommentCount(ARTICLE);
    }

    @Test
    @DisplayName("최상위에 달린 답글은 depth 2")
    void reply() {
        when(commentMapper.findById(TOP)).thenReturn(stored(TOP, null, 1));

        service.write(newComment(TOP));

        BbsCommentDto saved = captureInserted();
        assertThat(saved.getDepth()).isEqualTo(2);
        assertThat(saved.getParentCommentId()).isEqualTo(TOP);
    }

    @Test
    @DisplayName("3단은 만들지 않는다 — 거절 대신 조부모의 자식으로 평탄화")
    void flattensThirdLevel() {
        when(commentMapper.findById(REPLY)).thenReturn(stored(REPLY, TOP, 2));

        service.write(newComment(REPLY));

        BbsCommentDto saved = captureInserted();
        assertThat(saved.getDepth()).isEqualTo(2);
        // 손자가 되는 대신 같은 대화 줄기(TOP)의 자식으로 남는다
        assertThat(saved.getParentCommentId()).isEqualTo(TOP);
    }

    @Test
    @DisplayName("다른 글의 댓글에는 답글을 달 수 없다")
    void rejectsCrossArticleReply() {
        BbsCommentDto other = stored(TOP, null, 1);
        other.setArticleId(OTHER_ARTICLE);
        when(commentMapper.findById(TOP)).thenReturn(other);

        assertThatThrownBy(() -> service.write(newComment(TOP)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("다른 글");
    }

    @Test
    @DisplayName("없는 글·비공개 글에는 댓글을 달 수 없다")
    void rejectsCommentOnMissingArticle() {
        when(articleMapper.findById(ARTICLE)).thenReturn(null);

        assertThatThrownBy(() -> service.write(newComment(null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(commentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("삭제는 자식 대댓글까지 함께 — 부모 없는 답글을 남기지 않는다")
    void deleteCascadesToChildren() {
        when(commentMapper.findById(TOP)).thenReturn(stored(TOP, null, 1));

        service.delete(TOP, ARTICLE);

        verify(commentMapper).softDeleteChildren(eq(TOP), nullable(String.class), nullable(String.class));
        verify(commentMapper).softDelete(eq(TOP), nullable(String.class), nullable(String.class));
        verify(articleMapper).refreshCommentCount(ARTICLE);
    }

    @Test
    @DisplayName("남이 쓴 댓글은 지울 수 없다 — 담당자가 아니면 소유자만")
    void rejectsDeletingSomeoneElsesComment() {
        BbsCommentDto others = stored(TOP, null, 1);
        others.setWriterUserId("MBR_9");
        when(commentMapper.findById(TOP)).thenReturn(others);

        assertThatThrownBy(() -> service.delete(TOP, ARTICLE))
                .isInstanceOf(AccessDeniedException.class);
        verify(commentMapper, never()).softDelete(any(), any(), any());
    }

    @Test
    @DisplayName("작성자를 알 수 없는 댓글은 소유권으로 열리지 않는다")
    void rejectsDeletingOrphanWriterComment() {
        BbsCommentDto orphan = stored(TOP, null, 1);
        orphan.setWriterUserId(null);
        when(commentMapper.findById(TOP)).thenReturn(orphan);

        assertThatThrownBy(() -> service.delete(TOP, ARTICLE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("다른 글의 댓글은 이 글 주소로 지울 수 없다 — 사이트 경계가 여기서 선다")
    void rejectsDeletingCommentOfAnotherArticle() {
        BbsCommentDto elsewhere = stored(TOP, null, 1);
        elsewhere.setArticleId(OTHER_ARTICLE);
        when(commentMapper.findById(TOP)).thenReturn(elsewhere);

        // 내가 쓴 댓글이어도 스코프가 다르면 거절 — 소유권보다 스코프가 먼저다
        assertThatThrownBy(() -> service.delete(TOP, ARTICLE))
                .isInstanceOf(AccessDeniedException.class);
        verify(commentMapper, never()).softDelete(any(), any(), any());
    }

    @Test
    @DisplayName("담당자는 남의 댓글도 지울 수 있다")
    void staffCanDeleteAnyComment() {
        when(guard.hasRole("ROLE_STAFF")).thenReturn(true);
        BbsCommentDto others = stored(TOP, null, 1);
        others.setWriterUserId("MBR_9");
        when(commentMapper.findById(TOP)).thenReturn(others);

        service.delete(TOP, ARTICLE);

        verify(commentMapper).softDelete(eq(TOP), nullable(String.class), nullable(String.class));
    }

    @Test
    @DisplayName("숨김도 댓글 수를 다시 센다 — 증분으로는 반드시 어긋난다")
    void moderateRecountsComments() {
        when(guard.hasRole("ROLE_STAFF")).thenReturn(true);
        when(commentMapper.findById(TOP)).thenReturn(stored(TOP, null, 1));

        service.moderate(TOP, "HIDDEN");

        verify(commentMapper).updateStatus(eq(TOP), eq("HIDDEN"),
                nullable(String.class), nullable(String.class));
        verify(articleMapper).refreshCommentCount(ARTICLE);
    }

    @Test
    @DisplayName("모더레이션은 담당자 이상 — 소유자라고 열리지 않는다")
    void rejectsModerationByOwner() {
        when(commentMapper.findById(TOP)).thenReturn(stored(TOP, null, 1));

        assertThatThrownBy(() -> service.moderate(TOP, "HIDDEN"))
                .isInstanceOf(AccessDeniedException.class);
        verify(commentMapper, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    @DisplayName("알 수 없는 상태로는 모더레이션할 수 없다")
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> service.moderate(TOP, "WHATEVER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 내용은 저장하지 않는다")
    void rejectsBlankContent() {
        BbsCommentDto blank = newComment(null);
        blank.setContent("   ");

        assertThatThrownBy(() -> service.write(blank))
                .isInstanceOf(IllegalArgumentException.class);
        verify(commentMapper, never()).insert(any());
    }
}
