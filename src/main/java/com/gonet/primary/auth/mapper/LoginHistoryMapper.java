package com.gonet.primary.auth.mapper;

import com.gonet.primary.auth.dto.LoginHistory;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 로그인 이력 적재 (insert-only). */
@EgovMapper
public interface LoginHistoryMapper {

    int insert(LoginHistory history);
}
