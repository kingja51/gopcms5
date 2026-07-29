package com.gonet.primary.file.mapper;

import com.gonet.primary.file.dto.FileGroup;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** tb_file_group CRUD — 다운로드 권한의 단위. */
@EgovMapper
public interface FileGroupMapper {

    int insert(FileGroup group);

    FileGroup findById(@Param("fileGroupId") String fileGroupId);

    FileGroup findByEntity(@Param("entityType") String entityType,
                           @Param("entityId") String entityId);

    /** 정책만 바꾼다 — ensureGroup 이 기존 그룹의 권한을 맞출 때 쓴다. */
    int updateDownloadAuth(@Param("fileGroupId") String fileGroupId,
                           @Param("downloadAuth") String downloadAuth,
                           @Param("updatedBy") String updatedBy,
                           @Param("updatedIp") String updatedIp);
}
