package com.gonet.primary.dashboard.mapper;

import com.gonet.primary.dashboard.dto.DashboardStats.Bucket;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/**
 * 대시보드 집계 전용 매퍼 — 읽기만 한다.
 *
 * <p>도메인 매퍼(MemberMapper 등)를 거치지 않는 이유: 대시보드가 필요한 것은 <b>합계</b>뿐인데
 * 도메인 매퍼는 아직 없고, 있더라도 목록 쿼리를 전량 끌어와 앱에서 세는 것은 낭비다.
 * 집계는 DB 가 하고 앱은 받아 그린다.
 */
@EgovMapper
public interface DashboardMapper {

    /* ── 회원 ─────────────────────────────────────────────────────────── */

    long countMembers();

    long countMembersByStatus(@Param("status") String status);

    long countDormant();

    long countWithdraw();

    /** 최근 N개월 가입 추이 — 값이 없는 달은 결과에 없으므로 서비스가 0 으로 채운다. */
    List<Bucket> memberJoinTrend(@Param("months") int months);

    List<Bucket> memberStatusDistribution();

    /* ── 게시판 ───────────────────────────────────────────────────────── */

    long countBoards();

    long countArticles();

    long countComments();

    List<Bucket> articleByBoard(@Param("limit") int limit);

    List<Bucket> articleTrend(@Param("months") int months);

    /* ── 파일 ─────────────────────────────────────────────────────────── */

    long countFiles();

    /** 총 용량(바이트). 파일이 하나도 없으면 SUM 이 NULL 이라 COALESCE 로 0 을 보장한다. */
    long sumFileBytes();

    List<Bucket> fileByExtension(@Param("limit") int limit);

    List<Bucket> fileByScanStatus();

    /* ── 컨텐츠 ───────────────────────────────────────────────────────── */

    List<Bucket> contentStatusDistribution();
}
