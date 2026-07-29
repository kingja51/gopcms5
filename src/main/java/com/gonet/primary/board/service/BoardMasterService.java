package com.gonet.primary.board.service;

import com.gonet.common.web.PageResult;
import com.gonet.primary.board.dto.BbsMasterAdmDto;
import com.gonet.primary.board.dto.BbsMasterSearch;
import java.util.List;

/** 게시판 마스터 — 게시판별 정책의 원천. */
public interface BoardMasterService {

    PageResult<BbsMasterAdmDto> getAdmPage(BbsMasterSearch cond);

    BbsMasterAdmDto getAdm(String bbsMasterId);

    /** 사용자 화면 진입점 — /bbs/{siteCode}/{bbsCode}. 사용 중지·삭제분은 열리지 않는다. */
    BbsMasterAdmDto getByCode(String siteId, String bbsCode);

    /** 통합 게시판 후보(일반 게시판만 + 수정 중인 자기 자신). */
    List<BbsMasterAdmDto> getGroupCandidates(String siteId, String selfId);

    /**
     * 통합 게시판이 실제로 합쳐 보여줄 게시판 목록.
     *
     * <p>일반 게시판이면 자기 자신 하나. 통합 게시판이면 {@code grouped_board_ids} 의 대상
     * 중 <b>살아 있고 사용 중인</b> 것만 — CSV 는 스냅샷이라 대상이 지워지거나 중지돼도
     * 값이 남는다. 그대로 조회하면 닫아 둔 게시판의 글이 합본에서 계속 보인다.
     */
    List<BbsMasterAdmDto> getGroupedTargets(BbsMasterAdmDto master);

    void saveAdm(BbsMasterAdmDto master);

    void deleteAdm(String bbsMasterId);
}
