package com.gonet.primary.member.service;

import com.gonet.common.web.PageResult;
import com.gonet.primary.member.dto.MemberAdmRow;
import com.gonet.primary.member.dto.MemberAdmSearch;
import com.gonet.primary.member.dto.MemberDormantRow;
import com.gonet.primary.member.dto.MemberDto;
import com.gonet.primary.member.dto.MemberWithdrawRow;
import java.util.List;
import java.util.Map;

/**
 * 관리자 회원 관리.
 *
 * <p><b>관리자는 회원을 생성하지 않는다</b>(정책). 가입은 본인 동의와 본인확인을 거쳐야
 * 성립하는데, 관리자가 대신 만들면 그 둘이 없는 계정이 생긴다. 그래서 이 인터페이스에
 * create 가 없다 — 빠뜨린 게 아니다.
 */
public interface MemberAdmService {

    PageResult<MemberAdmRow> getPage(MemberAdmSearch search);

    /** 상태별 건수 — 목록 상단 요약. 키는 status, 값은 건수. */
    Map<String, Integer> countByStatus(String siteId);

    /** 상세 — PII 는 평문으로 온다. 화면이 마스킹 여부를 정한다. */
    MemberDto get(String memberId);

    /**
     * 상태 변경.
     *
     * @throws IllegalArgumentException 허용되지 않는 상태값이거나 대상이 없을 때
     */
    void changeStatus(String memberId, String status);

    /** 잠금 해제 — 실패 카운트와 잠금 시각을 함께 되돌린다. */
    void unlock(String memberId);

    /**
     * 강제 탈퇴 — 회원 스스로 하는 탈퇴와 <b>같은 경로</b>를 탄다.
     *
     * <p>경로가 둘이면 PII 파기 범위나 원장 적재가 갈라진다(PLAN §P10-3).
     */
    void forceWithdraw(String memberId, String reason);

    PageResult<MemberDormantRow> getDormantPage(MemberAdmSearch search);

    PageResult<MemberWithdrawRow> getWithdrawPage(MemberAdmSearch search);

    /**
     * 내려받기 대상 조회 — 건수 상한이 걸린다.
     *
     * <p>상한에 걸렸는지는 호출부가 알아야 한다(사용자에게 "일부만 받았다" 를 알려야
     * 하므로). 반환 크기가 상한과 같으면 잘렸을 가능성이 있다고 본다.
     */
    List<MemberAdmRow> getForExport(MemberAdmSearch search);

    /** 내려받기 상한값 — 화면 안내와 잘림 판정에 쓴다. */
    int exportLimit();
}
