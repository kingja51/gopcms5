package com.gonet.primary.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gonet.primary.dashboard.dto.DashboardStats.Bucket;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 차트 데이터를 뷰로 넘기기 위한 JSON 직렬화 — 템플릿에서 {@code @dashboardJson.buckets(...)} 로 쓴다.
 *
 * <p>인라인 {@code <script>} 로 데이터를 심지 않는 이유: CSP 가
 * {@code script-src 'self' 'nonce-…'} 라 nonce 없는 인라인은 실행되지 않고, 데이터마다
 * nonce 를 붙이는 것은 "인라인 스크립트 금지" 규약을 우회하는 것에 가깝다. 대신 값을
 * {@code data-*} 속성에 실어 보내고 외부 JS 가 읽는다 — Thymeleaf 가 속성값을 HTML 이스케이프
 * 하므로 따옴표·꺾쇠가 섞여도 마크업이 깨지지 않는다.
 */
@Component("dashboardJson")
@RequiredArgsConstructor
@Slf4j
public class DashboardJson {

    private final ObjectMapper objectMapper;

    /** 버킷 목록을 {@code [{"label":"2026-07","value":12}, …]} 로. 실패해도 화면은 살린다. */
    public String buckets(List<Bucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(buckets);
        } catch (JsonProcessingException e) {
            log.warn("차트 데이터 직렬화 실패 — 빈 차트로 대체한다: {}", e.toString());
            return "[]";
        }
    }
}
