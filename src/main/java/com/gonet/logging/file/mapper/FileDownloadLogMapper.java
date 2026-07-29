package com.gonet.logging.file.mapper;

import com.gonet.logging.file.dto.FileDownloadLog;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** 다운로드 이력 — 적재(insert-only) + 파일별 최근 조회. */
@EgovMapper
public interface FileDownloadLogMapper {

    int insert(FileDownloadLog log);

    /** 관리 상세 화면의 "최근 내려받은 기록". */
    List<FileDownloadLog> findRecentByFile(@Param("fileId") String fileId,
                                           @Param("limit") int limit);

    long countByFile(@Param("fileId") String fileId);
}
