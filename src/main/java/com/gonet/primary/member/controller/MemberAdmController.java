package com.gonet.primary.member.controller;

import com.gonet.common.util.Mask;
import com.gonet.logging.privacy.service.PrivacyAccessLogService;
import com.gonet.logging.privacy.service.PrivacyAccessLogger;
import com.gonet.primary.member.dto.MemberAdmRow;
import com.gonet.primary.member.dto.MemberAdmSearch;
import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.oauth2.service.MemberOAuthService;
import com.gonet.primary.member.service.MemberAdmService;
import com.gonet.primary.member.service.MemberPasswordResetService;
import com.gonet.primary.site.service.SiteService;
import com.gonet.scheduler.MemberLifecycleJob;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * 관리자 회원 관리 — {@code /adm/member}.
 *
 * <pre>
 *   GET  /adm/member                   목록 (마스킹 기본)
 *   GET  /adm/member/export            CSV 내려받기 (사유 필수 + 건수 상한)
 *   GET  /adm/member/dormant           휴면 현황
 *   GET  /adm/member/withdraw          탈퇴 원장
 *   POST /adm/member/batch/{job}       생명주기 배치 수동 실행
 *   GET  /adm/member/{id}              상세 (마스킹)
 *   GET  /adm/member/{id}?reason=…     마스킹 해제 (사유 필수, 이력 적재)
 *   POST /adm/member/{id}/status       상태 변경
 *   POST /adm/member/{id}/unlock       잠금 해제
 *   POST /adm/member/{id}/password     임시 비밀번호 발급
 *   POST /adm/member/{id}/withdraw     강제 탈퇴
 * </pre>
 *
 * <p>회원 경로의 {@code {memberId}} 에 {@code MBR_} 접두어 제약을 건다. 없으면
 * {@code /adm/member/batch/withdraw}(배치)가 {@code /{memberId}/withdraw}(강제 탈퇴)에도
 * 매칭돼 배치 버튼이 400 을 뱉었다 — 실측으로 잡은 충돌이다. PK 규약(conventions §2)이
 * 접두어를 보장하므로 제약이 라우팅과 현실을 일치시킨다.
 *
 * <p><b>등록이 없다</b>. 가입은 본인 동의와 본인확인을 거쳐야 성립하는데 관리자가 대신
 * 만들면 그 둘이 없는 계정이 생긴다(정책 — PLAN §P10-6).
 *
 * <p><b>개인정보는 마스킹이 기본</b>이고, 원본을 보려면 사유를 적어야 한다. 사유는
 * {@code log_privacy_access} 에 남는다(개인정보보호법 §29 접속기록). 목록 조회조차
 * 이력으로 남는다 — 대량 열람은 그 자체가 소명 대상이다.
 */
@Slf4j
@Controller
@RequestMapping("/adm/member")
@RequiredArgsConstructor
public class MemberAdmController {

    private static final List<String> STATUSES =
            List.of("ACTIVE", "LOCKED", "EMAIL_PENDING", "SUSPENDED");
    private static final List<String> JOIN_TYPES =
            List.of("HOMEPAGE", "EMAIL", "MOBILE", "NAVER", "KAKAO", "GOOGLE", "APPLE");

    /** 상세에 보여 줄 최근 접근 이력 건수. */
    private static final int RECENT_ACCESS = 20;

    /** 마스킹 해제·내려받기 사유의 최소 길이 — "확인" 두 글자로는 소명이 되지 않는다. */
    private static final int MIN_REASON = 5;

    /** 개인정보 원본 열람 권한 — 계층 밖 독립 역할이라 ROLE_ADMIN 이 상속하지 않는다(V907). */
    private static final String ROLE_PRIVACY = "ROLE_PRIVACY";

    private final MemberAdmService memberAdmService;
    private final MemberPasswordResetService passwordResetService;
    private final MemberOAuthService memberOAuthService;
    private final PrivacyAccessLogger privacyLogger;
    private final PrivacyAccessLogService privacyLogService;
    private final MemberLifecycleJob lifecycleJob;
    private final SiteService siteService;

    /* ── 목록 ───────────────────────────────────────────────────────────── */

    @GetMapping
    public String list(@ModelAttribute("cond") MemberAdmSearch cond, HttpServletRequest request,
            Model model) {
        var page = memberAdmService.getPage(cond);
        // 목록 열람도 이력이다 — 한 계정이 매일 전 회원을 훑는 패턴은 건수로만 드러난다
        privacyLogger.search(request, page.content().size());

        model.addAttribute("page", page);
        model.addAttribute("statusCounts", memberAdmService.countByStatus(cond.getSiteId()));
        addCommon(model);
        return "adm/member/list";
    }

    /* ── 상세 ───────────────────────────────────────────────────────────── */

    /**
     * 상세. {@code reason} 이 오면 마스킹을 풀고 그 사유를 이력에 남긴다.
     *
     * <p>사유를 GET 파라미터로 받는 것이 어색해 보이지만, 마스킹 해제는 <b>화면 전환</b>이라
     * 링크로 도달할 수 있어야 한다(뒤로 가기·새로고침이 자연스럽게 동작해야 한다).
     * 대신 사유가 URL 에 남으므로 개인정보는 절대 사유에 적지 않도록 화면이 안내한다.
     */
    @GetMapping("/{memberId:MBR_.+}")
    public String detail(@PathVariable String memberId,
            @RequestParam(required = false) String reason,
            HttpServletRequest request, Model model, RedirectAttributes ra) {

        MemberDto member = memberAdmService.get(memberId);
        if (member == null) {
            ra.addFlashAttribute("flashMessage", "회원을 찾을 수 없습니다.");
            return "redirect:/adm/member";
        }

        boolean asked = reason != null && !reason.isBlank();
        boolean reveal = false;
        if (asked && !hasPrivacyRole()) {
            // ROLE_PRIVACY 를 URL 규칙으로 가를 수 없다 — 상세와 같은 주소이기 때문이다.
            // 그래서 여기서 직접 본다(V920 주석 참조). 막은 것도 이력에 남긴다.
            privacyLogger.denied(request, "DECRYPT", memberId, "ROLE_PRIVACY 없음");
            model.addAttribute("flashMessage",
                    "개인정보 원본 열람은 개인정보 관리자(ROLE_PRIVACY) 권한이 필요합니다.");
        } else if (asked && reason.trim().length() < MIN_REASON) {
            privacyLogger.denied(request, "DECRYPT", memberId, "사유 길이 미달");
            model.addAttribute("flashMessage",
                    "열람 사유를 %d자 이상 입력해 주세요.".formatted(MIN_REASON));
        } else if (asked) {
            reveal = true;
        }

        if (reveal) {
            privacyLogger.decrypt(request, memberId, reason.trim());
        } else {
            privacyLogger.read(request, memberId, PrivacyAccessLogger.FIELDS_BASIC);
        }

        model.addAttribute("member", member);
        model.addAttribute("reveal", reveal);
        model.addAttribute("view", new MemberView(member, reveal));
        model.addAttribute("oauthLinks", memberOAuthService.findLinks(memberId));
        model.addAttribute("accessLogs", privacyLogService.recentByTarget(
                PrivacyAccessLogger.ENTITY_MEMBER, memberId, RECENT_ACCESS));
        addCommon(model);
        return "adm/member/detail";
    }

    /* ── 처리 ───────────────────────────────────────────────────────────── */

    @PostMapping("/{memberId:MBR_.+}/status")
    public String changeStatus(@PathVariable String memberId, @RequestParam String status,
            @RequestParam(required = false) String reason,
            HttpServletRequest request, RedirectAttributes ra) {
        try {
            memberAdmService.changeStatus(memberId, status);
            privacyLogger.update(request, memberId, "상태 변경 → " + status
                    + (reason == null || reason.isBlank() ? "" : " / " + reason.trim()));
            ra.addFlashAttribute("flashMessage", "상태를 " + status + " 로 변경했습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashMessage", e.getMessage());
        }
        return "redirect:/adm/member/" + memberId;
    }

    @PostMapping("/{memberId:MBR_.+}/unlock")
    public String unlock(@PathVariable String memberId, HttpServletRequest request,
            RedirectAttributes ra) {
        try {
            memberAdmService.unlock(memberId);
            privacyLogger.update(request, memberId, "로그인 잠금 해제");
            ra.addFlashAttribute("flashMessage", "잠금을 해제했습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashMessage", e.getMessage());
        }
        return "redirect:/adm/member/" + memberId;
    }

    /**
     * 임시 비밀번호 발급 — 화면에는 보여 주지 않고 회원 메일로만 간다.
     *
     * <p>관리자에게 값을 보여 주면 관리자가 그 계정으로 로그인할 수 있다. 계정을 되찾아
     * 주는 것과 계정을 가져가는 것은 다른 일이다.
     */
    @PostMapping("/{memberId:MBR_.+}/password")
    public String resetPassword(@PathVariable String memberId, HttpServletRequest request,
            RedirectAttributes ra) {
        try {
            passwordResetService.issueTemporaryPasswordByAdmin(memberId);
            privacyLogger.update(request, memberId, "임시 비밀번호 발급");
            ra.addFlashAttribute("flashMessage",
                    "임시 비밀번호를 회원 이메일로 발송했습니다. (관리자에게는 표시되지 않습니다)");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashMessage", e.getMessage());
        }
        return "redirect:/adm/member/" + memberId;
    }

    /**
     * 강제 탈퇴. {@code reason} 을 <b>선택 파라미터로</b> 받는다 — 필수로 두면 값이 비었을 때
     * 스프링이 400 을 뱉어 사용자에게 안내 문구 대신 빈 오류 페이지가 뜬다. 사유 필수 판정은
     * 서비스가 하고, 그 메시지가 화면으로 돌아간다.
     */
    @PostMapping("/{memberId:MBR_.+}/withdraw")
    public String forceWithdraw(@PathVariable String memberId,
            @RequestParam(required = false) String reason,
            HttpServletRequest request, RedirectAttributes ra) {
        try {
            memberAdmService.forceWithdraw(memberId, reason);
            privacyLogger.delete(request, memberId, "강제 탈퇴 / " + reason.trim());
            ra.addFlashAttribute("flashMessage", "강제 탈퇴 처리했습니다.");
            return "redirect:/adm/member";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashMessage", e.getMessage());
            return "redirect:/adm/member/" + memberId;
        }
    }

    /* ── 현황 ───────────────────────────────────────────────────────────── */

    @GetMapping("/dormant")
    public String dormant(@ModelAttribute("cond") MemberAdmSearch cond, Model model) {
        model.addAttribute("page", memberAdmService.getDormantPage(cond));
        addCommon(model);
        return "adm/member/dormant";
    }

    @GetMapping("/withdraw")
    public String withdraw(@ModelAttribute("cond") MemberAdmSearch cond, Model model) {
        model.addAttribute("page", memberAdmService.getWithdrawPage(cond));
        addCommon(model);
        return "adm/member/withdraw";
    }

    /**
     * 생명주기 배치 수동 실행 — 운영 복구 경로.
     *
     * <p>스케줄이 멈췄거나 시각을 놓쳤을 때 손으로 돌린다. <b>dry-run 설정은 그대로
     * 적용된다</b> — 수동 실행이라고 해서 실제 처리로 바뀌지 않는다. 손으로 돌리는
     * 순간에만 진짜로 지워지는 동작은 사고를 부른다.
     */
    @PostMapping("/batch/{job}")
    public String runBatch(@PathVariable String job, RedirectAttributes ra) {
        try {
            switch (job) {
                case "notice" -> lifecycleJob.sendDormantNotices();
                case "dormant" -> lifecycleJob.transferToDormant();
                case "withdraw" -> lifecycleJob.transferToWithdraw();
                case "purge" -> lifecycleJob.purgeWithdrawn();
                default -> throw new IllegalArgumentException("알 수 없는 배치: " + job);
            }
            ra.addFlashAttribute("flashMessage",
                    job + " 배치를 실행했습니다. 처리 결과는 서버 로그를 확인해 주세요.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashMessage", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("회원 생명주기 배치 수동 실행 실패 job={}", job, e);
            ra.addFlashAttribute("flashMessage", "배치 실행 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/adm/member/dormant";
    }

    /* ── 내려받기 ───────────────────────────────────────────────────────── */

    /**
     * CSV 내려받기 — 사유 필수, 건수 상한, 마스킹 유지.
     *
     * <p>세 가지가 함께 있어야 의미가 있다. 사유만 받고 마스킹을 풀면 사유가 형식이 되고,
     * 마스킹만 하고 상한이 없으면 전 회원 목록이 한 번에 나간다.
     *
     * <p>파일에도 마스킹된 값이 실린다. 원본이 필요한 업무는 파일이 아니라 화면에서
     * 건별로 사유를 남기고 보는 경로를 쓴다 — 파일은 통제 밖으로 나가면 회수할 수 없다.
     */
    @GetMapping("/export")
    public void export(@ModelAttribute("cond") MemberAdmSearch cond,
            @RequestParam(required = false) String reason,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        if (reason == null || reason.trim().length() < MIN_REASON) {
            privacyLogger.denied(request, "EXPORT", null, "사유 미기재");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "내려받기 사유를 %d자 이상 입력해 주세요.".formatted(MIN_REASON));
            return;
        }

        List<MemberAdmRow> rows = memberAdmService.getForExport(cond);
        privacyLogger.export(request, rows.size(), reason.trim());

        String fileName = "members-%s.csv".formatted(
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"%s\"".formatted(fileName));

        try (OutputStream out = response.getOutputStream();
                Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            // BOM — 없으면 엑셀이 UTF-8 을 못 알아보고 한글이 깨진다
            writer.write('﻿');
            writer.write("사이트,아이디,닉네임,이름,이메일,휴대전화,상태,가입유형,실명인증,최근로그인,가입일\n");
            for (MemberAdmRow row : rows) {
                writer.write(String.join(",",
                        csv(row.getSiteCode()), csv(row.getLoginId()), csv(row.getNickname()),
                        csv(row.getMaskedName()), csv(row.getMaskedEmail()),
                        csv(row.getMaskedPhone()), csv(row.getStatus()), csv(row.getJoinType()),
                        row.isVerified() ? "Y" : "N",
                        csv(row.getLastLoginAt() == null ? null : row.getLastLoginAt().toString()),
                        csv(row.getCreatedAt() == null ? null : row.getCreatedAt().toString())));
                writer.write('\n');
            }
            if (rows.size() >= memberAdmService.exportLimit()) {
                // 잘린 것을 알리지 않으면 받은 파일이 전부라고 믿는다
                writer.write("# 상한 %d건에서 잘렸습니다. 조건을 좁혀 다시 받아 주세요.\n"
                        .formatted(memberAdmService.exportLimit()));
            }
        }
        log.info("회원 목록 내려받기 {}건 actor={}", rows.size(), request.getRemoteUser());
    }

    /* ── 내부 ───────────────────────────────────────────────────────────── */

    /**
     * 개인정보 원본을 볼 수 있는 권한인가.
     *
     * <p>{@code ROLE_PRIVACY} 는 ADMIN&gt;MANAGER&gt;… 계층 <b>밖</b>의 독립 역할이라
     * ROLE_ADMIN 이어도 자동 상속되지 않는다(V907). "관리자니까 다 볼 수 있다" 를 막는
     * 것이 이 역할의 존재 이유다.
     */
    private boolean hasPrivacyRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ROLE_PRIVACY.equals(a.getAuthority()));
    }

    private void addCommon(Model model) {
        model.addAttribute("statuses", STATUSES);
        model.addAttribute("joinTypes", JOIN_TYPES);
        model.addAttribute("sites", siteService.getAllForSelect());
    }

    /**
     * CSV 한 칸 — 쉼표·따옴표·줄바꿈을 감싼다.
     *
     * <p>이름에 쉼표가 있으면 칸이 밀리고, 값이 {@code =} 나 {@code +} 로 시작하면 엑셀이
     * <b>수식으로 실행</b>한다(CSV 인젝션). 앞에 작은따옴표를 붙여 글자로 고정한다.
     */
    private String csv(String value) {
        // Mask 가 값 없음을 "-" 로 돌려준다. 표에서는 그게 읽기 좋지만 CSV 에서는 빈 칸이
        // 맞고, 그대로 두면 아래 인젝션 방어가 "-" 를 수식 시작으로 보고 "'-" 로 만든다.
        if (value == null || value.isBlank() || "-".equals(value)) {
            return "";
        }
        String safe = value;
        if ("=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    /**
     * 상세 화면이 쓰는 표시값 묶음 — 마스킹 여부를 <b>한 곳에서</b> 가른다.
     *
     * <p>뷰에 {@code reveal ? member.email : mask(member.email)} 같은 삼항을 흩뿌리면
     * 새 필드를 추가할 때 하나를 빠뜨려도 화면상 티가 나지 않는다.
     */
    public record MemberView(String name, String email, String phone, String birthDate,
            String address, String addressDetail, String di, String parentName) {

        MemberView(MemberDto m, boolean reveal) {
            this(reveal ? nz(m.getMemberName()) : Mask.name(m.getMemberName()),
                    reveal ? nz(m.getEmail()) : Mask.email(m.getEmail()),
                    reveal ? nz(m.getPhone()) : Mask.phone(m.getPhone()),
                    reveal ? nz(m.getBirthDate()) : Mask.birthDate(m.getBirthDate()),
                    reveal ? nz(m.getAddress()) : Mask.address(m.getAddress()),
                    reveal ? nz(m.getAddressDetail()) : Mask.address(m.getAddressDetail()),
                    // DI 는 해제해도 보여 주지 않는다 — 전 기관 공통 식별자라
                    // 화면에 띄울 업무상 이유가 없다. 있고 없음만 알면 된다.
                    Mask.token(m.getDi()),
                    reveal ? nz(m.getParentName()) : Mask.name(m.getParentName()));
        }

        private static String nz(String value) {
            return value == null || value.isBlank() ? "-" : value;
        }
    }
}
