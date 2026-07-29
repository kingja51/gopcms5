package com.gonet.primary.auth.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/**
 * 비밀번호 변경·이력 — 관리자(tb_admin_password_history)·회원(tb_member_password_history)
 * 두 계열이 컬럼 구성만 같고 테이블이 다르다. 벤더 분기가 아니라 <b>대상 테이블</b> 분기라
 * databaseId 가 아닌 메서드로 나눈다(동적 테이블명은 {@code ${}} 를 부르므로 금지).
 */
@EgovMapper
public interface PasswordMapper {

    /** 재사용 금지 검사용 — 최근 이력 해시(최신순). */
    List<String> findRecentAdminHashes(@Param("userId") String userId,
            @Param("limit") int limit);

    List<String> findRecentMemberHashes(@Param("userId") String userId,
            @Param("limit") int limit);

    int insertAdminHistory(@Param("historyId") String historyId,
            @Param("userId") String userId, @Param("passwordHash") String passwordHash);

    int insertMemberHistory(@Param("historyId") String historyId,
            @Param("userId") String userId, @Param("passwordHash") String passwordHash);

    /** 비밀번호 교체 + 변경/만료 일시 갱신 (만료는 changed_at + validDays). */
    int updateAdminPassword(@Param("userId") String userId,
            @Param("passwordHash") String passwordHash, @Param("validDays") int validDays,
            @Param("updatedBy") String updatedBy, @Param("updatedIp") String updatedIp);

    int updateMemberPassword(@Param("userId") String userId,
            @Param("passwordHash") String passwordHash, @Param("validDays") int validDays,
            @Param("updatedBy") String updatedBy, @Param("updatedIp") String updatedIp);
}
