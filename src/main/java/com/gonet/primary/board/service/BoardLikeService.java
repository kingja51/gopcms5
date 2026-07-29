package com.gonet.primary.board.service;

/** 좋아요 토글. */
public interface BoardLikeService {

    /**
     * 켜고 끄기. 이미 눌렀으면 취소된다.
     *
     * @return 토글 후 상태 — {@code liked} 여부와 갱신된 총 개수
     */
    LikeResult toggle(String targetType, String targetId, String sourceUrl);

    /** 토글 결과 — 화면이 버튼 상태와 숫자를 함께 갱신할 수 있게 둘 다 돌려준다. */
    record LikeResult(boolean liked, long count) {
    }
}
