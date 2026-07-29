package com.gonet.common.file.security;

import com.gonet.common.file.config.FileUploadProperties;
import com.gonet.common.file.dto.UploadCategory;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 방어 ① — 파일명·확장자 검사.
 *
 * <p>여기서 막는 것은 확장자만이 아니다. <b>파일명 자체가 공격 표면</b>이다:
 * <ul>
 *   <li>널바이트(<code>a.jpg\0.jsp</code>) — 하위 계층에서 문자열이 잘려 다른 확장자가 된다</li>
 *   <li>경로 요소(<code>../</code>, <code>/</code>, <code>\</code>) — 저장 경로를 벗어난다</li>
 *   <li>이중 확장자(<code>a.jsp.jpg</code>) — 서버 설정에 따라 앞 확장자로 실행될 수 있다</li>
 * </ul>
 *
 * <p>원본 파일명을 <b>저장 이름으로 쓰지 않는</b> 것이 가장 확실한 방어지만(그렇게 한다),
 * 표시용으로 DB 에 남기므로 여기서 한 번 걸러 둔다.
 */
@Component
@RequiredArgsConstructor
public class FileExtensionValidator {

    /** 확장자가 하나라도 이 목록에 걸리면 위치와 무관하게 거부한다(이중 확장자 대비). */
    private static final List<String> NEVER_ALLOWED = List.of(
            "jsp", "jspx", "jsw", "jsv", "jspf", "jhtml",
            "php", "php3", "php4", "php5", "phtml", "phar",
            "asp", "aspx", "ascx", "asa", "asax", "cer", "cdx",
            "exe", "dll", "com", "bat", "cmd", "msi", "scr", "pif",
            "sh", "bash", "csh", "ksh", "zsh", "cgi", "pl", "py", "rb",
            "jar", "war", "ear", "class", "vbs", "vbe", "js", "jse",
            "ws", "wsf", "wsh", "ps1", "psm1", "reg", "hta", "htaccess");

    private final FileUploadProperties properties;

    /**
     * @return 소문자 확장자 (점 없음)
     * @throws UploadValidationException 정책 위반
     */
    public String validate(String originalName, UploadCategory category) {
        if (originalName == null || originalName.isBlank()) {
            throw new UploadValidationException("파일명이 없습니다.");
        }
        if (originalName.indexOf('\0') >= 0) {
            throw new UploadValidationException("허용되지 않는 파일명입니다.");
        }
        if (originalName.contains("/") || originalName.contains("\\") || originalName.contains("..")) {
            throw new UploadValidationException("허용되지 않는 파일명입니다.");
        }
        if (originalName.length() > 255) {
            throw new UploadValidationException("파일명이 너무 깁니다. (255자 이내)");
        }

        String lower = originalName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            throw new UploadValidationException("확장자가 없는 파일은 올릴 수 없습니다.");
        }

        // 이중 확장자 — 마지막 확장자만 보지 않고 모든 마디를 검사한다
        for (String part : lower.split("\\.")) {
            if (NEVER_ALLOWED.contains(part)) {
                throw new UploadValidationException("실행 가능한 형식의 파일은 올릴 수 없습니다.");
            }
        }

        String extension = lower.substring(dot + 1);
        List<String> allowed = properties.extensionsOf(category.key());
        if (!allowed.contains(extension)) {
            throw new UploadValidationException(
                    "허용되지 않는 확장자입니다. (%s)".formatted(extension));
        }
        return extension;
    }
}
