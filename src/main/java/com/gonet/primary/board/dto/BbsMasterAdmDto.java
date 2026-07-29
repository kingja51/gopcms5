package com.gonet.primary.board.dto;

import com.gonet.common.audit.Auditable;
import lombok.Getter;
import lombok.Setter;

/** 게시판 마스터 — 게시판 하나의 정책 전부. */
@Getter
@Setter
public class BbsMasterAdmDto extends Auditable {

    private String bbsMasterId;
    private String siteId;
    private String siteCode;
    /** 연결 메뉴 — 다형 참조라 FK 가 없다. 비워도 게시판은 동작한다. */
    private String menuId;
    /** 사이트 안에서 유일. URL 에 그대로 노출된다 — /bbs/{siteCode}/{bbsCode} */
    private String bbsCode;
    private String bbsName;
    private String bbsType;

    private String commentYn;
    private String fileYn;
    private Integer fileCountMax;
    /** 바이트. 화면은 MB 로 받아 환산한다 — 운영자가 바이트를 세게 하지 않는다. */
    private Long fileSizeMax;
    private String anonymousYn;
    private String noticeTopYn;
    /** Y = 본문을 sanitize 후 HTML 로, N = 평문. 위지윅을 붙일지 정하는 스위치다. */
    private String htmlYn;
    private String captchaYn;

    private String readAuth;
    private String writeAuth;
    /** 첨부 다운로드 최소 권한 — 글의 file_group 으로 내려간다. */
    private String downloadAuth;

    /** 통합 게시판(B7) — 비어 있으면 일반 게시판. */
    private String groupedBoardIds;
    private String description;
    private String useYn;

    /* 목록 표시용 — 삭제 차단 판단과 규모 파악에 쓴다 */
    private String siteName;
    private String menuName;
    private int articleCount;
    private int categoryCount;

    /** 화면 입력값(MB) — 저장 시 fileSizeMax(byte)로 환산한다. */
    private Integer fileSizeMaxMb;

    public boolean isAggregator() {
        return groupedBoardIds != null && !groupedBoardIds.isBlank();
    }
}
