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

    void saveAdm(BbsMasterAdmDto master);

    void deleteAdm(String bbsMasterId);
}
