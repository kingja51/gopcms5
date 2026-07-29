package com.gonet.common.file.viewer;

/**
 * 문서 변환 실패 — 사용자에게는 "미리보기를 만들 수 없다" 로만 알린다.
 *
 * <p>실패 사유를 자세히 돌려주면 어떤 문서가 파서를 어떻게 흔드는지 알려주는 셈이 된다.
 * 상세는 서버 로그에만 남긴다.
 */
public class DocumentConversionException extends RuntimeException {

    public DocumentConversionException(String message) {
        super(message);
    }
}
