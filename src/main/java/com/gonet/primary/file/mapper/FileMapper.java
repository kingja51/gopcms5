package com.gonet.primary.file.mapper;

import com.gonet.primary.file.dto.FileItem;
import com.gonet.primary.file.dto.FileSearch;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.egovframe.rte.psl.dataaccess.mapper.EgovMapper;

/** tb_file CRUD. */
@EgovMapper
public interface FileMapper {

    int insert(FileItem file);

    FileItem findById(@Param("fileId") String fileId);

    /** 그룹의 활성 파일 — 목록·ZIP·picker 초기 표시가 모두 이걸 쓴다. */
    List<FileItem> findByGroup(@Param("fileGroupId") String fileGroupId);

    List<FileItem> findPage(FileSearch cond);

    int countPage(FileSearch cond);

    int increaseDownloadCount(@Param("fileId") String fileId);

    int softDelete(@Param("fileId") String fileId,
                   @Param("updatedBy") String updatedBy,
                   @Param("updatedIp") String updatedIp);

    /** picker 에서 빠진 파일 정리 — keep 목록에 없는 그룹 내 파일을 모두 내린다. */
    int softDeleteNotIn(@Param("fileGroupId") String fileGroupId,
                        @Param("keepIds") List<String> keepIds,
                        @Param("updatedBy") String updatedBy,
                        @Param("updatedIp") String updatedIp);

    int updateScanStatus(@Param("fileId") String fileId, @Param("status") String status);

    int updateThumbnail(@Param("fileId") String fileId, @Param("thumbnailPath") String thumbnailPath);
}
