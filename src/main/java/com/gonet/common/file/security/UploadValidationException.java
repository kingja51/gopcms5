package com.gonet.common.file.security;

/**
 * 업로드 거부 — 사용자 입력이 정책에 맞지 않는 경우(400).
 *
 * <p>메시지는 <b>사용자에게 그대로 보여줄 수 있는 문장</b>으로 쓴다. 다만 무엇이 왜 막혔는지
 * 지나치게 자세히 알려주면 우회 실험을 돕게 되므로, 정책 이름 정도로 그친다.
 */
public class UploadValidationException extends RuntimeException {

    public UploadValidationException(String message) {
        super(message);
    }
}
