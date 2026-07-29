package com.gonet.primary.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gonet.common.web.LoginPrincipal;
import com.gonet.primary.board.dto.BbsArticleAdmDto;
import com.gonet.primary.board.dto.BbsMasterAdmDto;
import com.gonet.primary.board.mapper.BbsArticleMapper;
import com.gonet.primary.file.service.FileAccessGuard;
import com.gonet.primary.file.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * 통합 게시판(B7) 규칙 — <b>합본은 읽기 전용</b>.
 *
 * <p>원전은 UI 에서 버튼만 감추고 서비스 가드를 후속으로 미뤄, URL 을 직접 부르면 합본으로
 * 글을 쓸 수 있었다(원전 §14-8 자인). 그 구멍이 다시 열리지 않도록 여기 고정한다.
 */
class BoardAggregatorTest {

    private static final String OWN = "BBM_00000000-0000-7000-8000-000000000001";
    private static final String OTHER = "BBM_00000000-0000-7000-8000-000000000002";
    private static final String AGG = "BBM_00000000-0000-7000-8000-000000000009";

    private final BbsArticleMapper articleMapper = mock(BbsArticleMapper.class);
    private final FileService fileService = mock(FileService.class);
    private final FileAccessGuard guard = mock(FileAccessGuard.class);
    private final BoardArticleServiceImpl service =
            new BoardArticleServiceImpl(articleMapper, fileService, guard);

    private BbsMasterAdmDto master(String id, String groupedIds) {
        BbsMasterAdmDto m = new BbsMasterAdmDto();
        m.setBbsMasterId(id);
        m.setBbsCode("code-" + id.substring(id.length() - 1));
        m.setWriteAuth("MEMBER");
        m.setReadAuth("ALL");
        m.setGroupedBoardIds(groupedIds);
        return m;
    }

    private BbsArticleAdmDto article(String bbsMasterId, String writerUserId) {
        BbsArticleAdmDto a = new BbsArticleAdmDto();
        a.setArticleId("BBA_00000000-0000-7000-8000-00000000000a");
        a.setBbsMasterId(bbsMasterId);
        a.setWriterUserId(writerUserId);
        a.setTitle("제목");
        a.setContent("본문");
        return a;
    }

    private void loginAs(String userId) {
        when(guard.currentPrincipal()).thenReturn(new LoginPrincipal(
                "MEMBER", userId, "user1", "홍길동", "SIT_1", "ai", "ROL_1", false));
    }

    @Test
    @DisplayName("합본에는 글을 쓸 수 없다 — 화면이 아니라 서비스가 막는다")
    void aggregatorRejectsWrite() {
        loginAs("MBR_1");

        assertThatThrownBy(() -> service.save(article(OWN, "MBR_1"), master(AGG, OWN + "," + OTHER)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("통합 게시판");
    }

    @Test
    @DisplayName("합본에서는 글쓰기 버튼도 나오지 않는다")
    void aggregatorHidesWriteButton() {
        loginAs("MBR_1");

        assertThat(service.canWrite(master(AGG, OWN))).isFalse();
        assertThat(service.canWrite(master(OWN, null))).isTrue();
    }

    @Test
    @DisplayName("합본으로 들어온 글은 작성자 본인이라도 수정할 수 없다")
    void aggregatorContextBlocksOwnerEdit() {
        loginAs("MBR_1");
        BbsArticleAdmDto mine = article(OWN, "MBR_1");

        // 원 게시판 문맥이면 수정 가능
        assertThat(service.canManage(mine, master(OWN, null))).isTrue();
        // 같은 글이라도 합본 문맥이면 불가
        assertThat(service.canManage(mine, master(AGG, OWN + "," + OTHER))).isFalse();
    }

    @Test
    @DisplayName("다른 게시판 문맥으로 넘어온 글도 관리 대상이 아니다")
    void foreignContextBlocksEdit() {
        loginAs("MBR_1");

        assertThat(service.canManage(article(OTHER, "MBR_1"), master(OWN, null))).isFalse();
    }

    @Test
    @DisplayName("통합 대상 CSV 가 비어 있으면 합본이 아니다 — 평범한 게시판으로 동작")
    void emptyGroupIsNotAggregator() {
        loginAs("MBR_1");
        BbsMasterAdmDto plain = master(OWN, "   ");

        assertThat(plain.isAggregator()).isFalse();
        assertThat(service.canWrite(plain)).isTrue();
    }
}
