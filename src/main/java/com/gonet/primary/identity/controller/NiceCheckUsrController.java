package com.gonet.primary.identity.controller;

import com.gonet.common.web.SiteContextHolder;
import com.gonet.primary.identity.config.NiceCheckProperties;
import com.gonet.primary.identity.dto.NiceCheckResult;
import com.gonet.primary.identity.dto.NiceEncodeResult;
import com.gonet.primary.identity.service.NiceCheckService;
import com.gonet.primary.site.dto.SiteContext;
import jakarta.servlet.http.HttpSession;
import java.util.regex.Pattern;
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

    /**
     * 복귀 경로 허용 형태 — 슬래시 <b>하나</b>로 시작하고 곧바로 경로 문자가 오며,
     * 이후에도 소문자·숫자·하이픈·밑줄·슬래시만 나오는 경로.
     *
     * <p>두 번째 자리에 슬래시를 허용하지 않는 것이 핵심이다. 그 한 글자가
     * {@code //evil} 같은 프로토콜 상대 URL 을 만든다(점이 없어도 사내망에서는 해석된다).
     * {@code /\}·{@code %2f}·{@code :}·개행은 문자 집합에서 이미 탈락한다.
     *
     * <p>쿼리스트링도 허용하지 않는다 — 지금 필요한 복귀 주소는 전부 순수 경로이고,
     * 허용 범위를 넓히면 그만큼 검사가 어려워진다.
     */
    private static final Pattern SAFE_NEXT = Pattern.compile("/[a-z0-9_-][a-z0-9_/-]{0,199}");

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
     * 부모창 복귀 경로 정리 — <b>우리 사이트 안의 경로만</b> 받는다.
     *
     * <p>이 값은 팝업이 부모창을 보낼 주소가 되므로(`window.location.href`) 느슨하면
     * 그대로 오픈 리다이렉트다. 걸러야 하는 것이 {@code //evil.com} 하나가 아니다:
     * <ul>
     *   <li>{@code //evil.com} — 브라우저가 프로토콜 상대 URL 로 읽는다</li>
     *   <li>{@code /\evil.com} — 브라우저는 URL 의 백슬래시를 슬래시로 정규화하므로
     *       (WHATWG URL 표준) 위와 같은 결과가 된다. <b>코드 리뷰 2026-07-30 지적</b></li>
     *   <li>{@code /%2fevil.com}·개행 삽입 등 — 인코딩·제어문자로 같은 효과를 노린다</li>
     * </ul>
     *
     * <p>그래서 "무엇을 막을까" 대신 <b>"무엇만 허용할까"</b> 로 뒤집는다 —
     * 사용자 프로그램 경로에 쓰이는 문자만 있는 단일 슬래시 시작 경로여야 통과한다.
     * 값이 조건을 벗어나면 가입 폼(사이트 컨텍스트 기준)으로 되돌린다.
     */
    private String safeNext(String next) {
        if (next != null && SAFE_NEXT.matcher(next).matches()) {
            return next;
        }
        SiteContext site = SiteContextHolder.get();
        return site == null ? "/" : "/" + site.getSiteCode() + "/member/join/form";
    }
}
