package com.gonet.primary.file.controller;

import com.gonet.common.file.security.FileStorage;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.primary.file.dto.DownloadAuth;
import com.gonet.primary.file.dto.FileEntityType;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.dto.FileSearch;
import com.gonet.primary.file.dto.VirusScanStatus;
import com.gonet.logging.file.service.FileDownloadLogService;
import com.gonet.logging.file.service.FileDownloadLogger;
import com.gonet.primary.file.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 파일 관리 — 목록·상세·강제 다운로드·삭제 + 공통 첨부 폼 시연. */
@Controller
@RequestMapping("/adm/file")
@RequiredArgsConstructor
@Slf4j
public class FileAdmController {

    private static final List<String> SCAN_STATUSES = List.of(
            VirusScanStatus.PENDING, VirusScanStatus.CLEAN, VirusScanStatus.INFECTED,
            VirusScanStatus.ERROR, VirusScanStatus.QUARANTINED, VirusScanStatus.RESCANNING);

    private final FileService fileService;
    private final FileStorage storage;
    private final FileDownloadLogService downloadLogService;
    private final FileDownloadLogger downloadLogger;

    /** 상세 화면이 보여줄 최근 다운로드 건수 — 그 이상은 운영 로그 화면(P10-6)에서. */
    private static final int RECENT_DOWNLOADS = 20;

    @GetMapping
    public String list(@ModelAttribute("cond") FileSearch cond, Model model) {
        model.addAttribute("page", fileService.getAdmPage(cond));
        model.addAttribute("scanStatuses", SCAN_STATUSES);
        return "adm/file/list";
    }

    /** 상세 — 메타데이터·무결성 해시·소유 묶음 정책·최근 다운로드 기록. */
    @GetMapping("/{fileId}")
    public String detail(@PathVariable String fileId, Model model, RedirectAttributes ra) {
        FileItem file = fileService.findAnyById(fileId);
        if (file == null) {
            ra.addFlashAttribute("flashMessage", "파일을 찾을 수 없습니다.");
            return "redirect:/adm/file";
        }
        model.addAttribute("file", file);
        model.addAttribute("scanStatuses", SCAN_STATUSES);
        model.addAttribute("downloadAuths", DownloadAuth.SELECTABLE);
        model.addAttribute("recentDownloads",
                downloadLogService.recentByFile(fileId, RECENT_DOWNLOADS));
        model.addAttribute("downloadTotal", downloadLogService.countByFile(fileId));
        return "adm/file/detail";
    }

    /** 표시명·정렬 수정. 저장 경로·해시·MIME 은 방어 판정의 근거라 손대지 않는다. */
    @PostMapping("/{fileId}/save")
    public String save(@PathVariable String fileId,
            @RequestParam(value = "originalName", required = false) String originalName,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            RedirectAttributes ra) {
        try {
            fileService.updateAdm(fileId, originalName, sortOrder);
            ra.addFlashAttribute("flashMessage", "저장했습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashMessage", e.getMessage());
        }
        return "redirect:/adm/file/" + fileId;
    }

    /** 검사 상태 수동 변경 — 오탐 해제·재검사 요청. */
    @PostMapping("/{fileId}/scan-status")
    public String scanStatus(@PathVariable String fileId,
            @RequestParam("status") String status, RedirectAttributes ra) {
        try {
            fileService.updateScanStatusAdm(fileId, status);
            ra.addFlashAttribute("flashMessage", "검사 상태를 %s 로 바꿨습니다.".formatted(status));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashMessage", e.getMessage());
        }
        return "redirect:/adm/file/" + fileId;
    }

    /**
     * 공개 범위 변경 — <b>묶음 단위</b>다.
     *
     * <p>같은 글의 첨부 5개가 서로 다른 공개 범위를 갖는 상황은 정책 오류에 가깝다.
     * 그래서 파일이 아니라 묶음에 걸고, 화면에도 그렇게 안내한다.
     */
    @PostMapping("/{fileId}/download-auth")
    public String downloadAuth(@PathVariable String fileId,
            @RequestParam("fileGroupId") String fileGroupId,
            @RequestParam("downloadAuth") String downloadAuth, RedirectAttributes ra) {
        try {
            fileService.updateDownloadAuthAdm(fileGroupId, downloadAuth);
            ra.addFlashAttribute("flashMessage",
                    "이 묶음의 공개 범위를 %s 로 바꿨습니다.".formatted(downloadAuth));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashMessage", e.getMessage());
        }
        return "redirect:/adm/file/" + fileId;
    }

    /** 복구 — 정리 배치가 물리 삭제하기 전까지 되돌릴 수 있다. */
    @PostMapping("/{fileId}/restore")
    public String restore(@PathVariable String fileId, RedirectAttributes ra) {
        fileService.restoreAdm(fileId);
        ra.addFlashAttribute("flashMessage", "파일을 복구했습니다.");
        return "redirect:/adm/file/" + fileId;
    }

    /**
     * 공통 첨부 폼 시연·점검 화면.
     *
     * <p>진짜 목적은 시연이 아니라 <b>회귀 확인</b>이다. 게시판·컨텐츠가 붙기 전에도
     * 업로드 경로 전체(권한→방어→저장→목록→다운로드)를 한 화면에서 확인할 수 있어야
     * 나중에 도메인이 늘어도 무엇이 깨졌는지 빨리 좁힐 수 있다.
     */
    @GetMapping("/picker")
    public String picker(Model model) {
        // 실제 폼과 같은 방식 — 저장 전에 PK 를 미리 발급해 그룹의 주인을 정한다
        model.addAttribute("entityId", Uid.next(UidPrefix.CNT));
        model.addAttribute("entityTypes", FileEntityType.selectable());
        model.addAttribute("downloadAuths", DownloadAuth.SELECTABLE);
        return "adm/file/picker";
    }

    /** 시연 폼 저장 — picker 가 보낸 CSV 를 받아 그룹을 정리한다(실제 도메인과 같은 흐름). */
    @PostMapping("/picker")
    public String pickerSave(@RequestParam("entityType") String entityType,
            @RequestParam("entityId") String entityId,
            @RequestParam(value = "downloadAuth", required = false) String downloadAuth,
            @RequestParam(value = "attachments", required = false) String attachments,
            RedirectAttributes ra) {

        if (!FileEntityType.isValid(entityType)) {
            ra.addFlashAttribute("flashMessage", "알 수 없는 첨부 구분입니다.");
            return "redirect:/adm/file";
        }
        // 정책 확정은 <b>저장 시점</b>에 서버가 한다 — 업로드 요청은 정책을 바꾸지 못한다
        String groupId = fileService.ensureGroup(entityType, entityId, null, downloadAuth);
        List<String> keep = (attachments == null || attachments.isBlank())
                ? List.of() : List.of(attachments.split(","));
        int removed = fileService.syncAttachments(groupId, keep);
        ra.addFlashAttribute("flashMessage",
                "첨부 %d건 확정, %d건 정리했습니다.".formatted(keep.size(), removed));
        return "redirect:/adm/file";
    }

    @PostMapping("/{fileId}/delete")
    public String delete(@PathVariable String fileId, RedirectAttributes ra) {
        fileService.deleteAdm(fileId);
        ra.addFlashAttribute("flashMessage",
                "삭제 표시했습니다. 보존기간이 지나면 정리 배치가 실체를 지웁니다.");
        return "redirect:/adm/file";
    }

    /**
     * 관리자 강제 다운로드 — 검사 상태와 무관하게 원본을 확인한다.
     * INFECTED 파일을 열어 확인해야 하는 상황이 실제로 있고, 그 행위는 로그로 남는다.
     */
    @GetMapping("/{fileId}/download")
    public void adminDownload(@PathVariable String fileId,
            jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        FileItem item = fileService.openForAdmin(fileId);
        // 관리자 강제 다운로드는 검사 판정을 우회하므로 더더욱 기록이 남아야 한다
        downloadLogger.write(request, item.getFileId(), item.getFileGroupId(),
                item.getOriginalName(), item.getExtension(), item.getSizeBytes(),
                FileDownloadLogger.TYPE_ADMIN, FileDownloadLogger.RESULT_SUCCESS);
        Path path = storage.resolve(item.getStoredPath());
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"%s\"".formatted(item.getStoredName()));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        try (OutputStream out = response.getOutputStream()) {
            response.setContentLengthLong(Files.size(path));
            Files.copy(path, out);
        } catch (IOException e) {
            log.warn("관리자 다운로드 실패 file={}: {}", fileId, e.toString());
        }
    }
}
