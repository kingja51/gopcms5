package com.gonet.primary.board.controller;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.board.dto.BbsCategoryAdmDto;
import com.gonet.primary.board.dto.BbsMasterAdmDto;
import com.gonet.primary.board.dto.BbsMasterSearch;
import com.gonet.primary.board.dto.BbsType;
import com.gonet.primary.board.dto.BoardAuth;
import com.gonet.primary.board.service.BoardCategoryService;
import com.gonet.primary.board.service.BoardMasterService;
import com.gonet.primary.file.dto.DownloadAuth;
import com.gonet.primary.site.service.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 게시판 관리 — 마스터 + 카테고리.
 *
 * <p>카테고리를 별도 화면으로 떼지 않고 마스터 폼 아래에 붙인다. 분류는 게시판 없이
 * 존재할 수 없고 수도 적어서, 오가며 관리하는 것보다 한 화면에서 보는 편이 낫다.
 */
@Controller
@RequestMapping("/adm/board")
@RequiredArgsConstructor
public class BoardAdmController {

    private static final String LIST = "redirect:/adm/board";

    private final BoardMasterService boardMasterService;
    private final BoardCategoryService boardCategoryService;
    private final SiteService siteService;

    @GetMapping
    public String list(@ModelAttribute("cond") BbsMasterSearch cond, Model model) {
        model.addAttribute("page", boardMasterService.getAdmPage(cond));
        model.addAttribute("sites", siteService.getAdmPage(new PageRequest()).content());
        model.addAttribute("types", BbsType.ALL);
        return "adm/board/list";
    }

    @GetMapping("/form")
    public String form(@RequestParam(required = false) String bbsMasterId, Model model) {
        BbsMasterAdmDto board = bbsMasterId == null
                ? newBoard() : boardMasterService.getAdm(bbsMasterId);
        if (board == null) {
            return LIST;
        }
        model.addAttribute("board", board);
        fillFormOptions(model, board);
        return "adm/board/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("board") BbsMasterAdmDto board,
            Model model, RedirectAttributes redirect) {
        try {
            boardMasterService.saveAdm(board);
        } catch (IllegalArgumentException e) {
            model.addAttribute("flashError", e.getMessage());
            fillFormOptions(model, board);
            return "adm/board/form";
        }
        redirect.addFlashAttribute("flashOk", "저장되었습니다.");
        return "redirect:/adm/board/form?bbsMasterId=" + board.getBbsMasterId();
    }

    @PostMapping("/delete")
    public String delete(@RequestParam String bbsMasterId, RedirectAttributes redirect) {
        try {
            boardMasterService.deleteAdm(bbsMasterId);
            redirect.addFlashAttribute("flashOk", "삭제되었습니다.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return LIST;
    }

    /* ── 카테고리 — 마스터 폼 안에서 관리 ─────────────────────────────── */

    @PostMapping("/category/save")
    public String saveCategory(@ModelAttribute BbsCategoryAdmDto category,
            RedirectAttributes redirect) {
        try {
            boardCategoryService.saveAdm(category);
            redirect.addFlashAttribute("flashOk", "분류를 저장했습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/adm/board/form?bbsMasterId=" + category.getBbsMasterId();
    }

    @PostMapping("/category/delete")
    public String deleteCategory(@RequestParam String categoryId,
            @RequestParam String bbsMasterId, RedirectAttributes redirect) {
        try {
            boardCategoryService.deleteAdm(categoryId);
            redirect.addFlashAttribute("flashOk", "분류를 삭제했습니다.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/adm/board/form?bbsMasterId=" + bbsMasterId;
    }

    /* ── 폼 부속 ──────────────────────────────────────────────────────── */

    private void fillFormOptions(Model model, BbsMasterAdmDto board) {
        model.addAttribute("sites", siteService.getAdmPage(new PageRequest()).content());
        model.addAttribute("types", BbsType.ALL);
        model.addAttribute("readAuths", BoardAuth.READ);
        model.addAttribute("writeAuths", BoardAuth.WRITE);
        model.addAttribute("downloadAuths", DownloadAuth.SELECTABLE);
        // 신규 등록 중이면 분류·통합 후보를 아직 붙일 수 없다(게시판이 없으므로)
        if (board.getBbsMasterId() != null && !board.getBbsMasterId().isBlank()) {
            model.addAttribute("categories",
                    boardCategoryService.getByBoard(board.getBbsMasterId()));
            model.addAttribute("groupCandidates",
                    boardMasterService.getGroupCandidates(board.getSiteId(), board.getBbsMasterId()));
        }
    }

    /** 새 게시판의 기본값 — 가장 흔한 구성으로 시작해 손댈 곳을 줄인다. */
    private BbsMasterAdmDto newBoard() {
        BbsMasterAdmDto board = new BbsMasterAdmDto();
        board.setBbsType(BbsType.NOTICE);
        board.setReadAuth("ALL");
        board.setWriteAuth("ADMIN");
        board.setDownloadAuth(DownloadAuth.ANONYMOUS);
        board.setCommentYn("N");
        board.setFileYn("Y");
        board.setFileCountMax(5);
        board.setFileSizeMaxMb(10);
        board.setNoticeTopYn("Y");
        board.setHtmlYn("N");
        board.setCaptchaYn("N");
        board.setAnonymousYn("N");
        board.setUseYn("Y");
        return board;
    }
}
