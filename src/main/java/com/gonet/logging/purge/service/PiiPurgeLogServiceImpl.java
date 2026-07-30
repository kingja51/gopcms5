package com.gonet.logging.purge.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.crypto.PiiHash;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.logging.error.service.ErrorLogger;
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
    private final ErrorLogger errorLogger;

    /**
     * 적재 실패를 <b>삼킨다</b> — 예외를 밖으로 던지지 않는다.
     *
     * <p>전에는 그대로 전파했다. 그러면 logging_db 장애 하나가 <b>회원 탈퇴를 막는다</b> —
     * 셀프 탈퇴도, 관리자 강제 탈퇴도 불가능해진다(코드 리뷰 2026-07-30 지적).
     * 로그 하나 때문에 사용자 업무가 멈추는 것이 더 큰 손해라는 판단이다.
     *
     * <p>대신 <b>실패를 드러낸다</b>: {@code log_error} 에 남겨 관리자 화면
     * ({@code /adm/error-log})에서 확인할 수 있게 하고, 그 적재마저 실패하면
     * 파일 로그({@code gopcms-error.log})가 받는다.
     *
     * <p>남은 위험은 명시해 둔다 — 파기는 됐는데 파기 이력이 없는 행이 생길 수 있다.
     * 그때는 에러 로그의 {@code RECORD_FAILURE:PII_PURGE_LOG} 항목이 유일한 단서다.
     */
    @Override
    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX,
            propagation = Propagation.REQUIRES_NEW)
    public void writeMemberPurge(String memberId, String reason, String tableList) {
        try {
            insert(memberId, reason, tableList);
        } catch (RuntimeException e) {
            log.error("파기 이력 적재 실패(파기는 계속 진행) member={} reason={}: {}",
                    memberId, reason, e.toString());
            errorLogger.logRecordFailure("PII_PURGE_LOG",
                    "member=%s reason=%s tables=%s".formatted(memberId, reason, tableList), e);
        }
    }

    private void insert(String memberId, String reason, String tableList) {
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
