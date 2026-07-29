package com.gonet.logging.retention.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/**
 * {@code logging_db} 로그 파기.
 *
 * <p><b>테이블명을 파라미터로 넘기지만 SQL 이 되지는 않는다.</b> {@code ${}} 는 금지
 * 규약이고(SQLi), 테이블명은 {@code #{}} 로 바인딩할 수 없다. 그래서 XML 이
 * {@code <choose>} 로 <b>미리 적어 둔 문장 중 하나를 고른다</b> — 파라미터는 분기 선택에만
 * 쓰이고 문자열이 SQL 에 끼어들 자리가 없다.
 *
 * <p>모르는 이름이 오면 어느 분기에도 걸리지 않아 문법 오류로 즉시 터진다. 조용히
 * 엉뚱한 테이블을 지우는 것보다 낫다(호출 전 등록부 검증은 서비스가 한 번 더 한다).
 */
@EgovMapper
public interface LogRetentionMapper {

    /** 파기 대상 건수 — dry-run 이 보고하는 값. */
    int countExpired(@Param("table") String table, @Param("cutoff") LocalDateTime cutoff);

    /**
     * 오래된 것부터 {@code limit} 건 삭제.
     *
     * <p>한 번에 다 지우지 않는다. 상한을 두면 잘못 돌아도 피해가 한 배치로 제한되고,
     * 큰 테이블에서 락을 오래 잡아 서비스가 멈추는 일도 없다.
     */
    int deleteExpired(@Param("table") String table, @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);
}
