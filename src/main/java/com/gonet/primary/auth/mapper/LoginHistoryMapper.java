package com.gonet.primary.auth.mapper;

import com.gonet.primary.auth.dto.LoginHistory;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 로그인 이력 — 적재(insert-only) + 직전 로그인 조회. */
@EgovMapper
public interface LoginHistoryMapper {

    int insert(LoginHistory history);

    /**
     * 보존기간이 지난 로그인 이력 건수 — 파기 배치의 dry-run 보고값.
     *
     * <p>성격은 로그인데 위치가 primary_db 다(P6-3). 그래서 logging_db 파기와 같은
     * 배치에 있으면서도 <b>다른 DataSource·TxManager</b> 를 탄다.
     */
    int countExpired(@org.apache.ibatis.annotations.Param("cutoff")
            java.time.LocalDateTime cutoff);

    /** 오래된 것부터 상한만큼 삭제. */
    int deleteExpired(@org.apache.ibatis.annotations.Param("cutoff")
            java.time.LocalDateTime cutoff,
            @org.apache.ibatis.annotations.Param("limit") int limit);

    /**
     * 직전(= 이번 로그인 <b>이전</b>) 성공 로그인 1건.
     *
     * <p>이번 로그인 이력은 이미 적재된 뒤이므로 최신 1건을 건너뛰고 그 다음을 집는다.
     * "이전 로그인 일시" 는 계정 도용을 <b>본인이</b> 알아채는 가장 값싼 장치다 —
     * 내가 접속한 적 없는 시각이 찍혀 있으면 그 자체가 신호다.
     */
    LoginHistory findPreviousSuccess(@Param("userId") String userId);
}
