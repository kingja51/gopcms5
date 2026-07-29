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

    /* ── 정리 배치 (P8-5) ──────────────────────────────────────────────── */

    /**
     * 물리 삭제 대상 — soft delete 후 보존기간이 지난 파일.
     *
     * <p>바로 지우지 않고 유예를 두는 이유: 오삭제를 되돌릴 창이 필요하고,
     * 무결성 대조(file_hash)를 해야 할 사건이 뒤늦게 드러나기도 한다.
     */
    List<FileItem> findPurgeTargets(@Param("cutoff") java.time.LocalDateTime cutoff,
                                    @Param("limit") int limit);

    /** 물리 파일을 지운 뒤 행도 지운다 — 실체 없는 행이 남으면 목록이 거짓말을 한다. */
    int hardDelete(@Param("fileId") String fileId);

    /**
     * 재검사 대상 — 결과가 확정되지 않은 채 오래 머문 파일.
     * 스캐너가 죽었거나 응답을 못 받은 경우가 여기 쌓인다.
     */
    List<FileItem> findRescanTargets(@Param("staleBefore") java.time.LocalDateTime staleBefore,
                                     @Param("limit") int limit);
}
