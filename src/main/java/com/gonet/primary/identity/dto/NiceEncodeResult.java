package com.gonet.primary.identity.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * NICE CheckPlus 암호화 데이터 생성 결과 — 사용자에게 보여줄 폼의 hidden input
 * {@code EncodeData} 값을 만든다.
 *
 * <p>{@code encData} 가 비어 있으면 {@code message} 에 사용자 노출용 사유.
 * 둘 중 하나만 의미 있음.
 */
@Getter
@RequiredArgsConstructor
public class NiceEncodeResult {

    private final String encData;
    private final String message;

    public static NiceEncodeResult ok(String encData) {
        return new NiceEncodeResult(encData, null);
    }

    public static NiceEncodeResult fail(String message) {
        return new NiceEncodeResult(null, message);
    }
}
