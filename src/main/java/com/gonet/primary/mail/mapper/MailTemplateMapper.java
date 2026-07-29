package com.gonet.primary.mail.mapper;

import com.gonet.primary.mail.dto.MailTemplate;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** tb_mail_template 조회 — 메일 본문은 운영자가 화면에서 고칠 수 있어야 한다(코드 고정 금지). */
@EgovMapper
public interface MailTemplateMapper {

    MailTemplate findByCode(@Param("templateCode") String templateCode);
}
