package com.gonet.logging.audit.mapper;

import com.gonet.logging.audit.dto.AuditLog;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** {@code log_audit} — 적재는 insert 전용(감사 기록은 고치지 않는다), 조회는 관리자용. */
@EgovMapper
public interface AuditLogMapper {

    int insert(AuditLog log);

    List<AuditLog> findPage(AuditLog.Search search);

    int countPage(AuditLog.Search search);

    /** 상세 — PK 가 복합({@code log_audit_id, logged_at})이라 id 로 찾되 최신 1건을 취한다. */
    AuditLog findById(@Param("logAuditId") Long logAuditId);
}
