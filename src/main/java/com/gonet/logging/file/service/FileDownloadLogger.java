package com.gonet.logging.file.service;

import com.gonet.common.web.ClientIpResolver;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.logging.error.service.ErrorLogger;
import com.gonet.logging.file.dto.FileDownloadLog;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 다운로드 이력 적재 — logging_db 전용 TxManager + {@code REQUIRES_NEW} 격리.
 *
 * <p><b>이력 적재가 다운로드를 막아서는 안 된다.</b> logging_db 가 잠깐 불안정하다고
 * 사용자가 파일을 못 받으면 손해가 더 크다. 그래서 ①별도 트랜잭션으로 떼어내고
 * ②호출부가 예외를 삼키게 한다(아래 {@code write} 가 스스로 삼킨다).
 *
 * <p>거부된 시도({@code DENIED})도 남긴다 — 한 계정이 남의 비공개 자료를 반복해서
 * 두드리는 것은 그 자체로 신호이며, 성공만 기록하면 그 패턴이 보이지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileDownloadLogger {

    /*
     * 값은 V2 마이그레이션의 CHECK 제약(chk_logfiledl_type · chk_logfiledl_result)과
     * 1:1 이어야 한다. 임의 문자열을 쓰면 적재가 조용히 실패한다 —
     * 실측: 'DENIED' 를 넣었다가 제약 위반으로 기록이 통째로 사라졌다(다운로드는 계속됐다).
     */
    public static final String TYPE_SINGLE = "SINGLE";
    public static final String TYPE_GROUP_ZIP = "GROUP_ZIP";
    public static final String TYPE_ADMIN = "ADMIN";
    public static final String RESULT_SUCCESS = "SUCCESS";
    /** 권한 미달로 막힌 시도 — DDL 의 허용값은 BLOCKED 다(DENIED 아님). */
    public static final String RESULT_BLOCKED = "BLOCKED";
    /** 전송 중 오류 등 실패. */
    public static final String RESULT_FAIL = "FAIL";

    private final FileDownloadLogService logService;
    private final ClientIpResolver clientIpResolver;
    private final ErrorLogger errorLogger;

    /** 적재 실패는 삼킨다 — 부가 기록 때문에 본 기능이 무너지면 안 된다. */
    public void write(HttpServletRequest request, String fileId, String fileGroupId,
            String originalName, String extension, Long sizeBytes,
            String downloadType, String result) {
        try {
            FileDownloadLog row = new FileDownloadLog();
            row.setFileId(fileId);
            row.setFileGroupId(fileGroupId);
            row.setDownloadType(downloadType);
            row.setOriginalName(originalName);
            row.setExtension(extension);
            row.setSizeBytes(sizeBytes);
            row.setResult(result);
            if (request != null) {
                row.setRequestUri(request.getRequestURI());
                row.setClientIp(clientIpResolver.resolve(request));
                row.setUserAgent(trim(request.getHeader("User-Agent"), 500));
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof LoginPrincipal p) {
                row.setActorUserId(p.userId());
                row.setActorUserType(p.userType());
                row.setActorLoginId(p.loginId());
            }
            logService.insert(row);   // 별도 빈 — 자기호출이면 REQUIRES_NEW 가 무시된다
        } catch (RuntimeException e) {
            log.warn("다운로드 이력 적재 실패(다운로드는 계속) file={}: {}", fileId, e.toString());
            // CHECK 제약 위반으로 기록이 조용히 사라지던 실측 사례가 여기에 걸린다
            errorLogger.logRecordFailure("FILE_DOWNLOAD_LOG",
                    "file=%s type=%s result=%s".formatted(fileId, downloadType, result), e);
        }
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
