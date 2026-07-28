package com.gonet.common.audit;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 감사컬럼 6종 공통 부모 — 전 테이블 공통 컬럼(created_by/ip/at + updated_by/ip/at)과 1:1.
 *
 * <p>이 클래스를 상속한 DTO 는 {@link AuditInterceptor} 가 INSERT/UPDATE 시점에
 * 감사 필드를 자동 세팅한다(도메인 코드에서 수동 세팅 금지).
 * created_at/updated_at 은 DB DEFAULT/ON UPDATE 가 2차 안전망.
 */
@Getter
@Setter
public abstract class Auditable {

    private String createdBy;
    private String createdIp;
    private LocalDateTime createdAt;
    private String updatedBy;
    private String updatedIp;
    private LocalDateTime updatedAt;
}
