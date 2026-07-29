package com.gonet.primary.file.service;

import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.file.mapper.FileGroupMapper;
import com.gonet.primary.file.mapper.FileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정리 배치의 <b>단건 트랜잭션 작업자</b> — {@link FilePurgeService} 와 별도 빈이다.
 *
 * <p>같은 빈 안에 두면 {@code this.deleteRow(...)} 가 프록시를 우회해
 * {@code @Transactional} 이 <b>통째로 무시된다</b>(CLAUDE.md 트랜잭션 함정).
 * 그러면 배치 전체가 하나의 트랜잭션처럼 묶여, 한 건이 실패할 때 앞서 정리한 것까지
 * 되돌아간다 — 디스크는 이미 지운 뒤라서 DB 만 되살아나는 최악의 조합이 된다.
 *
 * <p>{@code REQUIRES_NEW} 로 건마다 끊는 이유도 같다. 1건 실패가 나머지를 막지 않아야
 * 배치가 매번 같은 지점에서 멈추지 않는다.
 */
@Component
@RequiredArgsConstructor
public class FilePurgeWorker {

    private final FileMapper fileMapper;
    private final FileGroupMapper fileGroupMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            transactionManager = MyBatisConfig.PRIMARY_TX)
    public void deleteFileRow(String fileId) {
        fileMapper.hardDelete(fileId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            transactionManager = MyBatisConfig.PRIMARY_TX)
    public void deleteGroupRow(String fileGroupId) {
        fileGroupMapper.hardDelete(fileGroupId);
    }
}
