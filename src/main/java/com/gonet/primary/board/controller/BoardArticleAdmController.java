package com.gonet.primary.board.controller;

import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.primary.board.dto.BbsArticleAdmDto;
import com.gonet.primary.board.dto.BbsArticleSearch;
import com.gonet.primary.board.dto.BbsMasterAdmDto;
import com.gonet.primary.board.service.BoardArticleService;
import com.gonet.primary.board.service.BoardCategoryService;
import com.gonet.primary.board.service.BoardCommentService;
import com.gonet.primary.board.service.BoardMasterService;
import com.gonet.primary.file.service.FileService;
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

/**
 * 게시글 관리 — 게시판 하나에 종속된 화면이라 URL 도 그 아래에 둔다
 * ({@code /adm/board/{bbsMasterId}/article}). 어느 게시판의 글인지가 경로에 드러나야
 * 실수로 다른 게시판에 쓰는 일이 없다.
 */
@Controller
@RequestMapping("/adm/board/{bbsMasterId}/article")
@RequiredArgsConstructor
public class BoardArticleAdmController {

    private final BoardArticleService boardArticleService;
    private final BoardMasterService boardMasterService;
    private final BoardCategoryService boardCategoryService;
    private final BoardCommentService boardCommentService;
    private final FileService fileService;

    @GetMapping
    public String list(@PathVariable String bbsMasterId,
            @ModelAttribute("cond") BbsArticleSearch cond, Model model) {
        BbsMasterAdmDto master = requireMaster(bbsMasterId);
        cond.setBbsMasterId(bbsMasterId);
        model.addAttribute("master", master);
        model.addAttribute("page", boardArticleService.getPage(cond));
        model.addAttribute("categories", boardCategoryService.getByBoard(bbsMasterId));
        return "adm/board/article/list";
    }

    @GetMapping("/form")
    public String form(@PathVariable String bbsMasterId,
            @RequestParam(required = false) String articleId, Model model) {
        BbsMasterAdmDto master = requireMaster(bbsMasterId);
        BbsArticleAdmDto article;
        if (articleId == null || articleId.isBlank()) {
            article = new BbsArticleAdmDto();
            // 폼을 여는 시점에 PK 를 발급한다 — 첨부 picker 가 저장 전에 이 ID 로 파일을
            // 올리기 때문이다. 저장하지 않고 나가면 purge 배치가 고아 그룹을 회수한다.
            article.setArticleId(Uid.next(UidPrefix.BBA));
            article.setStatus("PUBLISHED");
            article.setNoticeYn("N");
            article.setSecretYn("N");
        } else {
            article = boardArticleService.get(articleId);
            if (article == null) {
                return redirectList(bbsMasterId);
            }
            model.addAttribute("attachedFiles", attachedFiles(article));
            // 댓글은 글에 종속된 모더레이션 대상이라 같은 화면에서 다룬다
            model.addAttribute("comments", boardCommentService.getByArticle(articleId));
        }
        model.addAttribute("master", master);
        model.addAttribute("article", article);
        model.addAttribute("categories", boardCategoryService.getByBoard(bbsMasterId));
        return "adm/board/article/form";
    }

    @PostMapping("/save")
    public String save(@PathVariable String bbsMasterId,
            @ModelAttribute("article") BbsArticleAdmDto article,
            Model model, RedirectAttributes redirect) {
        BbsMasterAdmDto master = requireMaster(bbsMasterId);
        try {
            boardArticleService.save(article, master);
        } catch (IllegalArgumentException | AccessDeniedException e) {
            model.addAttribute("flashError", e.getMessage());
            model.addAttribute("master", master);
            model.addAttribute("categories", boardCategoryService.getByBoard(bbsMasterId));
            model.addAttribute("attachedFiles", attachedFiles(article));
            return "adm/board/article/form";
        }
        redirect.addFlashAttribute("flashOk", "저장되었습니다.");
        return redirectList(bbsMasterId);
    }

    @PostMapping("/delete")
    public String delete(@PathVariable String bbsMasterId,
            @RequestParam String articleId, RedirectAttributes redirect) {
        boardArticleService.delete(articleId);
        redirect.addFlashAttribute("flashOk", "삭제되었습니다.");
        return redirectList(bbsMasterId);
    }

    /* ── 댓글 모더레이션 — 글 화면 안에서 처리한다 ─────────────────────── */

    @PostMapping("/comment/moderate")
    public String moderateComment(@PathVariable String bbsMasterId,
            @RequestParam String articleId, @RequestParam String commentId,
            @RequestParam String status, RedirectAttributes redirect) {
        try {
            boardCommentService.moderate(commentId, status);
            redirect.addFlashAttribute("flashOk",
                    "HIDDEN".equals(status) ? "댓글을 숨겼습니다." : "댓글을 다시 노출했습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return redirectForm(bbsMasterId, articleId);
    }

    @PostMapping("/comment/delete")
    public String deleteComment(@PathVariable String bbsMasterId,
            @RequestParam String articleId, @RequestParam String commentId,
            RedirectAttributes redirect) {
        boardCommentService.delete(commentId);
        redirect.addFlashAttribute("flashOk", "댓글을 삭제했습니다.");
        return redirectForm(bbsMasterId, articleId);
    }

    private String redirectForm(String bbsMasterId, String articleId) {
        return "redirect:/adm/board/" + bbsMasterId + "/article/form?articleId=" + articleId;
    }

    /** 첨부 그룹이 없는 글(첨부 0건)은 질의하지 않는다. */
    private java.util.List<com.gonet.primary.file.dto.FileItem> attachedFiles(
            BbsArticleAdmDto article) {
        return article.getFileGroupId() == null || article.getFileGroupId().isBlank()
                ? java.util.List.of() : fileService.findByGroup(article.getFileGroupId());
    }

    /** 게시판이 없으면 글 화면도 성립하지 않는다 — 빈 화면 대신 즉시 끊는다. */
    private BbsMasterAdmDto requireMaster(String bbsMasterId) {
        BbsMasterAdmDto master = boardMasterService.getAdm(bbsMasterId);
        if (master == null) {
            throw new IllegalArgumentException("게시판을 찾을 수 없습니다.");
        }
        return master;
    }

    private String redirectList(String bbsMasterId) {
        return "redirect:/adm/board/" + bbsMasterId + "/article";
    }
}
