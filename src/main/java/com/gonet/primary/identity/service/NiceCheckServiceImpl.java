package com.gonet.primary.identity.service;

import com.gonet.primary.identity.config.NiceCheckProperties;
import com.gonet.primary.identity.dto.NiceCheckResult;
import com.gonet.primary.identity.dto.NiceEncodeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.gonet.common.service.AbstractCmsService;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link NiceCheckService} 구현 — NiceID.jar({@code NiceID.Check.CPClient}) 호출 래퍼.
 *
 * <p>본 구현은 JSP 샘플을 그대로 옮긴 평문 직렬화 포맷을 따른다:
 * {@code 7:REQ_SEQ<len>:<value>8:SITECODE<len>:<value>...}.
 * 각 필드의 length 는 UTF-8 byte 길이 (Spring Boot 기본 인코딩과 일치).
 *
 * <p>NICE SDK 가 던지는 반환 코드는 한국어 메시지로 변환해 사용자에게 노출.
 *
 * <p>이식 메모(gopcms2026 → gopcms5): eGov 규약상 서비스는 {@code AbstractCmsService} 를
 * 거쳐 {@code EgovAbstractServiceImpl} 를 <b>간접</b> 상속한다(CLAUDE.md).
 * 사이트 자격이 비어 있으면 암호화 단계에서 안내 문구를 돌려주고 기동은 막지 않는다 —
 * 계약 전에도 나머지 기능이 떠야 하기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NiceCheckServiceImpl extends AbstractCmsService implements NiceCheckService {

    private final NiceCheckProperties props;

    @Override
    public String generateRequestNo() {
        return new NiceID.Check.CPClient().getRequestNO(props.getSiteCode());
    }

    @Override
    public NiceEncodeResult encode(String reqSeq) {
        if (isBlank(props.getSiteCode()) || isBlank(props.getSitePassword())) {
            log.warn("NICE_ENCODE_SKIP reason=site-code or site-password is empty");
            return NiceEncodeResult.fail("NICE 사이트 자격이 설정되지 않았습니다. 관리자에게 문의하세요.");
        }

        var niceCheck = new NiceID.Check.CPClient();
        String plain = buildPlainData(
                reqSeq,
                props.getSiteCode(),
                nullToEmpty(props.getAuthType()),
                props.getReturnUrl(),
                props.getErrorUrl(),
                nullToEmpty(props.getCustomize()));

        int rc = niceCheck.fnEncode(props.getSiteCode(), props.getSitePassword(), plain);
        if (rc == 0) {
            return NiceEncodeResult.ok(niceCheck.getCipherData());
        }
        String msg = switch (rc) {
            case -1 -> "암호화 시스템 오류입니다.";
            case -2 -> "암호화 처리 오류입니다.";
            case -3 -> "암호화 데이터 오류입니다.";
            case -9 -> "입력 데이터 오류입니다.";
            default -> "알 수 없는 오류입니다. (코드: " + rc + ")";
        };
        log.warn("NICE_ENCODE_FAIL rc={} msg={}", rc, msg);
        return NiceEncodeResult.fail(msg);
    }

    @Override
    public NiceCheckResult decode(String encodeData) {
        if (isBlank(encodeData)) {
            return NiceCheckResult.fail(-9, "EncodeData 가 비어 있습니다.");
        }
        var niceCheck = new NiceID.Check.CPClient();
        int rc = niceCheck.fnDecode(props.getSiteCode(), props.getSitePassword(), encodeData);

        if (rc != 0) {
            String msg = switch (rc) {
                case -1  -> "복호화 시스템 오류입니다.";
                case -4  -> "복호화 처리 오류입니다.";
                case -5  -> "복호화 해쉬 오류입니다.";
                case -6  -> "복호화 데이터 오류입니다.";
                case -9  -> "입력 데이터 오류입니다.";
                case -12 -> "사이트 패스워드 오류입니다.";
                default  -> "알 수 없는 오류입니다. (코드: " + rc + ")";
            };
            log.warn("NICE_DECODE_FAIL rc={} msg={}", rc, msg);
            return NiceCheckResult.fail(rc, msg);
        }

        @SuppressWarnings("unchecked")
        Map<String, String> m = (HashMap<String, String>) niceCheck.fnParse(niceCheck.getPlainData());

        // 정책: 본 시스템은 DI(64byte) 만 회원 식별 키로 사용. CI(88byte) 는 주민등록번호 수준의
        // 강한 PII 로 취급하여 의도적으로 메모리에 적재하지 않는다. NICE 콘솔에서 CI 응답이
        // 활성화돼 있어도 본 메서드는 m.get("CI") 를 읽지 않으므로 무관.
        log.info("===NICE_DECODE_PARSED keys={} hasDI={} diLen={} reqSeq={} authType={}",
                m.keySet(),
                m.get("DI") != null,
                m.get("DI") == null ? 0 : m.get("DI").length(),
                m.get("REQ_SEQ"),
                m.get("AUTH_TYPE"));

        // NICE 는 UTF8_NAME 을 URL-encoded UTF-8 문자열로 반환 (예: %EC%98%A4%EC%9D%BC%ED%83%9D = "오일택").
        // NAME 은 EUC-KR raw 바이트 — UTF-8 환경에서는 깨지므로 UTF8_NAME 을 디코딩해 사용.
        String name = urlDecodeUtf8(m.get("UTF8_NAME"));
        if (isBlank(name)) name = m.get("NAME");

        return NiceCheckResult.builder()
                .success(true)
                .returnCode(0)
                .reqSeq(m.get("REQ_SEQ"))
                .resSeq(m.get("RES_SEQ"))
                .authType(m.get("AUTH_TYPE"))
                .name(name)
                .birthDate(m.get("BIRTHDATE"))
                .gender(m.get("GENDER"))
                .nationalInfo(m.get("NATIONALINFO"))
                .di(m.get("DI"))
                // .ci(m.get("CI"))  ← 정책상 CI 는 사용하지 않음 (PII 강도 → 적재 금지)
                .mobileNo(m.get("MOBILE_NO"))
                .mobileCo(m.get("MOBILE_CO"))
                .cipherDateTime(niceCheck.getCipherDateTime())
                .build();
    }

    /**
     * NICE 평문 직렬화 — {@code <len-keyword>:KEY<byte-len>:VALUE} 가 한 필드.
     * KEY 문자열의 length 는 키워드 그대로(REQ_SEQ=7, SITECODE=8 등)이며, VALUE 의
     * length 는 UTF-8 byte 수.
     */
    private static String buildPlainData(String reqSeq, String siteCode, String authType,
                                          String returnUrl, String errorUrl, String customize) {
        return "7:REQ_SEQ"   + utf8Len(reqSeq)    + ":" + reqSeq +
               "8:SITECODE"  + utf8Len(siteCode)  + ":" + siteCode +
               "9:AUTH_TYPE" + utf8Len(authType)  + ":" + authType +
               "7:RTN_URL"   + utf8Len(returnUrl) + ":" + returnUrl +
               "7:ERR_URL"   + utf8Len(errorUrl)  + ":" + errorUrl +
               "9:CUSTOMIZE" + utf8Len(customize) + ":" + customize;
    }

    private static int utf8Len(String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }

    /** NICE UTF8_NAME 등 URL-encoded UTF-8 필드 디코딩. 디코딩 실패 시 원본 반환. */
    private static String urlDecodeUtf8(String s) {
        if (isBlank(s)) return s;
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            log.warn("NICE_UTF8_DECODE_FAIL value={} reason={}", s, ex.getMessage());
            return s;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
