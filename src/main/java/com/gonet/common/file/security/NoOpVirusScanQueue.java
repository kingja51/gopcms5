package com.gonet.common.file.security;

import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 백신 미연동 기본 구현 — 아무것도 하지 않고 상태를 PENDING 으로 남긴다.
 *
 * <p>주의: 이것은 "검사했고 안전하다" 가 아니라 <b>"검사하지 않았다"</b> 는 뜻이다.
 * PENDING 을 다운로드 허용으로 두는 것은 앞의 다중 방어(확장자·매직바이트·재인코딩)를
 * 통과했다는 전제 위의 운영 판단이며, 백신을 켜면 CLEAN 으로만 열리게 좁혀진다.
 *
 * <p>구현 선택은 {@code gopcms.file.clamav.enabled} 하나로 한다.
 * {@code @ConditionalOnMissingBean} 은 컴포넌트 스캔에서는 평가 순서에 좌우돼 신뢰할 수
 * 없다 — 실측에서 빈이 아예 등록되지 않아 기동이 실패했다. 설정 기반 분기가 예측 가능하다.
 */
@Component
@ConditionalOnProperty(name = "gopcms.file.clamav.enabled", havingValue = "false",
        matchIfMissing = true)
@Slf4j
public class NoOpVirusScanQueue implements VirusScanQueue {

    @Override
    public void enqueue(String fileId, Path storedFile) {
        log.debug("백신 미연동 — 검사 건너뜀 fileId={}", fileId);
    }
}
