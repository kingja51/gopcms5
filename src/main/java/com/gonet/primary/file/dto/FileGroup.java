package com.gonet.primary.file.dto;

import com.gonet.common.audit.Auditable;
import lombok.Getter;
import lombok.Setter;

/**
 * tb_file_group — 업로드 묶음이자 <b>다운로드 권한의 단위</b>.
 *
 * <p>권한을 파일이 아니라 그룹에 두는 이유: 글 하나에 붙은 첨부 5개가 서로 다른 공개
 * 범위를 갖는 상황은 정책 오류에 가깝다. 그룹 단위로 묶으면 "이 글의 첨부" 라는 하나의
 * 판단만 관리하면 된다.
 */
@Getter
@Setter
public class FileGroup extends Auditable {

    private String fileGroupId;
    /** BBS / CONTENT / BANNER / POPUP / MEMBER / EDITOR … 다형 참조(FK 없음). */
    private String entityType;
    private String entityId;
    private String siteId;
    private String siteCode;
    private String downloadAuth;
    private String deleteYn;
}
