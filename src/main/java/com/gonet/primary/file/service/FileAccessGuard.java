package com.gonet.primary.file.service;

import com.gonet.common.util.Csv;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.primary.file.dto.DownloadAuth;
import com.gonet.primary.file.dto.FileGroup;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 업로드·다운로드 권한 판정 — <b>단일 진입점</b>.
 *
 * <p>권한 판단이 여러 곳에 흩어지면 그중 하나만 느슨해도 전체가 뚫린다. 컨트롤러가 아니라
 * 여기서만 판정하고, 컨트롤러는 결과를 따른다.
 *
 * <h3>업로드 = ROLE_REAL 이상</h3>
 * 전개된 역할 집합에 {@code ROLE_REAL} 이 있으면 통과한다. 계층상 ADMIN·MANAGER·STAFF·
 * MEMBER 는 모두 ROLE_REAL 을 포함하므로 자동으로 열리고, <b>단독 역할인 ROLE_PRIVACY 만
 * 가진 계정은 업로드할 수 없다</b> — 계층 단절의 의도된 결과다.
 *
 * <h3>다운로드 = tb_file_group.download_auth</h3>
 * 등록 시점에 정해진 그룹 정책이 유일한 근거다. 그중 {@code OWNER_PRIVACY} 만 성격이
 * 다르다 — 역할이 아니라 <b>관계</b>(작성자 본인)를 묻기 때문에 별도 경로로 판정한다.
 */
@Component
@Slf4j
public class FileAccessGuard {

    /** 업로드 최소 역할 — 실명인증 이전 계정의 업로드를 원천 차단한다. */
    public static final String UPLOAD_MIN_ROLE = "ROLE_REAL";

    /**
     * 개인정보 관리자 역할 ID. 계층이 끊겨 있어(parent NULL) 상속으로는 얻을 수 없고,
     * 명시적으로 부여받은 계정만 CSV 에 이 값을 갖는다.
     */
    @Value("${gopcms.security.role-privacy-id:ROL_01985a10-0000-7000-8000-000000001008}")
    private String rolePrivacyId;

    /** 로그인 주체 — 없으면 null. */
    public LoginPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getPrincipal() instanceof LoginPrincipal p ? p : null;
    }

    /**
     * 업로드 권한 검사.
     *
     * @throws InsufficientAuthenticationException 비로그인
     * @throws AccessDeniedException               ROLE_REAL 미만
     */
    public LoginPrincipal requireUploadPermission() {
        LoginPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw new InsufficientAuthenticationException("로그인이 필요합니다.");
        }
        if (!hasRole(principal, UPLOAD_MIN_ROLE)) {
            throw new AccessDeniedException("실명인증 회원 이상만 파일을 올릴 수 있습니다.");
        }
        return principal;
    }

    /** 에디터 이미지 업로드는 더 좁다 — 본문에 자산을 심는 일이라 실무 담당자 이상. */
    public LoginPrincipal requireEditorUploadPermission() {
        LoginPrincipal principal = requireUploadPermission();
        if (!hasRole(principal, "ROLE_STAFF")) {
            throw new AccessDeniedException("담당자 이상만 본문 이미지를 올릴 수 있습니다.");
        }
        return principal;
    }

    /**
     * 다운로드 권한 검사.
     *
     * @param ownerUserId OWNER_PRIVACY 판정에 쓰는 소유자 ID — 그 정책이 아니면 null 이어도 된다
     */
    public void enforceDownload(FileGroup group, String ownerUserId) {
        if (group == null) {
            throw new AccessDeniedException("파일 정보를 찾을 수 없습니다.");
        }
        String policy = group.getDownloadAuth();
        if (policy == null || policy.isBlank() || DownloadAuth.ANONYMOUS.equals(policy)) {
            return;                                        // 공개 자료
        }

        LoginPrincipal principal = currentPrincipal();
        if (principal == null) {
            // 비로그인은 401 로 — 로그인하면 열릴 수도 있는 상태와 영구 거부를 구분한다
            throw new InsufficientAuthenticationException("로그인이 필요합니다.");
        }

        if (DownloadAuth.OWNER_PRIVACY.equals(policy)) {
            enforceOwnerPrivacy(principal, ownerUserId, group.getFileGroupId());
            return;
        }
        if (DownloadAuth.ROLE_MEMBER.equals(policy)) {
            return;                                        // 인증되었으면 통과
        }
        if (hasRole(principal, policy) || hasRole(principal, "ROLE_ADMIN")) {
            return;
        }
        throw new AccessDeniedException("이 파일을 내려받을 권한이 없습니다.");
    }

    /**
     * OWNER_PRIVACY — 민원글처럼 <b>본인만</b> 열람해야 하는 첨부.
     *
     * <p>여기서 {@code ROLE_ADMIN} 은 자동 통과하지 <b>않는다</b>. 그것이 이 정책의 존재
     * 이유다 — 운영 관리자라도 남의 개인정보 첨부를 열 수 없어야 하고, 필요하면
     * {@code ROLE_PRIVACY} 를 명시적으로 부여받아야 한다(부여 사실이 기록으로 남는다).
     */
    private void enforceOwnerPrivacy(LoginPrincipal principal, String ownerUserId, String groupId) {
        Set<String> roleIds = Csv.toSet(principal.roleIds());
        if (roleIds.contains(rolePrivacyId)) {
            log.info("개인정보 첨부 열람 — 사유=ROLE_PRIVACY actor={} group={}",
                    principal.userId(), groupId);
            return;
        }
        // created_by 가 시드·시스템 값이면 '본인' 으로 인정하지 않는다 —
        // 그런 값이 다수 행에 반복되면 한 계정이 남의 자료를 모두 여는 사고가 된다
        if (ownerUserId != null && !ownerUserId.isBlank()
                && !isSentinel(ownerUserId)
                && ownerUserId.equals(principal.userId())) {
            log.info("개인정보 첨부 열람 — 사유=OWNER actor={} group={}",
                    principal.userId(), groupId);
            return;
        }
        throw new AccessDeniedException("본인 또는 개인정보 관리자만 내려받을 수 있습니다.");
    }

    private boolean isSentinel(String userId) {
        String upper = userId.toUpperCase();
        return "ALL".equals(upper) || "ANONYMOUS".equals(upper)
                || "SYSTEM".equals(upper) || "GUEST".equals(upper);
    }

    /** 현재 주체가 해당 역할을 가졌는지 — 서비스 계층에서도 쓴다. */
    public boolean hasRole(String roleCode) {
        return hasRole(null, roleCode);
    }

    /** 전개된 역할 CSV 에 role_code 가 있는지 — 스냅샷이라 역할 변경은 재로그인 후 반영된다. */
    private boolean hasRole(LoginPrincipal principal, String roleCode) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            for (var granted : auth.getAuthorities()) {
                if (roleCode.equals(granted.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }
}
