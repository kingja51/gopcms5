package com.gonet.logging.audit.controller;

import com.gonet.logging.audit.dto.AuditLog;
import com.gonet.logging.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 감사 로그 조회 (/adm/audit-log) — <b>누가 무엇을 바꿨는지 사람이 보는 자리</b>.
 *
 * <p><b>읽기 전용이다.</b> 등록·수정·삭제가 없다 — 감사 기록을 고칠 수 있으면 감사가
 * 아니다. 오래된 기록의 정리는 화면이 아니라 보존기간 배치가 맡는다
 * ({@code log_audit} 36개월, {@code LogRetentionWorker}).
 *
 * <p>이 화면 자체의 조회는 감사 로그에 남지 않는다 — 인터셉터가 GET 을 기록하지 않기
 * 때문이다. 조회까지 남기면 감사 로그가 자기 조회 기록으로 불어난다.
 *
 * <p>URL 규칙은 새로 넣지 않는다 — {@code /adm/**} ROLE_ADMIN 규칙(priority 20)이
 * 이미 덮는다(conventions.md §7).
 */
@Controller
@RequiredArgsConstructor
public class AuditLogAdmController {

    private final AuditLogService auditLogService;

    @GetMapping("/adm/audit-log")
    public String list(@ModelAttribute("cond") AuditLog.Search cond, Model model) {
        model.addAttribute("page", auditLogService.getPage(cond));
        return "adm/audit-log/list";
    }

    /**
     * 상세 — 변경 전/후 스냅샷을 읽는 자리(현재 값은 NULL이다,
     * {@link AuditLog#getBeforeJson()} 참조).
     *
     * <p>없는 ID 는 404 대신 목록으로 되돌린다 — 보존기간 배치가 지운 직후의 북마크
     * 접근이 대표적이고, 그건 오류가 아니다.
     */
    @GetMapping("/adm/audit-log/{logAuditId:\\d+}")
    public String detail(@PathVariable Long logAuditId, Model model) {
        AuditLog row = auditLogService.get(logAuditId);
        if (row == null) {
            return "redirect:/adm/audit-log";
        }
        model.addAttribute("row", row);
        return "adm/audit-log/detail";
    }
}
