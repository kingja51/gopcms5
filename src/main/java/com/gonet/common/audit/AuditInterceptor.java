package com.gonet.common.audit;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

/**
 * 감사컬럼 자동 세팅 MyBatis 플러그인 — 3개 SqlSessionFactory 공통 등록 (MyBatisConfig).
 *
 * <p>INSERT: created·updated 6종 세팅 / UPDATE: updated 3종 세팅.
 * 대상은 {@link Auditable} 상속 파라미터({@code @Param} 맵·컬렉션 내부 포함).
 * 이미 값이 있으면 덮어쓰지 않는다(마이그레이션·배치의 명시 값 존중).
 */
@Intercepts(@Signature(type = Executor.class, method = "update",
        args = {MappedStatement.class, Object.class}))
public class AuditInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        SqlCommandType commandType = statement.getSqlCommandType();

        if (parameter != null
                && (commandType == SqlCommandType.INSERT || commandType == SqlCommandType.UPDATE)) {
            for (Auditable auditable : extractAuditables(parameter)) {
                apply(auditable, commandType);
            }
        }
        return invocation.proceed();
    }

    private Set<Auditable> extractAuditables(Object parameter) {
        Set<Auditable> found = new LinkedHashSet<>();
        collect(parameter, found);
        return found;
    }

    private void collect(Object candidate, Set<Auditable> found) {
        if (candidate instanceof Auditable auditable) {
            found.add(auditable);
        } else if (candidate instanceof Map<?, ?> map) {          // @Param 파라미터 맵
            map.values().forEach(value -> collectShallow(value, found));
        } else if (candidate instanceof Collection<?> collection) { // 배치 insert 리스트
            collection.forEach(value -> collectShallow(value, found));
        }
    }

    /** 맵/컬렉션 1단계 내부만 — 임의 깊이 재귀로 인한 순환 참조 방지. */
    private void collectShallow(Object candidate, Set<Auditable> found) {
        if (candidate instanceof Auditable auditable) {
            found.add(auditable);
        } else if (candidate instanceof Collection<?> collection) {
            collection.forEach(value -> {
                if (value instanceof Auditable auditable) {
                    found.add(auditable);
                }
            });
        }
    }

    private void apply(Auditable auditable, SqlCommandType commandType) {
        LocalDateTime now = LocalDateTime.now();
        String userId = AuditorContext.currentUserId();
        String ip = AuditorContext.currentIp();

        if (commandType == SqlCommandType.INSERT) {
            if (auditable.getCreatedBy() == null) {
                auditable.setCreatedBy(userId);
            }
            if (auditable.getCreatedIp() == null) {
                auditable.setCreatedIp(ip);
            }
            if (auditable.getCreatedAt() == null) {
                auditable.setCreatedAt(now);
            }
        }
        if (auditable.getUpdatedBy() == null) {
            auditable.setUpdatedBy(userId);
        }
        if (auditable.getUpdatedIp() == null) {
            auditable.setUpdatedIp(ip);
        }
        if (auditable.getUpdatedAt() == null) {
            auditable.setUpdatedAt(now);
        }
    }
}
