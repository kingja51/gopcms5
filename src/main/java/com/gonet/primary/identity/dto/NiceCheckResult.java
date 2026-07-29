package com.gonet.primary.identity.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * NICE CheckPlus 안심본인인증 콜백 복호화 결과.
 *
 * <p>{@code success=true} 인 경우 NICE 가 {@code RTN_URL} 로 POST 한
 * {@code EncodeData} 를 {@code fnDecode + fnParse} 로 풀어 얻은 식별 정보를 담는다.
 * {@code success=false} 인 경우 {@link #message} 가 사용자 노출용 한국어 사유,
 * {@link #returnCode} 가 NICE SDK 의 원본 반환 코드.
 *
 * <p>정책: <b>회원 식별 키는 DI(64byte) 만 사용</b>. CI(88byte) 는 주민등록번호 수준의 강한
 * PII 로 취급하여 NICE 디코딩 단계에서부터 의도적으로 적재하지 않는다 — 본 DTO 도 CI 필드를
 * 보유하지 않음. DI 는 사이트 단위 식별자이므로 사이트 간 동일인 통합이 필요하면 별도 정책 검토.
 *
 * <p>DI 도 PII 이므로 컨트롤러에서 모델로 직접 전달하지 말고 세션에만 잠시 보관 후
 * 회원가입 흐름에 전달할 것 — URL 파라미터·로그 본문 절대 금지.
 */
@Getter
@Builder
public class NiceCheckResult {

    private final boolean success;
    private final int returnCode;
    private final String message;

    private final String reqSeq;        // 우리가 발급한 요청 번호 (세션 검증 키)
    private final String resSeq;        // NICE 응답 번호
    private final String authType;      // M(모바일)/X/U/F/S/C
    private final String name;          // 이름 (UTF8_NAME)
    private final String birthDate;     // YYYYMMDD
    private final String gender;        // 1=남, 0=여
    private final String nationalInfo;  // 0=내국인, 1=외국인
    private final String di;            // 중복가입 확인 (64 byte) — 회원 식별 키
    // CI 필드는 정책상 보유하지 않음 (PII 강도 → 적재 금지)
    private final String mobileNo;      // 휴대폰 번호
    private final String mobileCo;      // 통신사
    private final String cipherDateTime; // YYMMDDHHMMSS — NICE 가 암호화한 시각

    /** 실패 응답 — NICE SDK 반환 코드 + 한국어 메시지. */
    public static NiceCheckResult fail(int returnCode, String message) {
        return NiceCheckResult.builder()
                .success(false)
                .returnCode(returnCode)
                .message(message)
                .build();
    }
}
