package com.gonet.primary.template.dto;

import com.gonet.common.audit.Auditable;
import lombok.Getter;
import lombok.Setter;

/**
 * 템플릿(시각 언어 축) 관리 DTO — tb_template.
 *
 * <p>{@code templateCode} 는 CSS 파일 1장을 가리킨다({@code /tmpl/css/{code}.css}).
 * 코드를 바꾸면 그 파일도 함께 있어야 하며, 없으면 기동 스모크가 부팅을 세운다.
 */
@Getter
@Setter
public class TemplateAdmDto extends Auditable {

    private String templateId;
    private String templateCode;
    private String templateName;

    /** 템플릿 기본 레이아웃 — 사이트가 레이아웃을 고르지 않으면 이 값이 쓰인다(NOT NULL) */
    private String defaultLayoutId;

    /** 디자인 지침 원문(design-md) — 참고용 텍스트 */
    private String designMd;

    private String description;
    private String useYn;

    /** 목록 표시용 조인·집계 */
    private String defaultLayoutCode;
    private int themeCount;
    private int siteCount;
}
