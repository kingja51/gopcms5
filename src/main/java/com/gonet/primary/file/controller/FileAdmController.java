package com.gonet.primary.file.controller;

import com.gonet.common.file.security.FileStorage;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.primary.file.dto.DownloadAuth;
import com.gonet.primary.file.dto.FileEntityType;
import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.dto.FileSearch;
import com.gonet.primary.file.dto.VirusScanStatus;
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

    private final FileService fileService;
    private final FileStorage storage;

    @GetMapping
    public String list(@ModelAttribute("cond") FileSearch cond, Model model) {
        model.addAttribute("page", fileService.getAdmPage(cond));
        model.addAttribute("scanStatuses", List.of(
                VirusScanStatus.PENDING, VirusScanStatus.CLEAN, VirusScanStatus.INFECTED,
                VirusScanStatus.ERROR, VirusScanStatus.QUARANTINED, VirusScanStatus.RESCANNING));
        return "adm/file/list";
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
        ra.addFlashAttribute("flashMessage", "파일을 삭제했습니다.");
        return "redirect:/adm/file";
    }

    /**
     * 관리자 강제 다운로드 — 검사 상태와 무관하게 원본을 확인한다.
     * INFECTED 파일을 열어 확인해야 하는 상황이 실제로 있고, 그 행위는 로그로 남는다.
     */
    @GetMapping("/{fileId}/download")
    public void adminDownload(@PathVariable String fileId, HttpServletResponse response) {
        FileItem item = fileService.openForAdmin(fileId);
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
