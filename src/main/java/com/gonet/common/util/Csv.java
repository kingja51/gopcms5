package com.gonet.common.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CSV 컬럼 파싱 — role_ids·required_roles·allowed_user_types 등 규약상 CSV 스냅샷 공통.
 *
 * <p>순서를 보존한다(LinkedHashSet) — 역할 CSV 는 sort_order 순서가 곧 표시 순서다.
 */
public final class Csv {

    private Csv() {
        // 유틸리티 클래스 인스턴스화 방지
    }

    /** 빈 값·공백 항목을 걸러낸 순서 보존 집합. null 은 빈 집합. */
    public static Set<String> toSet(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
