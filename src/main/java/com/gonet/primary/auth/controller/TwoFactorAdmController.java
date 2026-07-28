package com.gonet.primary.auth.controller;

import com.gonet.common.crypto.Aes256Gcm;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.config.security.TotpService;
import com.gonet.primary.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 관리자 2단계 인증 등록 (/adm/2fa/setup).
 *
 * <p>시크릿은 <b>확정 전까지 세션에만</b> 둔다 — 등록을 끝내지 못한 시크릿이 계정에 남으면
 * 그 계정은 아무도 못 여는 상태가 된다. 앱에서 만든 코드가 실제로 맞는 것을 확인한 뒤에야
 * (검증 성공) 암호문으로 저장한다.
 *
 * <p>등록 후에는 세션을 무효화해 재로그인시킨다 — 이번 로그인 때 발급된 주체 스냅샷의
 * {@code twoFactorPending} 이 참이라, 갱신하지 않으면 등록 강제 필터에 계속 걸린다.
 */
@Controller
@RequiredArgsConstructor
public class TwoFactorAdmController {

    /** 세션 키 — 확정 전 시크릿(base32 평문, 이 요청 흐름 안에서만 존재) */
    private static final String PENDING_SECRET = "gopcms.totp.pendingSecret";

    private final TotpService totpService;
    private final AuthService authService;
    private final Aes256Gcm aes256Gcm;

    @GetMapping("/adm/2fa/setup")
    public String setupForm(@AuthenticationPrincipal LoginPrincipal principal,
            HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(true);
        String secret = (String) session.getAttribute(PENDING_SECRET);
        if (secret == null) {
            secret = totpService.newSecret();
            session.setAttribute(PENDING_SECRET, secret);
        }
        String uri = totpService.otpAuthUri(principal.loginId(), secret);
        model.addAttribute("secret", secret);          // 카메라를 못 쓰는 환경의 수동 입력용
        model.addAttribute("qrDataUri", totpService.qrDataUri(uri));
        return "adm/2fa-setup";
    }

    @PostMapping("/adm/2fa/setup")
    public String confirm(@AuthenticationPrincipal LoginPrincipal principal,
            @RequestParam String totpCode, HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        String secret = session == null ? null : (String) session.getAttribute(PENDING_SECRET);
        if (secret == null) {
            return "redirect:/adm/2fa/setup"; // 세션 만료 — 새 시크릿부터 다시
        }
        if (!totpService.verify(secret, totpCode)) {
            model.addAttribute("error", "인증코드가 일치하지 않습니다. 앱의 시각 동기화를 확인해 주세요.");
            model.addAttribute("secret", secret);
            model.addAttribute("qrDataUri",
                    totpService.qrDataUri(totpService.otpAuthUri(principal.loginId(), secret)));
            return "adm/2fa-setup";
        }

        authService.enableTwoFactor(principal.userId(), aes256Gcm.encrypt(secret));
        session.invalidate();
        SecurityContextHolder.clearContext();
        return "redirect:/adm/login?tfa";
    }
}
