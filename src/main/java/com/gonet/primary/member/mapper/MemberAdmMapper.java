package com.gonet.primary.member.mapper;

import com.gonet.primary.member.dto.MemberAdmRow;
import com.gonet.primary.member.dto.MemberAdmSearch;
import com.gonet.primary.member.dto.MemberDormantRow;
import com.gonet.primary.member.dto.MemberWithdrawRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/**
 * 관리자 회원 조회·상태 변경.
 *
 * <p>가입·마이페이지가 쓰는 {@link MemberMapper} 와 나눈 이유: 관리자 조회는 목록
 * 전용 결과({@link MemberAdmRow})와 검색 분기를 갖고, 회원 자신의 경로와 권한 성격이
 * 다르다. 한 매퍼에 섞으면 "회원이 부를 수 있는 쿼리" 와 "관리자만 부를 수 있는 쿼리" 가
 * 구분되지 않는다.
 */
@EgovMapper
public interface MemberAdmMapper {

    List<MemberAdmRow> findPage(MemberAdmSearch search);

    int countPage(MemberAdmSearch search);

    /** 상태별 건수 — 목록 상단 요약. */
    List<Map<String, Object>> countByStatus(@Param("siteId") String siteId);

    /**
     * 상태 변경. {@code ACTIVE} 로 되돌릴 때는 잠금 흔적도 함께 지운다 —
     * 상태만 바꾸고 {@code locked_until} 이 남으면 "활성인데 로그인 안 되는" 계정이 된다.
     */
    int updateStatus(@Param("memberId") String memberId, @Param("status") String status,
            @Param("actor") String actor, @Param("actorIp") String actorIp);

    /** 잠금 해제 — 실패 카운트·잠금 시각을 함께 되돌린다. */
    int unlock(@Param("memberId") String memberId, @Param("actor") String actor,
            @Param("actorIp") String actorIp);

    List<MemberDormantRow> findDormantPage(MemberAdmSearch search);

    int countDormantPage(MemberAdmSearch search);

    List<MemberWithdrawRow> findWithdrawPage(MemberAdmSearch search);

    int countWithdrawPage(MemberAdmSearch search);

    /**
     * 내려받기용 조회 — 페이지가 아니라 {@code exportLimit} 만 둔다.
     *
     * <p>상한이 없으면 한 번의 클릭으로 전 회원 개인정보가 파일로 빠져나간다.
     * 검색 조건은 목록과 같은 것을 쓴다 — 화면에서 본 것과 받은 것이 달라지면 안 된다.
     */
    List<MemberAdmRow> findForExport(MemberAdmSearch search);
}
