package com.gonet.logging.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.gonet.logging.retention.dto.RetentionTarget;
import com.gonet.logging.retention.dto.RetentionTarget.RetentionKey;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 파기 등록부.
 *
 * <p>여기가 조용히 틀어지면 <b>지우면 안 되는 것이 지워지거나</b>, 지워야 할 것이 영영
 * 남는다. 둘 다 배포 후에는 발견하기 어렵고 앞쪽은 되돌릴 수 없다. 그래서 정책을 문장이
 * 아니라 테스트로 고정한다.
 */
class RetentionTargetTest {

    private final List<RetentionTarget> logging = RetentionTarget.loggingTargets();

    @Test
    @DisplayName("파기 기록은 배치가 손대지 않는다 — 지웠다는 증빙이 사라진다")
    void piiPurgeIsExcluded() {
        RetentionTarget target = find("log_pii_purge");

        assertThat(target.purgeable()).isFalse();
        assertThat(target.retentionKey()).isEqualTo(RetentionKey.NONE);
        // 제외 사유가 없으면 다음 사람이 "빠뜨린 것" 으로 보고 되살린다
        assertThat(target.note()).isNotBlank();
    }

    @Test
    @DisplayName("개인정보 접근 이력은 다른 로그보다 오래 남는다(5년 vs 36개월)")
    void privacyLogKeepsLonger() {
        assertThat(find("log_privacy_access").retentionKey()).isEqualTo(RetentionKey.PRIVACY);
        assertThat(find("log_access").retentionKey()).isEqualTo(RetentionKey.LOG);
        assertThat(find("log_audit").retentionKey()).isEqualTo(RetentionKey.LOG);
        assertThat(find("log_security").retentionKey()).isEqualTo(RetentionKey.LOG);
    }

    @Test
    @DisplayName("통계 stat_* 는 등록부에 없다 — 영구 보존이라 파기 대상이 아니다")
    void statisticsAreNotListed() {
        assertThat(logging).noneMatch(t -> t.table().startsWith("stat_"));
        assertThat(RetentionTarget.primaryTargets()).noneMatch(t -> t.table().startsWith("stat_"));
    }

    @Test
    @DisplayName("로그인 이력은 primary 목록에 있다 — 다른 DataSource 를 탄다")
    void loginHistoryIsOnPrimarySide() {
        List<RetentionTarget> primary = RetentionTarget.primaryTargets();

        assertThat(primary).extracting(RetentionTarget::table)
                .containsExactly("tb_login_history");
        assertThat(primary.get(0).retentionKey()).isEqualTo(RetentionKey.LOGIN_HISTORY);
        // logging_db 목록에 섞여 있으면 없는 테이블을 지우려다 실패한다
        assertThat(logging).noneMatch(t -> t.table().startsWith("tb_"));
    }

    @Test
    @DisplayName("파기 대상은 기준 시각 컬럼을 반드시 갖는다")
    void purgeableTargetsHaveTimeColumn() {
        assertThat(logging).filteredOn(RetentionTarget::purgeable)
                .allSatisfy(t -> {
                    assertThat(t.timeColumn()).as(t.table()).isNotBlank();
                    assertThat(t.retentionKey()).as(t.table()).isNotEqualTo(RetentionKey.NONE);
                });
    }

    private RetentionTarget find(String table) {
        return logging.stream().filter(t -> t.table().equals(table)).findFirst()
                .orElseThrow(() -> new AssertionError("등록부에 없는 테이블: " + table));
    }
}
