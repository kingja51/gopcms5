package com.gonet.primary.board.dto;

import com.gonet.common.audit.Auditable;
import lombok.Getter;
import lombok.Setter;

/** 게시판 안의 분류 — 글당 1개(옵셔널). */
@Getter
@Setter
public class BbsCategoryAdmDto extends Auditable {

    private String categoryId;
    private String bbsMasterId;
    /** 게시판 안에서 유일. */
    private String categoryCode;
    private String categoryName;
    private Integer sortOrder;
    private String useYn;

    /** 삭제 차단 판단용 — 이 분류를 쓰는 글 수. */
    private int articleCount;
}
