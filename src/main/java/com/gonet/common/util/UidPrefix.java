package com.gonet.common.util;

/**
 * PK 접두어 레지스트리 — doc/conventions.md §2 와 1:1 (유일 원본은 문서, 여기는 컴파일 가드).
 *
 * <p>신규 테이블 절차: ① conventions.md §2 표 등록 → ② 이 enum 추가 → ③ DDL 작성.
 * 접두어 재사용·변경 금지.
 */
public enum UidPrefix {

    /* ── 코어 (primary_db) ── */
    SIT("tb_site"),
    TPL("tb_template"),
    THM("tb_theme"),
    LAY("tb_layout"),
    MNU("tb_menu"),
    CNT("tb_content"),
    CNH("tb_content_history"),
    BNR("tb_banner"),
    POP("tb_popup"),
    TRM("tb_terms"),

    /* ── 게시판 (V9) ── */
    BBM("tb_bbs_master"),
    BBA("tb_bbs_article"),
    BBC("tb_bbs_comment"),
    BCT("tb_bbs_category"),
    LIK("tb_bbs_like"),
    RPT("tb_bbs_report"),

    /* ── 파일 (V9) ── */
    FGR("tb_file_group"),
    FIL("tb_file"),

    /* ── 회원·조직·인증 (V6) ── */
    ADM("tb_admin"),
    AGR("tb_admin_group"),
    ARL("tb_admin_role"),
    AIP("tb_admin_allow_ip"),
    APH("tb_admin_password_history"),
    MBR("tb_member"),
    MBC("tb_member_consent"),
    MDN("tb_member_dormant_notice"),
    MOT("tb_member_otp"),
    MBO("tb_member_oauth"),
    MPH("tb_member_password_history"),
    DPT("tb_department"),
    EMP("tb_employee"),
    ROL("tb_role"),
    AUT("tb_auth"),
    RLA("tb_role_auth"),
    RLH("tb_role_hierarchy"),
    RUA("tb_role_url_access"),
    LGH("tb_login_history"),

    /* ── 공통 프로그램·운영 ── */
    SCM("tb_schedule_master"),
    SCH("tb_schedule"),
    SVM("tb_survey_master"),
    SVY("tb_survey"),
    SVQ("tb_survey_question"),
    SVO("tb_survey_option"),
    SVR("tb_survey_response"),
    SVA("tb_survey_answer"),
    HOL("tb_holiday"),
    MTP("tb_mail_template"),
    NTF("tb_notification"),
    MWN("tb_minwon"),
    COD("tb_code"),
    CGR("tb_code_group"),
    AUD("tb_audit_log"),

    /* ── logging_db — 로그는 대개 시퀀스 PK, 아래만 UUID 채번 ── */
    PPG("log_pii_purge");

    private final String tableName;

    UidPrefix(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
