package com.gonet.primary.board.controller;

import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.board.dto.BbsArticleAdmDto;
import com.gonet.primary.board.dto.BbsArticleSearch;
import com.gonet.primary.board.dto.BbsCommentDto;
import com.gonet.primary.board.dto.BbsMasterAdmDto;
import com.gonet.primary.board.service.ArticleViewCounter;
import com.gonet.primary.board.service.BoardArticleService;
import com.gonet.primary.board.service.BoardCategoryService;
import com.gonet.primary.board.service.BoardCommentService;
import com.gonet.primary.board.service.BoardMasterService;
import com.gonet.primary.file.service.FileService;
import com.gonet.primary.site.dto.SiteContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 게시판 사용자 화면 — {@code /bbs/{siteCode}/{bbsCode}} (P9-0 라우팅 계약).
 *
 * <p>컨트롤러는 <b>논리 뷰명만</b> 반환한다({@code front/board/**}). 어느 레이아웃으로
 * 그릴지는 {@code SiteTemplateViewResolver} 가 사이트 컨텍스트를 보고 정한다 —
 * 그래서 같은 코드가 7종 레이아웃 어디에 얹혀도 동작한다.
 *
 * <p>타입별(공지·FAQ·갤러리…) 차이는 뷰명을 갈라 8벌을 만드는 대신 <b>공용 뷰 한 벌이
 * 타입별 조각을 고르는</b> 방식으로 둔다. 사이트/레이아웃 오버라이드(Resolver 3단 폴백)와
 * 타입 분기가 곱해지면 조합이 폭발하고, 타입 하나를 더할 때마다 폴백 3벌을 함께
 * 만들어야 한다.
 */
@Controller
@RequestMapping("/bbs/{siteCode}/{bbsCode}")
@RequiredArgsConstructor
public class BoardUsrController {

    /** 글 ID 는 형식이 고정돼 있어 {@code /write} 같은 고정 경로와 섞이지 않는다. */
    private static final String ARTICLE_ID = "{articleId:BBA_[0-9a-f-]{36}}";

    private final BoardMasterService boardMasterService;
    private final BoardArticleService boardArticleService;
    private final BoardCategoryService boardCategoryService;
    private final BoardCommentService boardCommentService;
    private final ArticleViewCounter articleViewCounter;
    private final FileService fileService;

    @GetMapping
    public String list(@PathVariable String siteCode, @PathVariable String bbsCode,
            @ModelAttribute("cond") BbsArticleSearch cond, Model model) throws Exception {
        BbsMasterAdmDto master = requireMaster(bbsCode);
        boardArticleService.requireRead(master);

        cond.setBbsMasterId(master.getBbsMasterId());
        cond.setStatus("PUBLISHED");            // 숨김·신고보류는 사용자에게 보이지 않는다
        if (master.isAggregator()) {
            // 합본 — 살아 있고 <b>이 사용자가 볼 수 있는</b> 대상만 모은다.
            // 볼 수 없는 게시판을 합치면 상세를 막아도 목록에 제목이 새어 나간다(실측 발견).
            // 대상이 하나도 없으면 빈 목록이 되어야지 조건 없는 전체 조회가 되면 안 된다
            // (빈 IN 절 방지용 sentinel).
            List<String> ids = boardMasterService.getGroupedTargets(master).stream()
                    .filter(boardArticleService::isReadable)
                    .map(BbsMasterAdmDto::getBbsMasterId).toList();
            cond.setBbsMasterIds(ids.isEmpty() ? List.of("-") : ids);
        }
        model.addAttribute("master", master);
        model.addAttribute("page", boardArticleService.getPage(cond));
        model.addAttribute("categories", boardCategoryService.getByBoard(master.getBbsMasterId()));
        model.addAttribute("boardBase", base(siteCode, bbsCode));
        model.addAttribute("canWrite", boardArticleService.canWrite(master));
        return "front/board/list";
    }

    @GetMapping(ARTICLE_ID)
    public String detail(@PathVariable String siteCode, @PathVariable String bbsCode,
            @PathVariable String articleId, Model model,
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        BbsMasterAdmDto master = requireMaster(bbsCode);
        boardArticleService.requireRead(master);

        BbsArticleAdmDto article = boardArticleService.get(articleId);
        if (article == null || !"PUBLISHED".equals(article.getStatus())
                || !belongsTo(master, article)) {
            throw new NoResourceFoundException(
                    org.springframework.http.HttpMethod.valueOf(request.getMethod()),
                    request.getRequestURI());
        }
        // 합본은 원 게시판의 읽기 권한을 그대로 물려받는다 — 통합이 ALL 이라고 해서
        // 회원 전용 게시판의 글이 공개되면 통합 게시판이 우회 통로가 된다
        BbsMasterAdmDto origin = master;
        if (master.isAggregator()) {
            origin = boardMasterService.getAdm(article.getBbsMasterId());
            boardArticleService.requireRead(origin);
        }

        boolean readable = boardArticleService.canRead(article);
        // 볼 수 없는 글은 조회수도 올리지 않는다 — 못 본 글의 조회수가 오르면 안 된다
        if (readable) {
            articleViewCounter.countOnce(articleId, request, response);
            model.addAttribute("comments", boardCommentService.getByArticle(articleId));
            if (article.getFileGroupId() != null) {
                model.addAttribute("attachedFiles",
                        fileService.findByGroup(article.getFileGroupId()));
            }
        }

        model.addAttribute("master", master);
        model.addAttribute("article", article);
        model.addAttribute("readable", readable);
        model.addAttribute("canManage", boardArticleService.canManage(article, master));
        model.addAttribute("origin", origin);
        model.addAttribute("originBase", base(siteCode, origin.getBbsCode()));
        model.addAttribute("boardBase", base(siteCode, bbsCode));
        return "front/board/detail";
    }

    @GetMapping("/write")
    public String write(@PathVariable String siteCode, @PathVariable String bbsCode,
            @RequestParam(required = false) String articleId, Model model) throws Exception {
        BbsMasterAdmDto master = requireMaster(bbsCode);
        boardArticleService.requireRead(master);
        if (!boardArticleService.canWrite(master)) {
            throw new AccessDeniedException("이 게시판에 글을 쓸 권한이 없습니다.");
        }

        BbsArticleAdmDto article;
        if (articleId == null || articleId.isBlank()) {
            article = new BbsArticleAdmDto();
            // 폼을 여는 시점에 PK 발급 — 첨부 picker 가 저장 전에 이 ID 로 올린다
            article.setArticleId(Uid.next(UidPrefix.BBA));
            article.setNoticeYn("N");
            article.setSecretYn("N");
        } else {
            article = boardArticleService.get(articleId);
            if (article == null || !boardArticleService.canManage(article, master)) {
                throw new AccessDeniedException("본인이 쓴 글만 수정할 수 있습니다.");
            }
            if (article.getFileGroupId() != null) {
                model.addAttribute("attachedFiles",
                        fileService.findByGroup(article.getFileGroupId()));
            }
        }
        model.addAttribute("master", master);
        model.addAttribute("article", article);
        model.addAttribute("categories", boardCategoryService.getByBoard(master.getBbsMasterId()));
        model.addAttribute("boardBase", base(siteCode, bbsCode));
        return "front/board/write";
    }

    @PostMapping("/save")
    public String save(@PathVariable String siteCode, @PathVariable String bbsCode,
            @ModelAttribute("article") BbsArticleAdmDto article,
            RedirectAttributes redirect) {
        BbsMasterAdmDto master = requireMaster(bbsCode);
        try {
            boardArticleService.save(article, master);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            return "redirect:" + base(siteCode, bbsCode) + "/write?articleId="
                    + article.getArticleId();
        }
        return "redirect:" + base(siteCode, bbsCode) + "/" + article.getArticleId();
    }

    @PostMapping("/delete")
    public String delete(@PathVariable String siteCode, @PathVariable String bbsCode,
            @RequestParam String articleId, RedirectAttributes redirect) {
        BbsMasterAdmDto master = requireMaster(bbsCode);
        BbsArticleAdmDto article = boardArticleService.get(articleId);
        if (article == null || !boardArticleService.canManage(article, master)) {
            throw new AccessDeniedException("본인이 쓴 글만 삭제할 수 있습니다.");
        }
        boardArticleService.delete(articleId);
        redirect.addFlashAttribute("flashOk", "삭제되었습니다.");
        return "redirect:" + base(siteCode, bbsCode);
    }

    /* ── 댓글 — 상세 화면에서 처리한다 ─────────────────────────────────── */

    @PostMapping(ARTICLE_ID + "/comment")
    public String writeComment(@PathVariable String siteCode, @PathVariable String bbsCode,
            @PathVariable String articleId, @ModelAttribute BbsCommentDto comment,
            RedirectAttributes redirect) {
        BbsMasterAdmDto master = requireMaster(bbsCode);
        if (!"Y".equals(master.getCommentYn())) {
            throw new AccessDeniedException("댓글을 받지 않는 게시판입니다.");
        }
        comment.setArticleId(articleId);
        try {
            boardCommentService.write(comment);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:" + base(siteCode, bbsCode) + "/" + articleId;
    }

    @PostMapping(ARTICLE_ID + "/comment/delete")
    public String deleteComment(@PathVariable String siteCode, @PathVariable String bbsCode,
            @PathVariable String articleId, @RequestParam String commentId,
            RedirectAttributes redirect) {
        boardCommentService.delete(commentId);
        redirect.addFlashAttribute("flashOk", "댓글을 삭제했습니다.");
        return "redirect:" + base(siteCode, bbsCode) + "/" + articleId;
    }

    /* ── 부속 ─────────────────────────────────────────────────────────── */

    /**
     * 게시판 해석 — 현재 사이트 안에서만 찾는다.
     *
     * <p>사이트 컨텍스트는 {@code SiteResolveFilter} 가 두 번째 세그먼트로 이미 세워 두었다.
     * 경로의 siteCode 를 다시 믿지 않는 이유는 그 두 값이 어긋나면 다른 사이트의 게시판을
     * 열 수 있기 때문이다.
     */
    private BbsMasterAdmDto requireMaster(String bbsCode) {
        SiteContext site = SiteContextHolder.get();
        BbsMasterAdmDto master = site == null
                ? null : boardMasterService.getByCode(site.getSiteId(), bbsCode);
        if (master == null || !"Y".equals(master.getUseYn())) {
            // 사용 중지 게시판도 없는 것으로 — 존재 여부를 알려 줄 이유가 없다
            throw new IllegalArgumentException("게시판을 찾을 수 없습니다.");
        }
        return master;
    }

    /** 이 URL 로 열 수 있는 글인지 — 일반 게시판은 자기 글만, 합본은 대상 게시판의 글까지. */
    private boolean belongsTo(BbsMasterAdmDto master, BbsArticleAdmDto article) {
        if (master.getBbsMasterId().equals(article.getBbsMasterId())) {
            return true;
        }
        return master.isAggregator()
                && boardMasterService.getGroupedTargets(master).stream()
                        .anyMatch(t -> t.getBbsMasterId().equals(article.getBbsMasterId()));
    }

    private String base(String siteCode, String bbsCode) {
        return "/bbs/" + siteCode + "/" + bbsCode;
    }
}
