package com.gonet.logging.purge.mapper;

import com.gonet.logging.purge.dto.PiiPurgeLog;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** {@code log_pii_purge} — insert 전용. 갱신·삭제 메서드를 두지 않는다. */
@EgovMapper
public interface PiiPurgeLogMapper {

    int insert(PiiPurgeLog log);
}
