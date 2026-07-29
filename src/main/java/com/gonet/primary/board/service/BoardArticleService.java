package com.gonet.primary.board.service;

import com.gonet.common.web.PageResult;
import com.gonet.primary.board.dto.BbsArticleAdmDto;
import com.gonet.primary.board.dto.BbsArticleSearch;
import com.gonet.primary.board.dto.BbsMasterAdmDto;

/** 게시글 — 작성·수정·삭제·열람 판정. */
public interface BoardArticleService {

    PageResult<BbsArticleAdmDto> getPage(BbsArticleSearch cond);

    /** 상세 — 삭제글은 null. 비밀글 열람 판정은 {@link #canRead} 로 따로 묻는다. */
    BbsArticleAdmDto get(String articleId);

    /**
     * 저장. 신규면 PK 를 채워 넣는다(폼에서 미리 발급한 값이 있으면 그대로 쓴다 —
     * 첨부 picker 가 그 ID 로 이미 파일을 올려 두었기 때문이다).
     */
    void save(BbsArticleAdmDto article, BbsMasterAdmDto master);

    void delete(String articleId);

    /** 비밀글 열람 가능 여부 — 작성자 본인 또는 담당자 이상. */
    boolean canRead(BbsArticleAdmDto article);

    /**
     * 게시판 읽기 권한 — 마스터의 {@code read_auth} 를 실행한다.
     *
     * @throws org.springframework.security.authentication.InsufficientAuthenticationException 비로그인
     * @throws org.springframework.security.access.AccessDeniedException 권한 부족
     */
    void requireRead(BbsMasterAdmDto master);

    /**
     * 이 게시판을 볼 수 있는지 — 예외 없이 묻는다.
     *
     * <p>합본이 대상 목록을 추릴 때 쓴다. 볼 수 없는 게시판을 합치면 <b>목록에 제목이
     * 새어 나간다</b> — 상세를 막아도 소용없다(실측 발견).
     */
    boolean isReadable(BbsMasterAdmDto master);

    /** 글을 고치거나 지울 수 있는지 — 작성자 본인 또는 담당자 이상. */
    boolean canManage(BbsArticleAdmDto article);

    /**
     * 통합 게시판 문맥까지 반영한 관리 권한.
     *
     * <p>합본(B7)은 <b>읽기 전용</b>이다. 통합 URL 로 들어온 글은 작성자 본인이라도 여기서
     * 고칠 수 없다 — 어느 게시판의 정책으로 저장할지가 모호해지기 때문이다. 원 게시판으로
     * 가면 평소대로 수정할 수 있다.
     */
    boolean canManage(BbsArticleAdmDto article, BbsMasterAdmDto context);

    /**
     * 글쓰기 버튼을 보여줄지 — 화면 판단용.
     *
     * <p>실제 차단은 {@link #save} 가 한다. UI 가드만 두면 URL 직접 호출로 우회된다
     * (원전이 통합 게시판에서 실제로 겪은 구멍 — PLAN P9-6 참조).
     */
    boolean canWrite(BbsMasterAdmDto master);

    /** 조회수 +1 (감사컬럼 미갱신). 중복 방지는 {@link ArticleViewCounter} 가 판단한다. */
    void increaseViewCount(String articleId);
}
