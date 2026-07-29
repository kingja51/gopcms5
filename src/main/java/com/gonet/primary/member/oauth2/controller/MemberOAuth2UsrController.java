package com.gonet.primary.member.oauth2.controller;

import com.gonet.config.security.SocialLoginAuthenticator;
import com.gonet.primary.member.dto.JoinSession;
import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.oauth2.dto.ExternalProfile;
import com.gonet.primary.member.oauth2.dto.MemberOAuthDto;
import com.gonet.primary.member.oauth2.dto.OAuth2Provider;
import com.gonet.primary.member.oauth2.service.MemberOAuthService;
import com.gonet.primary.member.oauth2.service.OAuth2Exception;
import com.gonet.primary.member.oauth2.service.OAuth2Service;
import com.gonet.primary.site.dto.SiteContext;
import com.gonet.primary.site.service.SiteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 소셜 로그인 — {@code /member/oauth2/{provider}}.
 *
 * <pre>
 *   GET /member/oauth2/{provider}?siteCode=x   인가 시작 (state 발급 → provider 로 이동)
 *   GET /member/oauth2/{provider}/callback     provider 콜백
 *                                                · 연결된 회원 있음 → 즉시 로그인
 *                                                · 없음            → 가입 마법사로
 * </pre>
 *
 * <p><b>경로에 siteCode 가 없는 이유</b>는 본인인증과 같다 — 콜백 URL 은 provider 콘솔에
 * 등록한 문자열 하나뿐이라 사이트마다 경로가 갈릴 수 없다. 사이트는 시작할 때 받아
 * 세션에 적어 두고 콜백에서 되살린다.
 *
 * <p><b>state</b> 는 세션에 넣고 콜백에서 <b>1회 소비</b>한다. 이게 없으면 공격자가 만든
 * 인가 코드를 피해자 브라우저에 흘려 남의 소셜 계정을 피해자 계정에 붙일 수 있다.
 *
 * <p>신규 회원은 바로 만들지 않고 가입 마법사로 보낸다 — 약관 동의와 본인인증(NICE)은
 * 소셜로 왔다고 건너뛸 수 있는 절차가 아니다.
 */
@Slf4j
@Controller
@RequestMapping("/member/oauth2")
@RequiredArgsConstructor
public class MemberOAuth2UsrController {

    private static final String SESSION_STATE = "GOPCMS_OAUTH2_STATE";
    private static final String SESSION_SITE = "GOPCMS_OAUTH2_SITE";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OAuth2Service oauth2Service;
    private final MemberOAuthService memberOAuthService;
    private final SocialLoginAuthenticator authenticator;
    private final SiteService siteService;

    /* ── 1) 인가 시작 ───────────────────────────────────────────────────── */

    @GetMapping("/{provider}")
    public String start(@PathVariable("provider") String providerCode,
            @RequestParam(required = false) String siteCode, HttpServletRequest request) {

        OAuth2Provider provider = OAuth2Provider.fromCode(providerCode);
        if (provider == null || !oauth2Service.isConfigured(provider)) {
            log.info("소셜 로그인 시작 거부 provider={} (미설정)", providerCode);
            return loginRedirect(siteCode, "social");
        }
        SiteContext site = resolveSite(siteCode);
        if (site == null) {
            return loginRedirect(siteCode, "site");
        }

        String state = randomState();
        HttpSession session = request.getSession(true);
        session.setAttribute(stateKey(provider), state);
        // 콜백 URL 에는 siteCode 가 없다 — 어느 사이트에서 시작했는지는 세션만 안다
        session.setAttribute(SESSION_SITE, site.getSiteCode());

        try {
            return "redirect:" + oauth2Service.buildAuthorizeUrl(
                    provider, state, oauth2Service.callbackUrl(provider));
        } catch (OAuth2Exception e) {
            log.warn("소셜 로그인 시작 실패 provider={} reason={}", provider, e.getMessage());
            return loginRedirect(site.getSiteCode(), "social");
        }
    }

    /* ── 2) 콜백 ────────────────────────────────────────────────────────── */

    @GetMapping("/{provider}/callback")
    public String callback(@PathVariable("provider") String providerCode,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request, HttpServletResponse response) {

        HttpSession session = request.getSession(false);
        String siteCode = session == null ? null : (String) session.getAttribute(SESSION_SITE);

        OAuth2Provider provider = OAuth2Provider.fromCode(providerCode);
        if (provider == null) {
            return loginRedirect(siteCode, "social");
        }
        if (error != null && !error.isBlank()) {
            // 사용자가 provider 화면에서 취소한 경우가 대부분이다 — 조용히 되돌린다
            log.info("소셜 로그인 취소 provider={} error={}", provider, error);
            return loginRedirect(siteCode, "cancel");
        }
        if (isBlank(code) || isBlank(state) || session == null) {
            return loginRedirect(siteCode, "social");
        }

        // state 는 한 번만 쓴다 — 검증 성공 여부와 무관하게 먼저 지운다
        String expected = (String) session.getAttribute(stateKey(provider));
        session.removeAttribute(stateKey(provider));
        if (expected == null || !expected.equals(state)) {
            log.warn("소셜 로그인 state 불일치 provider={} — 콜백 거절", provider);
            return loginRedirect(siteCode, "social");
        }

        SiteContext site = resolveSite(siteCode);
        if (site == null) {
            return loginRedirect(siteCode, "site");
        }

        ExternalProfile profile;
        try {
            profile = oauth2Service.exchangeAndFetchProfile(
                    provider, code, oauth2Service.callbackUrl(provider));
        } catch (OAuth2Exception e) {
            // provider 응답 원문에는 우리 자격이 되비칠 수 있어 사용자에게는 일반 문구만
            log.warn("소셜 로그인 교환 실패 provider={} reason={}", provider, e.getMessage());
            return loginRedirect(site.getSiteCode(), "social");
        }

        MemberOAuthDto link = memberOAuthService.findLink(
                provider.name(), profile.getProviderUserId());
        if (link != null) {
            return loginLinked(request, response, site, link, provider);
        }
        return startJoin(request, site, profile);
    }

    /* ── 연결된 회원 — 즉시 로그인 ──────────────────────────────────────── */

    private String loginLinked(HttpServletRequest request, HttpServletResponse response,
            SiteContext site, MemberOAuthDto link, OAuth2Provider provider) {

        // findMember 는 delete_yn='N' 만 돌려준다 — null 이면 탈퇴했거나 휴면으로 옮겨진 것이다
        MemberDto member = memberOAuthService.findMember(link.getMemberId());
        if (member == null) {
            // 회원은 사라졌는데 연결만 남은 상태 — 연결을 내리고 새로 가입하게 한다
            log.warn("소셜 연결이 가리키는 회원 없음 oauth={} — 연결 해제", link.getMemberOauthId());
            memberOAuthService.unlink(link.getMemberOauthId());
            return loginRedirect(site.getSiteCode(), "social");
        }
        // 연결은 사이트를 묶지 않는다(uk_oauth_provider_user). 다른 사이트 회원이
        // 이 사이트 로그인 화면으로 들어온 경우라 거절해야 한다.
        if (!site.getSiteId().equals(member.getSiteId())) {
            log.info("소셜 로그인 사이트 불일치 member={} site={}",
                    member.getMemberId(), site.getSiteCode());
            return loginRedirect(site.getSiteCode(), "site");
        }

        if (!authenticator.login(request, response, site, member.getLoginId())) {
            return loginRedirect(site.getSiteCode(), "state");
        }
        memberOAuthService.recordLogin(link.getMemberOauthId());
        log.info("소셜 로그인 성공 provider={} member={}", provider, member.getMemberId());
        return "redirect:/" + site.getSiteCode() + "/index";
    }

    /* ── 미연결 — 가입 마법사로 ─────────────────────────────────────────── */

    /**
     * 외부 프로필을 마법사 상태에 심고 STEP 1 로 보낸다.
     *
     * <p>여기서 바로 회원을 만들지 않는다: 약관 동의와 본인인증은 소셜로 들어왔다고
     * 생략할 수 있는 절차가 아니다. 가입이 끝나는 시점에 연결이 생긴다.
     */
    private String startJoin(HttpServletRequest request, SiteContext site,
            ExternalProfile profile) {
        JoinSession join = new JoinSession();
        join.setSiteId(site.getSiteId());
        join.setSiteCode(site.getSiteCode());
        join.setOauthProvider(profile.getProvider().name());
        join.setOauthUserId(profile.getProviderUserId());
        join.setOauthEmail(profile.getEmail());
        join.setOauthName(profile.getName() != null ? profile.getName() : profile.getNickname());
        request.getSession(true).setAttribute(JoinSession.SESSION_KEY, join);

        log.info("소셜 신규 사용자 — 가입 절차로 provider={}", profile.getProvider());
        return "redirect:/" + site.getSiteCode() + "/member/join";
    }

    /* ── helpers ────────────────────────────────────────────────────────── */

    private SiteContext resolveSite(String siteCode) {
        return isBlank(siteCode)
                ? siteService.getDefaultSiteContext() : siteService.getSiteContext(siteCode);
    }

    /**
     * 로그인 화면으로 되돌린다. 실패 사유는 거친 분류만 넘긴다 — provider 원문 메시지를
     * 그대로 실으면 우리 설정 상태가 URL 에 드러난다.
     */
    private String loginRedirect(String siteCode, String reason) {
        String suffix = isBlank(siteCode) ? "" : "&siteCode=" + siteCode;
        return "redirect:/login?error=" + reason + suffix;
    }

    private String stateKey(OAuth2Provider provider) {
        return SESSION_STATE + ":" + provider.name();
    }

    private String randomState() {
        byte[] buffer = new byte[32];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
