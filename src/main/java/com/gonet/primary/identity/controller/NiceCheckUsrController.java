package com.gonet.primary.identity.controller;

import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.identity.config.NiceCheckProperties;
import com.gonet.primary.identity.dto.NiceCheckResult;
import com.gonet.primary.identity.dto.NiceEncodeResult;
import com.gonet.primary.identity.service.NiceCheckService;
import com.gonet.primary.site.dto.SiteContext;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * NICE CheckPlus 본인인증 — {@code /member/identity/nice}.
 *
 * <p><b>경로에 siteCode 가 없는 이유</b>: 콜백 URL 은 NICE 콘솔에 <b>문자열 그대로</b>
 * 등록되고 그 URL 로만 응답이 돌아온다. 사이트마다 경로가 갈리면 사이트 수만큼 계약을
 * 따로 맺어야 한다. 그래서 {@code /login} 과 같은 사이트 무관 엔드포인트로 두고,
 * 사이트는 {@code siteCode} 파라미터(SiteResolveFilter)와 세션으로 잇는다.
 *
 * <p>흐름 — 이 컨트롤러의 페이지는 <b>전부 팝업 안</b>에서 열린다:
 * <ol>
 *   <li>부모창(가입 STEP3)이 {@code window.open('/member/identity/nice?...')}</li>
 *   <li>GET {@code /} — 요청번호 발급 + 암호화 → NICE 로 자동 POST 하는 폼</li>
 *   <li>NICE 인증 후 {@code /success} 또는 {@code /fail} 로 콜백(우리 origin)</li>
 *   <li>결과 페이지가 부모창에 신호를 보내고 스스로 닫는다</li>
 * </ol>
 *
 * <p>콜백은 외부 도메인이 보내므로 <b>CSRF 예외 + URL 접근 규칙 PERMIT_ALL</b> 이 함께
 * 있어야 동작한다(V11/V919). 대신 세션의 요청번호와 콜백의 {@code REQ_SEQ} 를 대조해
 * 남이 만든 EncodeData 를 밀어 넣지 못하게 막는다.
 *
 * <p>DI 는 강한 PII 라 모델·URL·로그 어디에도 싣지 않고 세션에만 둔다. CI 는 아예
 * 복호화하지 않는다(서비스 주석 참조).
 */
@Slf4j
@Controller
@RequestMapping("/member/identity/nice")
@RequiredArgsConstructor
public class NiceCheckUsrController {

    /** 세션에 저장하는 요청번호 — 콜백 변조 검증의 단일 기준. */
    public static final String SESSION_REQ_SEQ = "GOPCMS_NICE_REQ_SEQ";

    /** 인증 결과 — 가입 폼이 한 번 소비한다. */
    public static final String SESSION_RESULT = "GOPCMS_NICE_RESULT";

    /** 인증 용도 — SELF(본인) / PARENT(법정대리인). 결과를 어느 칸에 넣을지 가른다. */
    public static final String SESSION_PURPOSE = "GOPCMS_NICE_PURPOSE";

    /** 인증 완료 후 부모창이 이동할 URL — 팝업이 부모에게 건네는 값. */
    public static final String SESSION_NEXT_URL = "GOPCMS_NICE_NEXT_URL";

    public static final String PURPOSE_SELF = "SELF";
    public static final String PURPOSE_PARENT = "PARENT";

    private final NiceCheckService niceCheckService;
    private final NiceCheckProperties props;

    /**
     * 인증 시작 — 팝업이 여는 첫 페이지. NICE 로 자동 POST 하는 폼을 렌더한다.
     *
     * @param purpose SELF(본인) 또는 PARENT(14세 미만 법정대리인)
     * @param next    인증 후 부모창이 갈 곳. 외부 URL 오픈 리다이렉트를 막으려 경로만 받는다.
     */
    @GetMapping
    public String launch(@RequestParam(defaultValue = PURPOSE_SELF) String purpose,
            @RequestParam(required = false) String next,
            HttpSession session, Model model) {

        String resolved = PURPOSE_PARENT.equals(purpose) ? PURPOSE_PARENT : PURPOSE_SELF;
        session.setAttribute(SESSION_PURPOSE, resolved);
        session.setAttribute(SESSION_NEXT_URL, safeNext(next));

        String reqSeq = niceCheckService.generateRequestNo();
        session.setAttribute(SESSION_REQ_SEQ, reqSeq);

        NiceEncodeResult encoded = niceCheckService.encode(reqSeq);
        model.addAttribute("encData", encoded.getEncData());
        model.addAttribute("message", encoded.getMessage());
        model.addAttribute("popupUrl", props.getPopupUrl());
        model.addAttribute("purposeLabel",
                PURPOSE_PARENT.equals(resolved) ? "법정대리인 본인인증" : "본인인증");
        return "front/identity/nice-launch";
    }

    /**
     * 성공 콜백 — NICE 는 인증 수단에 따라 GET/POST 를 섞어 보낸다(둘 다 받는다).
     *
     * <p>세션의 요청번호와 결과의 {@code REQ_SEQ} 가 다르면 거절한다. 이 URL 은 공개돼
     * 있어서 이 대조가 없으면 아무 EncodeData 나 밀어 넣을 수 있다.
     */
    @RequestMapping(value = "/success", method = {RequestMethod.GET, RequestMethod.POST})
    public String success(@RequestParam(value = "EncodeData", required = false) String encodeData,
            HttpSession session, Model model) {
        NiceCheckResult result = niceCheckService.decode(encodeData);

        if (result.isSuccess()) {
            String expected = (String) session.getAttribute(SESSION_REQ_SEQ);
            if (expected == null || !expected.equals(result.getReqSeq())) {
                log.warn("NICE 콜백 요청번호 불일치 — 거절");
                return fail(session, model,
                        NiceCheckResult.fail(-99, "세션이 일치하지 않습니다. 처음부터 다시 시도해 주세요."));
            }
            session.removeAttribute(SESSION_REQ_SEQ);
            // 결과는 세션에만 — 모델에 실으면 화면 소스에 DI 가 그대로 남는다
            session.setAttribute(SESSION_RESULT, result);
            log.info("NICE 본인인증 성공 purpose={} authType={} nationalInfo={}",
                    session.getAttribute(SESSION_PURPOSE),
                    result.getAuthType(), result.getNationalInfo());

            model.addAttribute("success", true);
            model.addAttribute("nextUrl", session.getAttribute(SESSION_NEXT_URL));
            return "front/identity/nice-result";
        }
        return fail(session, model, result);
    }

    @RequestMapping(value = "/fail", method = {RequestMethod.GET, RequestMethod.POST})
    public String failCallback(
            @RequestParam(value = "EncodeData", required = false) String encodeData,
            HttpSession session, Model model) {
        return fail(session, model, niceCheckService.decode(encodeData));
    }

    private String fail(HttpSession session, Model model, NiceCheckResult result) {
        // 실패한 요청번호는 즉시 버린다 — 재사용되면 대조가 무의미해진다
        session.removeAttribute(SESSION_REQ_SEQ);
        log.info("NICE 본인인증 실패 rc={}", result.getReturnCode());
        model.addAttribute("success", false);
        model.addAttribute("result", result);
        return "front/identity/nice-result";
    }

    /**
     * 부모창 복귀 경로 정리 — {@code /} 로 시작하고 {@code //} 가 아닌 값만 받는다.
     *
     * <p>{@code //evil.com} 은 브라우저가 프로토콜 상대 URL 로 읽어 외부로 나간다.
     * 값이 수상하면 가입 폼(사이트 컨텍스트 기준)으로 되돌린다.
     */
    private String safeNext(String next) {
        if (next != null && next.startsWith("/") && !next.startsWith("//")) {
            return next;
        }
        SiteContext site = SiteContextHolder.get();
        return site == null ? "/" : "/" + site.getSiteCode() + "/member/join/form";
    }
}
