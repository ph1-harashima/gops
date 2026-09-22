package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.DashboardAggregateCurrent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DashboardAggregateCurrentRepository extends JpaRepository<DashboardAggregateCurrent, Boolean> {

    Optional<DashboardAggregateCurrent> findBySingleton(Boolean singleton);

    /**
     * Stage 5J §7's "one statement, one transaction" atomic publication
     * step - the ONLY write path this table ever has (DashboardRefreshService,
     * called exclusively as the last step of a SUCCEEDED refresh). A native
     * upsert rather than JPA save/merge so the single-row activation is
     * provably one round trip, matching the "Dashboard reads flip to the
     * new data atomically at this instant" guarantee.
     */
    @Modifying
    @Query(value = "INSERT INTO dashboard_aggregate_current (singleton, active_refresh_run_id, activated_at) "
            + "VALUES (true, :refreshRunId, now()) "
            + "ON CONFLICT (singleton) DO UPDATE SET active_refresh_run_id = :refreshRunId, activated_at = now()",
            nativeQuery = true)
    void activate(@Param("refreshRunId") Long refreshRunId);
}
