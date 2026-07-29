package com.gonet.primary.dashboard.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.dashboard.dto.DashboardStats;
import com.gonet.primary.dashboard.dto.DashboardStats.Bucket;
import com.gonet.primary.dashboard.mapper.DashboardMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 집계 — 읽기 전용.
 *
 * <p>월별 추이는 <b>앱에서 빈 달을 채운다</b>. GROUP BY 결과에는 데이터가 없는 달이 아예
 * 없어서, 그대로 그리면 1월과 5월이 붙어버려 추세가 왜곡된다. 달력을 만드는 일은
 * SQL 로도 되지만(재귀 CTE) 벤더마다 문법이 갈리므로 여기서 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class DashboardServiceImpl extends AbstractCmsService implements DashboardService {

    /** 추이 차트가 보여줄 개월 수. */
    private static final int TREND_MONTHS = 12;
    /** 가로 막대(게시판별·확장자별)에 세울 최대 항목. */
    private static final int TOP_N = 8;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DashboardMapper dashboardMapper;

    @Override
    public DashboardStats getStats() {
        DashboardStats s = new DashboardStats();

        s.setMemberTotal(dashboardMapper.countMembers());
        s.setMemberActive(dashboardMapper.countMembersByStatus("ACTIVE"));
        s.setMemberLocked(dashboardMapper.countMembersByStatus("LOCKED"));
        s.setMemberSuspended(dashboardMapper.countMembersByStatus("SUSPENDED"));
        s.setMemberDormant(dashboardMapper.countDormant());
        s.setMemberWithdraw(dashboardMapper.countWithdraw());
        s.setMemberJoinTrend(fillMonths(dashboardMapper.memberJoinTrend(TREND_MONTHS)));
        s.setMemberStatus(dashboardMapper.memberStatusDistribution());

        s.setBoardCount(dashboardMapper.countBoards());
        s.setArticleTotal(dashboardMapper.countArticles());
        s.setCommentTotal(dashboardMapper.countComments());
        s.setArticleByBoard(dashboardMapper.articleByBoard(TOP_N));
        s.setArticleTrend(fillMonths(dashboardMapper.articleTrend(TREND_MONTHS)));

        s.setFileTotal(dashboardMapper.countFiles());
        s.setFileBytes(dashboardMapper.sumFileBytes());
        s.setFileByExtension(dashboardMapper.fileByExtension(TOP_N));
        s.setFileByScanStatus(dashboardMapper.fileByScanStatus());

        s.setContentStatus(dashboardMapper.contentStatusDistribution());
        return s;
    }

    /**
     * 최근 {@value #TREND_MONTHS} 개월을 빠짐없이 만들고, 조회 결과가 있는 달만 값을 채운다.
     * 없는 달은 0 — 데이터가 없다는 사실도 추세의 일부다.
     */
    private List<Bucket> fillMonths(List<Bucket> rows) {
        Map<String, Long> found = new LinkedHashMap<>();
        if (rows != null) {
            rows.forEach(b -> found.put(b.getLabel(), b.getValue()));
        }
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(TREND_MONTHS - 1L);
        List<Bucket> filled = new ArrayList<>(TREND_MONTHS);
        for (int i = 0; i < TREND_MONTHS; i++) {
            String key = start.plusMonths(i).format(MONTH);
            Bucket b = new Bucket();
            b.setLabel(key);
            b.setValue(found.getOrDefault(key, 0L));
            filled.add(b);
        }
        return filled;
    }
}
