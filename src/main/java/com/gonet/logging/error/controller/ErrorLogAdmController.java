package com.gonet.logging.error.controller;

import com.gonet.logging.error.dto.ErrorLog;
import com.gonet.logging.error.service.ErrorLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 에러 로그 조회 (/adm/error-log) — <b>조용히 삼킨 실패를 사람이 보는 자리</b>.
 *
 * <p>이 프로젝트는 부가 기록(접근·개인정보취급·파기·로그인 이력) 적재가 깨져도 본 업무를
 * 계속 진행한다. 그 판단의 대가로 "실패가 어딘가에 조용히 쌓이는" 위험이 생기는데,
 * 이 화면이 그 대가를 갚는다. 없으면 삼킨 실패는 서버 파일 로그에만 남고 아무도 안 본다.
 *
 * <p><b>읽기 전용이다.</b> 등록·수정·삭제가 없다 — 에러 기록은 고치지 않는다.
 * 오래된 기록의 정리는 화면이 아니라 보존기간 배치({@code LogRetentionWorker})가 맡는다.
 *
 * <p>URL 규칙은 새로 넣지 않는다 — {@code /adm/**} ROLE_ADMIN 규칙(priority 20)이
 * 이미 덮는다. 다만 {@code /adm/error-log/**} 는 그 규칙보다 앞서는 규칙이 없어야 한다
 * (conventions.md §7 — 무매칭 DENY).
 */
@Controller
@RequiredArgsConstructor
public class ErrorLogAdmController {

    /** 상단 요약이 훑는 기간 — 최근 상황만 본다. */
    private static final int SUMMARY_DAYS = 7;

    private final ErrorLogService errorLogService;

    @GetMapping("/adm/error-log")
    public String list(@ModelAttribute("cond") ErrorLog.Search cond, Model model) {
        model.addAttribute("page", errorLogService.getPage(cond));
        model.addAttribute("classCounts", errorLogService.getClassCounts(SUMMARY_DAYS));
        model.addAttribute("summaryDays", SUMMARY_DAYS);
        return "adm/error-log/list";
    }

    /**
     * 상세 — 스택트레이스는 여기서만 읽는다(목록은 mediumtext 를 싣지 않는다).
     *
     * <p>없는 ID 로 들어오면 404 대신 목록으로 되돌린다. 보존기간 배치가 지운 직후에
     * 북마크를 눌렀을 때가 대표적인데, 그건 오류가 아니라 정상이다.
     */
    @GetMapping("/adm/error-log/{logErrorId:\\d+}")
    public String detail(@PathVariable Long logErrorId, Model model) {
        ErrorLog row = errorLogService.get(logErrorId);
        if (row == null) {
            return "redirect:/adm/error-log";
        }
        model.addAttribute("row", row);
        return "adm/error-log/detail";
    }
}
