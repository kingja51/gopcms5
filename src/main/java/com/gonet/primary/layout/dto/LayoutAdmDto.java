package com.gonet.primary.layout.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 레이아웃(구조 축) 관리 DTO — tb_layout.
 *
 * <p>{@code layoutCode} 는 곧 <b>뷰 폴더명</b>({@code templates/layouts/{code}/})이다.
 * 코드를 바꾸면 물리 폴더도 함께 옮겨야 하며, 짝이 안 맞으면 기동 스모크
 * (LayoutSmokeRunner)가 부팅을 세운다 — 그래서 폼에서 경고를 띄운다.
 */
@Getter
@Setter
public class LayoutAdmDto {

    private String layoutId;
    private String layoutCode;
    private String layoutName;

    /** 설계안 참조(frame001~007) — wireframe/index.html 의 A~G 안 */
    private String wireframeRef;

    private String description;
    private int sortOrder;
    private String useYn;

    /** 목록 표시용 — 이 레이아웃을 쓰는 사이트 수(삭제 가능 판단) */
    private int siteCount;
}
