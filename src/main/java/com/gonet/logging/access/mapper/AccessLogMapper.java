package com.gonet.logging.access.mapper;

import com.gonet.logging.access.dto.AccessLog;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 접근 로그 적재 매퍼 — logging SqlSessionFactory 스캔 (com.gonet.logging.**). */
@EgovMapper
public interface AccessLogMapper {

    int insert(AccessLog accessLog);
}
