package com.gonet.primary.identity.service;

import com.gonet.primary.identity.dto.NiceCheckResult;
import com.gonet.primary.identity.dto.NiceEncodeResult;

/**
 * NICE CheckPlus 안심본인인증 서비스 — JSP 샘플의 스크립틀릿(NiceID.Check.CPClient
 * 호출부)을 도메인 서비스로 이관한 진입점.
 *
 * <p>계약:
 * <ul>
 *   <li>{@link #generateRequestNo()} — NICE 가 정의한 요청 번호 발급. 컨트롤러는 이를
 *       세션에 저장하고 콜백에서 검증한다 (세션 고정 방어).</li>
 *   <li>{@link #encode(String)} — 평문 RTN/ERR/SITECODE/REQ_SEQ 등을 NICE 가 정한
 *       포맷으로 직렬화 후 {@code fnEncode} 호출. 결과 {@code encData} 를 hidden form
 *       에 실어 NICE 팝업 URL 로 POST.</li>
 *   <li>{@link #decode(String)} — NICE 가 콜백한 {@code EncodeData} 를 복호화하고
 *       {@code fnParse} 로 키-값 맵을 풀어 DTO 로 반환.</li>
 * </ul>
 */
public interface NiceCheckService {

    String generateRequestNo();

    NiceEncodeResult encode(String reqSeq);

    NiceCheckResult decode(String encodeData);
}
