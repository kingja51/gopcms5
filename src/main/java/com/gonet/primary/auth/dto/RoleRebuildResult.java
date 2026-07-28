package com.gonet.primary.auth.dto;

/**
 * 역할 계층 재전개 결과 — 배치 로그·관리자 화면 피드백용.
 *
 * <p>{@code adminsFixed}·{@code membersFixed} 는 스냅샷이 계층과 어긋나 있던 계정 수다 —
 * 0 이 아니면 계층 변경 경로 어딘가가 재계산을 건너뛰었다는 신호다.
 *
 * @param roles       전개 대상 활성 역할 수
 * @param edges       재생성된 closure 행 수 (self 포함)
 * @param adminsFixed role_ids/role_codes 가 실제로 바뀐 관리자 수
 * @param membersFixed role_ids 가 실제로 바뀐 회원 수
 */
public record RoleRebuildResult(int roles, int edges, int adminsFixed, int membersFixed) {
}
