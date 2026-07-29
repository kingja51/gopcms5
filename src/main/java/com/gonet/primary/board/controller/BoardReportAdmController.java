package com.gonet.primary.board.controller;

import com.gonet.primary.board.dto.BbsReportSearch;
import com.gonet.primary.board.dto.ReactionTarget;
import com.gonet.primary.board.dto.ReportReason;
import com.gonet.primary.board.service.BoardReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 신고 검토 큐 — 임계로 자동 숨김된 것까지 사람이 다시 판단하는 자리. */
@Controller
@RequestMapping("/adm/board-report")
@RequiredArgsConstructor
public class BoardReportAdmController {

    private static final String LIST = "redirect:/adm/board-report";

    private final BoardReportService boardReportService;

    @GetMapping
    public String list(@ModelAttribute("cond") BbsReportSearch cond, Model model) {
        model.addAttribute("page", boardReportService.getPage(cond));
        model.addAttribute("targetTypes", ReactionTarget.ALL);
        model.addAttribute("reasons", ReportReason.ALL);
        return "adm/board/report/list";
    }

    @PostMapping("/review")
    public String review(@RequestParam String reportId,
            @RequestParam String status,
            @RequestParam(required = false) String reviewNote,
            @RequestParam(required = false, defaultValue = "false") boolean hideTarget,
            RedirectAttributes redirect) {
        try {
            boardReportService.review(reportId, status, reviewNote, hideTarget);
            redirect.addFlashAttribute("flashOk",
                    "REJECTED".equals(status)
                            ? "기각 처리했습니다. 숨김 상태였다면 다시 노출됩니다."
                            : "처리했습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return LIST;
    }
}
