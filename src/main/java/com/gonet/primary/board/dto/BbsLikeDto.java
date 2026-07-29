package com.gonet.primary.board.dto;

import com.gonet.common.audit.Auditable;
import lombok.Getter;
import lombok.Setter;

/** 좋아요 — 게시글·댓글·컨텐츠 공용(다형 참조). 익명은 지원하지 않는다(중복 방지 불가). */
@Getter
@Setter
public class BbsLikeDto extends Auditable {

    private String likeId;
    private String targetType;
    private String targetId;
    private String userId;
    private String userType;
    /** 클릭이 일어난 페이지 — 어느 화면에서 반응이 나왔는지 분석용. */
    private String sourceUrl;
    private String menuId;
    private String deleteYn;
}
