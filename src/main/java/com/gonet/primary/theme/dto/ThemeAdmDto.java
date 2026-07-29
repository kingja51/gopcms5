package com.gonet.primary.theme.dto;

import com.gonet.common.audit.Auditable;
import lombok.Getter;
import lombok.Setter;

/**
 * 테마(색 축) 관리 DTO — tb_theme.
 *
 * <p>테마는 <b>파일이 없는 축</b>이다: {@code cssClass} 가 {@code <html class>} 에 붙어
 * 템플릿 CSS 안의 {@code --brand-*} 변수를 갈아끼운다. 그래서 템플릿에 종속되며
 * (복합 FK), 다른 템플릿의 테마를 사이트에 물리면 DB 가 거부한다(P5 실증).
 */
@Getter
@Setter
public class ThemeAdmDto extends Auditable {

    private String themeId;

    /** 소속 템플릿 — 테마는 템플릿 밖에서 홀로 존재하지 않는다 */
    private String templateId;

    private String themeCode;
    private String themeName;

    /** {@code <html>} 에 붙는 클래스 ('' = 템플릿 기본 브랜드) */
    private String cssClass;

    private int sortOrder;
    private String useYn;

    /** 목록 표시용 */
    private String templateCode;
    private int siteCount;
}
