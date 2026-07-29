package com.gonet.primary.board.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.board.dto.BbsCategoryAdmDto;
import com.gonet.primary.board.mapper.BbsCategoryMapper;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시판 카테고리 서비스 — 게시판 안에서만 유효한 분류. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class BoardCategoryServiceImpl extends AbstractCmsService implements BoardCategoryService {

    /** 목록 필터 쿼리스트링에 실릴 수 있어 코드도 좁게 잡는다. */
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,49}$");

    private final BbsCategoryMapper bbsCategoryMapper;

    @Override
    public List<BbsCategoryAdmDto> getByBoard(String bbsMasterId) {
        return bbsCategoryMapper.findByBoard(bbsMasterId);
    }

    @Override
    public BbsCategoryAdmDto getAdm(String categoryId) {
        return bbsCategoryMapper.findById(categoryId);
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void saveAdm(BbsCategoryAdmDto category) {
        if (category.getBbsMasterId() == null || category.getBbsMasterId().isBlank()) {
            throw new IllegalArgumentException("게시판이 지정되지 않았습니다.");
        }
        if (category.getCategoryCode() == null
                || !CODE.matcher(category.getCategoryCode()).matches()) {
            throw new IllegalArgumentException("분류 코드는 영문·숫자로 시작하는 1~50자여야 합니다.");
        }
        if (category.getCategoryName() == null || category.getCategoryName().isBlank()) {
            throw new IllegalArgumentException("분류 이름을 입력해 주세요.");
        }
        // 코드는 게시판 안에서만 유일하면 된다 — 다른 게시판의 같은 코드는 서로 무관하다
        if (bbsCategoryMapper.countByCode(category.getBbsMasterId(),
                category.getCategoryCode(), category.getCategoryId()) > 0) {
            throw new IllegalArgumentException(
                    "이 게시판에 이미 있는 분류 코드입니다. (%s)".formatted(category.getCategoryCode()));
        }
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        category.setUseYn("Y".equals(category.getUseYn()) ? "Y" : "N");

        if (category.getCategoryId() == null || category.getCategoryId().isBlank()) {
            category.setCategoryId(Uid.next(UidPrefix.BCT));
            bbsCategoryMapper.insert(category);
        } else {
            bbsCategoryMapper.update(category);
        }
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    public void deleteAdm(String categoryId) {
        // 이 분류를 쓰는 글이 있으면 지우지 않는다 — 지우면 그 글들이
        // 분류 없는 상태가 아니라 '존재하지 않는 분류' 를 가리키게 된다.
        int articles = bbsCategoryMapper.countArticles(categoryId);
        if (articles > 0) {
            throw new IllegalStateException(
                    "이 분류를 쓰는 게시글이 %d건 있어 삭제할 수 없습니다.".formatted(articles));
        }
        bbsCategoryMapper.softDelete(categoryId,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
    }
}
