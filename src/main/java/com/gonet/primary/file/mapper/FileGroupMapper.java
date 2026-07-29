package com.gonet.primary.file.mapper;

import com.gonet.primary.file.dto.FileGroup;
import java.util.List;
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

    /**
     * 고아 그룹 — 파일이 하나도 없는 채 오래된 묶음.
     *
     * <p>폼을 열면 PK 를 미리 발급하고 그룹이 생기는데, 저장하지 않고 나가면 그대로 남는다.
     * 다형 참조(FK 없음)라 DB 가 대신 정리해 주지 않는다.
     */
    List<String> findOrphanGroupIds(@Param("cutoff") java.time.LocalDateTime cutoff,
                                    @Param("limit") int limit);

    int hardDelete(@Param("fileGroupId") String fileGroupId);
}
