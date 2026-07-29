package com.gonet.primary.board.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.HtmlSanitizer;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.common.web.PageResult;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.board.dto.BbsArticleAdmDto;
import com.gonet.primary.board.dto.BbsArticleSearch;
import com.gonet.primary.board.dto.BbsMasterAdmDto;
import com.gonet.primary.board.dto.BbsType;
import com.gonet.primary.board.mapper.BbsArticleMapper;
import com.gonet.primary.file.dto.DownloadAuth;
import com.gonet.primary.file.service.FileAccessGuard;
import com.gonet.primary.file.service.FileService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 서비스.
 *
 * <p>게시판 마스터가 정한 정책(누가 쓰고·첨부를 누가 받는지)이 글 단위로 <b>실행</b>되는
 * 지점이다. 판정을 화면에 두면 URL 직접 호출로 우회되므로 전부 여기서 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
@Slf4j
public class BoardArticleServiceImpl extends AbstractCmsService implements BoardArticleService {

    /** 공지 지정·비밀글 열람의 관리 경계. 계층상 STAFF 이상이면 모두 포함된다. */
    private static final String MANAGE_ROLE = "ROLE_STAFF";
    private static final String ENTITY_TYPE = "BBS";

    private final BbsArticleMapper bbsArticleMapper;
    private final FileService fileService;
    private final FileAccessGuard accessGuard;

    @Override
    public PageResult<BbsArticleAdmDto> getPage(BbsArticleSearch cond) {
        return new PageResult<>(bbsArticleMapper.findPage(cond), bbsArticleMapper.countPage(cond),
                cond.getPage(), cond.getSize());
    }

    @Override
    public BbsArticleAdmDto get(String articleId) {
        return bbsArticleMapper.findById(articleId);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void save(BbsArticleAdmDto article, BbsMasterAdmDto master) {
        // 합본에는 글을 쓸 수 없다 — 화면에서 버튼만 감추면 URL 직접 호출로 뚫린다
        // (원전이 UI 가드만 두고 서비스 가드를 미뤄 실제로 열려 있던 구멍)
        if (master.isAggregator()) {
            throw new AccessDeniedException(
                    "통합 게시판에는 글을 쓸 수 없습니다. 원래 게시판에서 작성해 주세요.");
        }
        LoginPrincipal writer = requireWritePermission(master);
        validate(article, master);
        normalize(article, master, writer);

        boolean isNew = resolveNew(article, master);

        // 첨부 정책은 글의 성격에서 나온다 — 공지면 마스터 정책보다 우선해 공개
        String downloadAuth = resolveArticleDownloadAuth(article, master);
        syncAttachments(article, master, downloadAuth);

        if (isNew) {
            bbsArticleMapper.insert(article);
            log.info("게시글 등록 article={} board={} notice={} download={}",
                    article.getArticleId(), master.getBbsCode(),
                    article.getNoticeYn(), downloadAuth);
        } else {
            bbsArticleMapper.update(article);
        }
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void delete(String articleId) {
        bbsArticleMapper.softDelete(articleId,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
    }

    @Override
    public boolean canRead(BbsArticleAdmDto article) {
        if (article == null) {
            return false;
        }
        if (!article.isSecret()) {
            return true;
        }
        LoginPrincipal principal = accessGuard.currentPrincipal();
        if (principal == null) {
            return false;
        }
        if (accessGuard.hasRole(MANAGE_ROLE)) {
            return true;
        }
        return article.getWriterUserId() != null
                && article.getWriterUserId().equals(principal.userId());
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void increaseViewCount(String articleId) {
        bbsArticleMapper.increaseViewCount(articleId);
    }

    /**
     * 신규인지 수정인지 판정.
     *
     * <p><b>PK 가 비었는지로 판단하면 안 된다</b> — 이 도메인은 폼을 여는 시점에 PK 를 미리
     * 발급한다(첨부 picker 가 저장 전에 그 ID 로 파일을 올리기 때문). 그래서 신규 글도 PK 를
     * 달고 들어오며, 그것을 수정으로 오인하면 존재하지 않는 행을 UPDATE 해 <b>0건 갱신으로
     * 조용히 사라진다</b>(실측 발견). 실제 존재 여부를 물어서 정한다.
     *
     * <p>덤으로 두 가지를 여기서 막는다 — 형식이 틀린 PK 주입, 그리고 다른 게시판의 글을
     * 이 게시판 URL 로 고치는 시도.
     */
    private boolean resolveNew(BbsArticleAdmDto article, BbsMasterAdmDto master) {
        String id = article.getArticleId();
        if (id == null || id.isBlank()) {
            article.setArticleId(Uid.next(UidPrefix.BBA));
            return true;
        }
        if (!id.matches(Uid.PATTERN) || !id.startsWith(UidPrefix.BBA.name() + "_")) {
            throw new IllegalArgumentException("잘못된 게시글 식별자입니다.");
        }
        BbsArticleAdmDto existing = bbsArticleMapper.findById(id);
        if (existing == null) {
            return true;
        }
        if (!master.getBbsMasterId().equals(existing.getBbsMasterId())) {
            throw new AccessDeniedException("다른 게시판의 글은 수정할 수 없습니다.");
        }
        // 쓰기 권한이 있다고 남의 글을 고칠 수 있는 것은 아니다 —
        // 사용자 화면이 붙으면서 실제 위험이 되는 지점(관리 화면만 있을 땐 드러나지 않았다)
        if (!canManage(existing)) {
            throw new AccessDeniedException("본인이 쓴 글만 수정할 수 있습니다.");
        }
        return false;
    }

    @Override
    public void requireRead(BbsMasterAdmDto master) {
        String auth = master.getReadAuth();
        if (auth == null || "ALL".equals(auth)) {
            return;                                  // 공개 게시판
        }
        LoginPrincipal principal = accessGuard.currentPrincipal();
        if (principal == null) {
            // 비로그인은 401 계열 — 로그인하면 열릴 수도 있는 상태와 영구 거부를 구분한다
            throw new InsufficientAuthenticationException("로그인이 필요합니다.");
        }
        if ("ADMIN".equals(auth) && !accessGuard.hasRole(MANAGE_ROLE)) {
            throw new AccessDeniedException("이 게시판을 볼 권한이 없습니다.");
        }
    }

    @Override
    public boolean isReadable(BbsMasterAdmDto master) {
        try {
            requireRead(master);
            return true;
        } catch (InsufficientAuthenticationException | AccessDeniedException e) {
            return false;
        }
    }

    @Override
    public boolean canWrite(BbsMasterAdmDto master) {
        if (master.isAggregator()) {
            return false;                            // 합본은 읽기 전용
        }
        if (accessGuard.currentPrincipal() == null) {
            return false;                            // 비로그인 작성은 지원하지 않는다
        }
        return !"ADMIN".equals(master.getWriteAuth()) || accessGuard.hasRole(MANAGE_ROLE);
    }

    @Override
    public boolean canManage(BbsArticleAdmDto article, BbsMasterAdmDto context) {
        // 합본으로 들어온 글은 읽기 전용 — 어느 게시판 정책으로 저장할지가 모호하다.
        // canManage = (context.bbsMasterId == article.bbsMasterId) 라는 원전 규칙과 같다.
        if (context != null && context.isAggregator()) {
            return false;
        }
        if (context != null && article != null
                && !context.getBbsMasterId().equals(article.getBbsMasterId())) {
            return false;
        }
        return canManage(article);
    }

    @Override
    public boolean canManage(BbsArticleAdmDto article) {
        if (article == null) {
            return false;
        }
        if (accessGuard.hasRole(MANAGE_ROLE)) {
            return true;
        }
        LoginPrincipal principal = accessGuard.currentPrincipal();
        return principal != null && principal.userId() != null
                && principal.userId().equals(article.getWriterUserId());
    }

    /* ── 권한 ──────────────────────────────────────────────────────────── */

    /**
     * 쓰기 권한 — 마스터의 {@code write_auth} 를 실행한다.
     *
     * <ul>
     *   <li>{@code MEMBER} — 로그인한 사용자면 된다</li>
     *   <li>{@code ADMIN} — 담당자 이상(ROLE_STAFF). 공지·보도자료 게시판의 기본값</li>
     * </ul>
     */
    private LoginPrincipal requireWritePermission(BbsMasterAdmDto master) {
        LoginPrincipal principal = accessGuard.currentPrincipal();
        if (principal == null) {
            throw new InsufficientAuthenticationException("로그인이 필요합니다.");
        }
        if ("ADMIN".equals(master.getWriteAuth()) && !accessGuard.hasRole(MANAGE_ROLE)) {
            throw new AccessDeniedException("이 게시판에 글을 쓸 권한이 없습니다.");
        }
        return principal;
    }

    /**
     * 글 첨부의 다운로드 권한.
     *
     * <p><b>공지글은 ANONYMOUS 를 강제</b>한다 — 마스터 정책보다 우선이다. 공지는 알리려고
     * 올리는 글이라 첨부(공고문·서식)가 로그인 뒤에 숨으면 목적을 잃는다. 마스터 정책 변경
     * 시의 cascade 가 공지를 제외하는 것도 같은 이유다(BbsMasterMapper.cascadeDownloadAuth).
     */
    private String resolveArticleDownloadAuth(BbsArticleAdmDto article, BbsMasterAdmDto master) {
        if (article.isNotice()) {
            return DownloadAuth.ANONYMOUS;
        }
        String auth = master.getDownloadAuth();
        return DownloadAuth.isValid(auth) ? auth : DownloadAuth.ROLE_MEMBER;
    }

    /* ── 검증·정리 ─────────────────────────────────────────────────────── */

    private void validate(BbsArticleAdmDto a, BbsMasterAdmDto master) {
        if (a.getTitle() == null || a.getTitle().isBlank()) {
            throw new IllegalArgumentException("제목을 입력해 주세요.");
        }
        if (a.getContent() == null || a.getContent().isBlank()) {
            throw new IllegalArgumentException("내용을 입력해 주세요.");
        }
        // 공지는 목록 맨 위를 차지하고 첨부까지 공개로 바뀐다 — 아무나 지정하게 두지 않는다
        if ("Y".equals(a.getNoticeYn()) && !accessGuard.hasRole(MANAGE_ROLE)) {
            throw new AccessDeniedException("공지 지정은 담당자 이상만 가능합니다.");
        }
        if (BbsType.YOUTUBE.equals(master.getBbsType())
                && (a.getLinkUrl() == null || a.getLinkUrl().isBlank())) {
            throw new IllegalArgumentException("영상 게시판은 원문 링크가 필요합니다.");
        }
    }

    private void normalize(BbsArticleAdmDto a, BbsMasterAdmDto master, LoginPrincipal writer) {
        a.setBbsMasterId(master.getBbsMasterId());
        a.setNoticeYn(yn(a.getNoticeYn()));
        a.setSecretYn(yn(a.getSecretYn()));
        if (a.getStatus() == null || a.getStatus().isBlank()) {
            a.setStatus("PUBLISHED");
        }
        if (a.getCategoryId() != null && a.getCategoryId().isBlank()) {
            a.setCategoryId(null);
        }

        // 본문은 저장 시점에 한 번만 정화한다 — 렌더에서 하면 화면마다 빠뜨릴 수 있고
        // DB 에는 위험한 마크업이 그대로 남는다. 평문 게시판은 손대지 않는다(렌더에서 이스케이프).
        if ("Y".equals(master.getHtmlYn())) {
            a.setContent(HtmlSanitizer.sanitize(a.getContent()));
        }

        // 작성자는 세션에서 온다 — 폼 hidden 을 믿으면 남의 이름으로 글이 올라간다
        a.setWriterUserId(writer.userId());
        a.setWriterUserType(writer.userType());
        if (a.getWriterName() == null || a.getWriterName().isBlank()) {
            a.setWriterName(writer.displayName() != null ? writer.displayName() : writer.loginId());
        }
        a.setClientIp(AuditorContext.currentIp());
    }

    /**
     * 첨부 동기화 — picker 가 보낸 파일 ID CSV 만 남기고 나머지는 soft delete.
     *
     * <p>그룹은 <b>폼 저장 시점에 정책과 함께</b> 만든다. picker 가 먼저 만들게 두면
     * DB 기본값(ROLE_MEMBER)이 박힌 그룹이 잠깐 존재하는 창이 생긴다.
     */
    private void syncAttachments(BbsArticleAdmDto a, BbsMasterAdmDto master, String downloadAuth) {
        List<String> keep = parseCsv(a.getAttachments());
        if (keep.isEmpty() && a.getFileGroupId() == null) {
            return;                                   // 첨부가 없던 글에 계속 없다
        }
        String groupId = fileService.ensureGroup(ENTITY_TYPE, a.getArticleId(),
                master.getSiteId(), downloadAuth);
        fileService.syncAttachments(groupId, keep);
        // 첨부를 모두 뺐으면 그룹 참조도 끊는다 — 빈 그룹은 purge 배치가 회수한다
        a.setFileGroupId(keep.isEmpty() ? null : groupId);
    }

    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String token : Arrays.asList(csv.split(","))) {
            String id = token.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private String yn(String value) {
        return "Y".equals(value) ? "Y" : "N";
    }
}
