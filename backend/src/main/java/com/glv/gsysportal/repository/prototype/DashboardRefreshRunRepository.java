package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.DashboardRefreshRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface DashboardRefreshRunRepository extends JpaRepository<DashboardRefreshRun, Long> {

    Optional<DashboardRefreshRun> findByStatus(String status);

    List<DashboardRefreshRun> findAllByOrderByCalculationStartedAtDesc(Pageable pageable);

    /**
     * Restart-recovery (Stage 5J §16 "Application restart during refresh"):
     * a RUNNING row whose {@code calculationStartedAt} is older than the
     * configured timeout is treated as abandoned by an application
     * restart mid-refresh, not as a still-live refresh - superseded before
     * the next tick proceeds, never left to block single-flight forever.
     */
    @Modifying
    @Query("UPDATE DashboardRefreshRun r SET r.status = 'FAILED', "
            + "r.errorMessage = 'orphaned after restart', r.calculationCompletedAt = CURRENT_TIMESTAMP "
            + "WHERE r.status = 'RUNNING' AND r.calculationStartedAt < :cutoff")
    int failOrphanedRunsStartedBefore(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * Stage 5K §20 Retention ("無制限蓄積禁止"): deletes finished
     * (SUCCEEDED/FAILED) runs older than the configured retention window,
     * always excluding {@code excludeRunId} (the currently-active run,
     * whatever {@code dashboard_aggregate_current} points at right now -
     * never purged even if it happens to be old, since it is still in
     * live use). Cascades to that run's {@code dashboard_legacy_aggregate}/
     * {@code dashboard_brand_legacy_aggregate} rows via the V34 migration's
     * {@code ON DELETE CASCADE}.
     */
    @Modifying
    @Query("DELETE FROM DashboardRefreshRun r WHERE r.status <> 'RUNNING' "
            + "AND r.calculationStartedAt < :cutoff AND r.id <> :excludeRunId")
    int deleteFinishedRunsStartedBeforeExcept(@Param("cutoff") OffsetDateTime cutoff, @Param("excludeRunId") long excludeRunId);
}
