package com.gonet.primary.board.mapper;

import com.gonet.primary.board.dto.BbsMasterAdmDto;
import com.gonet.primary.board.dto.BbsMasterSearch;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 게시판 마스터 CRUD — tb_bbs_master. */
@EgovMapper
public interface BbsMasterMapper {

    List<BbsMasterAdmDto> findPage(BbsMasterSearch cond);

    int countPage(BbsMasterSearch cond);

    BbsMasterAdmDto findById(@Param("bbsMasterId") String bbsMasterId);

    /** 사용자 화면 진입점 — /bbs/{siteCode}/{bbsCode} 가 이걸로 게시판을 찾는다. */
    BbsMasterAdmDto findByCode(@Param("siteId") String siteId, @Param("bbsCode") String bbsCode);

    /** 사이트 안에서 코드 중복 검사 (수정 시 자기 자신 제외). */
    int countByCode(@Param("siteId") String siteId, @Param("bbsCode") String bbsCode,
                    @Param("excludeId") String excludeId);

    /** 삭제 차단 판단 — 글이 하나라도 있으면 지우지 않는다. */
    int countArticles(@Param("bbsMasterId") String bbsMasterId);

    /** 통합 게시판 후보 — 일반 게시판만(중첩 금지). */
    List<BbsMasterAdmDto> findGroupCandidates(@Param("siteId") String siteId,
                                              @Param("selfId") String selfId);

    int insert(BbsMasterAdmDto master);

    int update(BbsMasterAdmDto master);

    int softDelete(@Param("bbsMasterId") String bbsMasterId,
                   @Param("updatedBy") String updatedBy,
                   @Param("updatedIp") String updatedIp);

    /**
     * 마스터의 다운로드 권한을 소속 글의 파일그룹에 일괄 반영.
     *
     * <p><b>공지글은 제외한다</b>(notice_yn='Y'). 공지 첨부는 등록 시점에 ANONYMOUS 로
     * 고정되는데(P9-2 resolveArticleDownloadAuth), 마스터 정책을 조이면서 같이 조이면
     * 누구나 볼 수 있어야 할 공고문이 조용히 닫힌다.
     */
    int cascadeDownloadAuth(@Param("bbsMasterId") String bbsMasterId,
                            @Param("downloadAuth") String downloadAuth,
                            @Param("updatedBy") String updatedBy,
                            @Param("updatedIp") String updatedIp);
}
