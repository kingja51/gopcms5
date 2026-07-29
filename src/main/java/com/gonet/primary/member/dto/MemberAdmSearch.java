package com.gonet.primary.member.dto;

import com.gonet.common.web.PageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 회원 목록 검색 조건.
 *
 * <p><b>검색 가능한 컬럼이 암호화 정책에 묶여 있다.</b> 이름·이메일·전화는 AES-GCM
 * 암호문이라 LIKE 를 걸 수 없다(같은 값도 매번 다른 문자열이 된다). 그래서:
 * <ul>
 *   <li>{@code keyword} — 평문 컬럼({@code login_id}, {@code nickname})만 LIKE</li>
 *   <li>{@code email} — {@code email_hash} <b>정확 일치</b>. 부분 검색은 불가능하다</li>
 *   <li>{@code phone} — {@code phone_hash} 정확 일치(숫자만 남겨 정규화한 뒤)</li>
 * </ul>
 * 이 제약이 화면 설계를 규정한다 — 이메일 입력창에 "전체 주소를 입력하세요" 안내가
 * 붙는 이유다.
 */
@Getter
@Setter
public class MemberAdmSearch extends PageRequest {

    /** 사이트 스코프 — 비우면 전 사이트. */
    private String siteId;

    /** ACTIVE | LOCKED | EMAIL_PENDING | SUSPENDED. */
    private String status;

    /** HOMEPAGE | NAVER | KAKAO | GOOGLE … */
    private String joinType;

    /** 이메일 — 해시 정확 매칭이라 전체 주소여야 한다. 화면 입력값 그대로다. */
    private String email;

    /** 전화번호 — 해시 정확 매칭. 하이픈은 무시된다. 화면 입력값 그대로다. */
    private String phone;

    /**
     * 검색에 실제로 쓰이는 해시 — 서비스가 채운다.
     *
     * <p>입력값 자리에 해시를 덮어쓰면 검색창에 64자 16진수가 되돌아온다(사용자가 친 것을
     * 화면이 잃는다). 표시용과 조회용을 나눠 둔다.
     */
    private String emailHash;
    private String phoneHash;

    /** 잠금된 계정만 — 운영자가 해제 대상을 골라 보는 경로. */
    private boolean lockedOnly;

    /** 실명인증(DI)을 마치지 않은 계정만. */
    private boolean unverifiedOnly;

    /**
     * 내려받기 건수 상한 — 화면 페이지 크기({@code size})와 별개다.
     *
     * <p>{@code size} 는 {@code PageRequest} 가 100 으로 묶는다(관리자가 URL 을 만져
     * 한 화면에 수천 건을 띄우지 못하게). 내려받기는 그보다 커야 쓸모가 있지만 무제한이면
     * 클릭 한 번에 전 회원 개인정보가 파일로 나간다. 그래서 상한이 별도 값으로 존재한다.
     * 서비스가 설정값에서 채워 넣는다 — 화면 파라미터로 받지 않는다.
     */
    private int exportLimit;
}
