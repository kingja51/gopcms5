package com.gonet.common.file.security;

/** 비인증·권한 미달 업로드 시도 — 엔진 진입 지점에서 즉시 끊는다(401/403). */
public class UnauthenticatedUploadException extends RuntimeException {

    public UnauthenticatedUploadException(String message) {
        super(message);
    }
}
