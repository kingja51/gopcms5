package com.gonet.primary.site.dto;

import com.gonet.common.audit.Auditable;
import lombok.Getter;
import lombok.Setter;

/**
 * 사이트 관리 화면의 목록 행 겸 폼 모델 (tb_site 편집 컬럼 전량).
 *
 * <p>렌더용 {@link SiteContext} 와 분리한 이유: 저쪽은 3축 <b>폴백이 적용된 최종값</b>이라
 * "관리자가 무엇을 선택했는가"(NULL=템플릿 기본)를 표현할 수 없다. 편집 화면은 원본 값을 다뤄야 한다.
 */
@Getter
@Setter
public class SiteAdmDto extends Auditable {

    private String siteId;
    private String siteCode;
    private String siteName;
    private String domain;
    private String defaultLang;
    private String parentSiteId;

    /** 3축 선택 — NULL 이면 "템플릿 기본"(SiteServiceImpl 폴백 대상) */
    private String templateId;
    private String themeId;
    private String layoutId;

    private String defaultYn;
    private int sortOrder;
    private String logoPath;
    private String faviconPath;
    private String description;
    private String headMeta;
    private String copyright;
    private String useYn;

    /** 목록 표시용 조인값 — 편집 대상 아님 */
    private String templateCode;
    private String themeCode;
    private String layoutCode;
    private int menuCount;
    private int contentCount;
}
