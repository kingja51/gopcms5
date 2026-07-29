package com.gonet.logging.file.service;

import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.logging.file.dto.FileDownloadLog;
import com.gonet.logging.file.mapper.FileDownloadLogMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다운로드 이력 적재·조회 — logging_db 전용 TxManager.
 *
 * <p>기록은 {@code REQUIRES_NEW} 로 주 트랜잭션과 끊는다. 이력 적재가 실패해도 본 작업이
 * 롤백되면 안 되고, 반대로 본 작업이 롤백돼도 "시도가 있었다" 는 사실은 남아야 한다.
 *
 * <p>예외를 삼키는 try/catch 는 <b>이 빈 밖</b>({@link FileDownloadLogger})에 둔다.
 * 트랜잭션 안에서 삼키면 rollback-only 로 표시된 트랜잭션이 커밋 시점에 다시 터진다
 * (P6-3 로그인 이력에서 겪은 것과 같은 함정).
 */
@Service
@RequiredArgsConstructor
public class FileDownloadLogService {

    private final FileDownloadLogMapper mapper;

    @Transactional(transactionManager = MyBatisConfig.LOGGING_TX,
            propagation = Propagation.REQUIRES_NEW)
    public void insert(FileDownloadLog row) {
        mapper.insert(row);
    }

    @Transactional(readOnly = true, transactionManager = MyBatisConfig.LOGGING_TX)
    public List<FileDownloadLog> recentByFile(String fileId, int limit) {
        return mapper.findRecentByFile(fileId, limit);
    }

    @Transactional(readOnly = true, transactionManager = MyBatisConfig.LOGGING_TX)
    public long countByFile(String fileId) {
        return mapper.countByFile(fileId);
    }
}
