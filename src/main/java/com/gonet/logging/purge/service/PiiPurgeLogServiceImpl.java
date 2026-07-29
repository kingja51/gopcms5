package com.gonet.logging.purge.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.crypto.PiiHash;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.logging.purge.dto.PiiPurgeLog;
import com.gonet.logging.purge.mapper.PiiPurgeLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파기 이력 적재 — logging_db 전용 TxManager + {@code REQUIRES_NEW}.
 *
 * <p>격리가 특히 중요한 자리다. 파기는 primary_db 트랜잭션에서 일어나고 이력은
 * logging_db 에 남는다 — 크로스 DB 라 애초에 한 트랜잭션으로 묶을 수 없다(규약 §3).
 * 그래서 <b>순서</b>로 안전을 확보한다: 실제 삭제보다 <b>먼저</b> 이력을 남긴다.
 * 뒤에 남기면 삭제가 성공하고 이력 적재가 실패했을 때 "지운 흔적이 없는 삭제" 가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PiiPurgeLogServiceImpl extends AbstractCmsService implements PiiPurgeLogService {

    /**
     * 파기 근거 — 화면·이력에 그대로 남는 문구다.
     *
     * <p>설정으로 뺀 이유: 근거 조항은 법령 개정이나 기관 정책에 따라 달라지는데,
     * 코드에 박아 두면 그때 빌드를 다시 해야 한다.
     */
    @Value("${gopcms.retention.legal-basis:개인정보보호법 제21조(개인정보의 파기)}")
    private String legalBasis;

    private final PiiPurgeLogMapper mapper;
    private final PiiHash piiHash;

    @Override
    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX,
            propagation = Propagation.REQUIRES_NEW)
    public void writeMemberPurge(String memberId, String reason, String tableList) {
        PiiPurgeLog row = new PiiPurgeLog();
        row.setPiiPurgeLogId(Uid.next(UidPrefix.PPG));
        row.setUserType(PiiPurgeLog.USER_TYPE_MEMBER);
        // 평문 ID 를 담으면 파기 이력 자체가 "이 사람이 회원이었다" 는 개인정보가 된다
        row.setUserIdHash(piiHash.hash(memberId));
        row.setPurgeReason(reason);
        row.setTableList(tableList);
        row.setLegalBasis(legalBasis);
        row.setCreatedBy(AuditorContext.currentUserId());
        row.setCreatedIp(AuditorContext.currentIp());
        mapper.insert(row);
    }
}
