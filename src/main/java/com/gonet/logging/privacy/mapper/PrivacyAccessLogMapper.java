package com.gonet.logging.privacy.mapper;

import com.gonet.logging.privacy.dto.PrivacyAccessLog;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** {@code log_privacy_access} — 개인정보 접근 이력. */
@EgovMapper
public interface PrivacyAccessLogMapper {

    int insert(PrivacyAccessLog log);

    /**
     * 특정 정보주체에 대한 최근 접근 이력.
     *
     * <p>정보주체 본인이 "누가 내 정보를 봤나" 를 물었을 때 답할 수 있어야 하고,
     * 관리자 화면에서도 한 회원에 대한 취급 내역이 한눈에 보여야 한다.
     */
    List<PrivacyAccessLog> recentByTarget(@Param("targetEntity") String targetEntity,
            @Param("targetId") String targetId, @Param("limit") int limit);
}
