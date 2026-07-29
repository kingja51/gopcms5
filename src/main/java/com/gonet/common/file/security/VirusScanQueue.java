package com.gonet.common.file.security;

import java.nio.file.Path;

/**
 * 방어 ⑥ — 백신 검사 큐(추상).
 *
 * <p>검사는 <b>비동기</b>다. 업로드 응답을 백신 응답까지 붙잡아 두면 사용자 경험이 무너지고,
 * 백신이 죽으면 업로드 전체가 멈춘다. 대신 결과가 나올 때까지 파일 상태를 PENDING 으로 두고
 * 다운로드 정책이 그 상태를 본다.
 */
public interface VirusScanQueue {

    /** 검사 요청 — 구현체가 비동기로 처리하고 상태를 갱신한다. */
    void enqueue(String fileId, Path storedFile);
}
