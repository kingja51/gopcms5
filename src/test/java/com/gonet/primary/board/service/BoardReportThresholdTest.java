package com.gonet.primary.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gonet.common.web.LoginPrincipal;
import com.gonet.primary.board.dto.BbsReportDto;
import com.gonet.primary.board.mapper.BbsArticleMapper;
import com.gonet.primary.board.mapper.BbsCommentMapper;
import com.gonet.primary.board.mapper.BbsReportMapper;
import com.gonet.primary.file.service.FileAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 신고 임계·검토 규칙 고정.
 *
 * <p>자동 숨김은 <b>잠정 조치</b>라는 전제가 이 테스트의 핵심이다 — 기각하면 반드시
 * 되살아나야 하고, 그러지 않으면 조직적 신고로 멀쩡한 글을 영구히 내릴 수 있다.
 */
class BoardReportThresholdTest {

    private static final String ARTICLE = "BBA_00000000-0000-7000-8000-00000000000a";

    private final BbsReportMapper reportMapper = mock(BbsReportMapper.class);
    private final BbsCommentMapper commentMapper = mock(BbsCommentMapper.class);
    private final BbsArticleMapper articleMapper = mock(BbsArticleMapper.class);
    private final FileAccessGuard guard = mock(FileAccessGuard.class);
    private final BoardReportServiceImpl service = new BoardReportServiceImpl(
            reportMapper, commentMapper, articleMapper, guard);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "threshold", 5);
        when(guard.currentPrincipal()).thenReturn(new LoginPrincipal(
                "MEMBER", "MBR_1", "user1", "홍길동", "SIT_1", "ai", "ROL_1", false));
    }

    @Test
    @DisplayName("임계 미만이면 대상 상태를 건드리지 않는다")
    void belowThresholdKeepsStatus() {
        when(reportMapper.countActive("ARTICLE", ARTICLE)).thenReturn(4);

        int count = service.report("ARTICLE", ARTICLE, "SPAM", null, "/bbs/ai/free");

        assertThat(count).isEqualTo(4);
        verify(reportMapper, never()).updateArticleStatus(anyString(), anyString(),
                nullable(String.class), nullable(String.class));
    }

    @Test
    @DisplayName("임계에 도달하면 자동으로 REPORTED — 삭제가 아니라 숨김이다")
    void atThresholdHidesTarget() {
        when(reportMapper.countActive("ARTICLE", ARTICLE)).thenReturn(5);

        service.report("ARTICLE", ARTICLE, "SPAM", null, null);

        verify(reportMapper).updateArticleStatus(eq(ARTICLE), eq("REPORTED"),
                nullable(String.class), nullable(String.class));
    }

    @Test
    @DisplayName("임계를 0 으로 두면 자동 전환이 꺼진다")
    void thresholdOffDisablesAutoHide() {
        ReflectionTestUtils.setField(service, "threshold", 0);
        when(reportMapper.countActive("ARTICLE", ARTICLE)).thenReturn(99);

        service.report("ARTICLE", ARTICLE, "SPAM", null, null);

        verify(reportMapper, never()).updateArticleStatus(anyString(), anyString(),
                nullable(String.class), nullable(String.class));
    }

    @Test
    @DisplayName("같은 사람이 같은 대상을 다시 신고하면 접수하지 않는다")
    void rejectsDuplicateReport() {
        when(reportMapper.countByReporter("ARTICLE", ARTICLE, "MBR_1")).thenReturn(1);

        assertThatThrownBy(() -> service.report("ARTICLE", ARTICLE, "SPAM", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 신고");
        verify(reportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("사유·대상 유형·식별자는 앱이 검증한다 — 다형 참조라 DB 가 잡아 주지 않는다")
    void validatesInput() {
        assertThatThrownBy(() -> service.report("NOPE", ARTICLE, "SPAM", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report("ARTICLE", "not-a-uuid", "SPAM", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report("ARTICLE", ARTICLE, "WHATEVER", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기각하면 자동 숨김이 풀린다 — 사람 판단이 자동 조치보다 위다")
    void rejectRestoresTarget() {
        when(reportMapper.findById("RPT_1")).thenReturn(report());

        service.review("RPT_1", "REJECTED", "문제 없음", false);

        verify(reportMapper).updateArticleStatus(eq(ARTICLE), eq("PUBLISHED"),
                nullable(String.class), nullable(String.class));
    }

    @Test
    @DisplayName("조치로 처리하면서 숨김을 선택하면 HIDDEN")
    void reviewCanHide() {
        when(reportMapper.findById("RPT_1")).thenReturn(report());

        service.review("RPT_1", "REVIEWED", "조치함", true);

        verify(reportMapper).updateArticleStatus(eq(ARTICLE), eq("HIDDEN"),
                nullable(String.class), nullable(String.class));
    }

    @Test
    @DisplayName("알 수 없는 처리 상태는 거부한다")
    void rejectsUnknownReviewStatus() {
        assertThatThrownBy(() -> service.review("RPT_1", "MAYBE", null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BbsReportDto report() {
        BbsReportDto r = new BbsReportDto();
        r.setReportId("RPT_1");
        r.setTargetType("ARTICLE");
        r.setTargetId(ARTICLE);
        return r;
    }
}
