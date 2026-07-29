package com.gonet.common.file.dto;

/**
 * 업로드 엔진의 결과 — 방어 단계를 모두 통과한 파일의 사실만 담는다.
 *
 * <p>엔진은 DB 를 모른다. 이 값을 받아 {@code tb_file} 로 옮기는 것은 도메인 서비스의 몫이다.
 *
 * @param originalName 사용자가 보낸 이름 — <b>표시용</b>. 경로 조작 방지를 위해 저장에는 쓰지 않는다
 * @param storedName   디스크에 놓인 이름 (UUID + 확장자)
 * @param storedPath   {@code baseDir} 기준 상대 경로
 * @param extension    소문자 확장자
 * @param mimeDetected Tika 가 매직바이트로 판별한 실제 MIME — 방어 판단의 기준값
 * @param mimeClient   클라이언트가 신고한 Content-Type — <b>참고용, 신뢰 금지</b>
 * @param sizeBytes    최종 크기(재인코딩 후 값)
 * @param sha256       무결성·중복 판정용 해시
 * @param image        이미지 여부
 * @param reencoded    재인코딩 수행 여부 (이미지에 심어진 스크립트 제거)
 */
public record UploadCommit(
        String originalName,
        String storedName,
        String storedPath,
        String extension,
        String mimeDetected,
        String mimeClient,
        long sizeBytes,
        String sha256,
        boolean image,
        boolean reencoded) {
}
