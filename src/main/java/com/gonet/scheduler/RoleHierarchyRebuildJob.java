package com.gonet.scheduler;

import com.gonet.primary.auth.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 역할 계층 closure·사용자 역할 CSV 정합성 배치 (P6-2).
 *
 * <p><b>정상 경로는 이벤트 트리거</b> — 역할·계층을 바꾼 트랜잭션이
 * {@link RoleService#rebuildHierarchy()} 를 직접 호출한다(P7 관리자 화면 저장 훅).
 * 이 스케줄은 그 경로를 우회한 변경(콘솔 수기 수정, 시드 반영)을 주기적으로 바로잡는
 * 안전망이라 <b>기본 비활성</b>({@code gopcms.batch.role-rebuild-cron: -})이다.
 *
 * <p>다중 인스턴스 운영 시 ShedLock 적용 필요 — 락 테이블(logging_db.shedlock)은 미도입.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleHierarchyRebuildJob {

    private final RoleService roleService;

    @Scheduled(cron = "${gopcms.batch.role-rebuild-cron:-}")
    public void rebuild() {
        log.info("역할 계층 재전개 배치 시작");
        roleService.rebuildHierarchy(); // 결과 요약 로그는 서비스가 남긴다
    }
}
