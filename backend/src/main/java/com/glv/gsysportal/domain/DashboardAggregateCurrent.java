package com.glv.gsysportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Stage 5K, V34 migration: the atomic "current version" pointer - a single
 * row (singleton = true is its PK), updated with one UPSERT statement as
 * the very last step of a successful {@link DashboardRefreshRun}
 * (DashboardRefreshService). Dashboard/Brand List only ever read the run
 * this row points at - never a partially-published run, by construction
 * (no other code path writes this table).
 */
@Entity
@Table(name = "dashboard_aggregate_current")
@Getter
@Setter
@NoArgsConstructor
public class DashboardAggregateCurrent {

    @Id
    private Boolean singleton = Boolean.TRUE;

    @Column(name = "active_refresh_run_id", nullable = false)
    private Long activeRefreshRunId;

    @Column(name = "activated_at", nullable = false)
    private OffsetDateTime activatedAt;
}
