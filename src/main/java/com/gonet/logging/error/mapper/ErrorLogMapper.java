package com.gonet.logging.error.mapper;

import com.gonet.logging.error.dto.ErrorLog;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** {@code log_error} — 적재는 insert 전용, 조회는 관리자 화면용. */
@EgovMapper
public interface ErrorLogMapper {

    int insert(ErrorLog log);

    List<ErrorLog> findPage(ErrorLog.Search search);

    int countPage(ErrorLog.Search search);

    /** 최근 {@code days} 일의 분류별 건수 — 상위 20개. */
    List<ErrorLog.ClassCount> findClassCounts(@Param("days") int days);

    /** 상세 — PK 가 복합(id, logged_at)이라 id 만으로 찾되 최신 1건을 취한다. */
    ErrorLog findById(@Param("logErrorId") Long logErrorId);
}
