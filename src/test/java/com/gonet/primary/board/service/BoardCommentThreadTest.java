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
import com.gonet.primary.board.dto.BbsCommentDto;
import com.gonet.primary.board.mapper.BbsArticleMapper;
import com.gonet.primary.board.mapper.BbsCommentMapper;
import com.gonet.primary.file.service.FileAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 댓글 스레드 규칙 — 2단 제한과 평탄화.
 *
 * <p>답글을 거절하는 대신 상위 줄기로 붙이는 선택이라, 규칙이 바뀌면 사용자에게 보이는
 * 동작이 바뀐다. 여기 고정해 둔다.
 */
class BoardCommentThreadTest {

    private static final String ARTICLE = "BBA_00000000-0000-7000-8000-00000000000a";
    private static final String TOP = "BBC_00000000-0000-7000-8000-000000000001";
    private static final String REPLY = "BBC_00000000-0000-7000-8000-000000000002";

    private final BbsCommentMapper commentMapper = mock(BbsCommentMapper.class);
    private final BbsArticleMapper articleMapper = mock(BbsArticleMapper.class);
    private final FileAccessGuard guard = mock(FileAccessGuard.class);
    private final BoardCommentServiceImpl service =
            new BoardCommentServiceImpl(commentMapper, articleMapper, guard);

    @BeforeEach
    void login() {
        when(guard.currentPrincipal()).thenReturn(new LoginPrincipal(
                "MEMBER", "MBR_1", "user1", "홍길동", "SIT_1", "ai", "ROL_1", false));
    }

    private BbsCommentDto newComment(String parentId) {
        BbsCommentDto c = new BbsCommentDto();
        c.setArticleId(ARTICLE);
        c.setParentCommentId(parentId);
        c.setContent("댓글");
        return c;
    }

    private BbsCommentDto stored(String id, String parentId, int depth) {
        BbsCommentDto c = new BbsCommentDto();
        c.setCommentId(id);
        c.setArticleId(ARTICLE);
        c.setParentCommentId(parentId);
        c.setDepth(depth);
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
        other.setArticleId("BBA_00000000-0000-7000-8000-0000000000ff");
        when(commentMapper.findById(TOP)).thenReturn(other);

        assertThatThrownBy(() -> service.write(newComment(TOP)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("다른 글");
    }

    @Test
    @DisplayName("삭제는 자식 대댓글까지 함께 — 부모 없는 답글을 남기지 않는다")
    void deleteCascadesToChildren() {
        when(commentMapper.findById(TOP)).thenReturn(stored(TOP, null, 1));

        service.delete(TOP);

        verify(commentMapper).softDeleteChildren(eq(TOP), nullable(String.class), nullable(String.class));
        verify(commentMapper).softDelete(eq(TOP), nullable(String.class), nullable(String.class));
        verify(articleMapper).refreshCommentCount(ARTICLE);
    }

    @Test
    @DisplayName("숨김도 댓글 수를 다시 센다 — 증분으로는 반드시 어긋난다")
    void moderateRecountsComments() {
        when(commentMapper.findById(TOP)).thenReturn(stored(TOP, null, 1));

        service.moderate(TOP, "HIDDEN");

        verify(commentMapper).updateStatus(eq(TOP), eq("HIDDEN"),
                nullable(String.class), nullable(String.class));
        verify(articleMapper).refreshCommentCount(ARTICLE);
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
