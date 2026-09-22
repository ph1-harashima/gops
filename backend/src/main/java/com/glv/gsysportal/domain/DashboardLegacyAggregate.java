package com.glv.gsysportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stage 5K, V34 migration: the Overall Aggregate - one row per SUCCEEDED
 * {@link DashboardRefreshRun}, keyed by that run's id (not a separate
 * generated id - Stage 5J §5's "independent row, not summed from Brand
 * rows at read time" decision). Written once, at refresh publication time,
 * never updated afterward.
 */
@Entity
@Table(name = "dashboard_legacy_aggregate")
@Getter
@Setter
@NoArgsConstructor
public class DashboardLegacyAggregate {

    @Id
    @Column(name = "refresh_run_id")
    private Long refreshRunId;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @Column(name = "out_of_stock_count", nullable = false)
    private int outOfStockCount;

    @Column(name = "long_term_out_of_stock_count", nullable = false)
    private int longTermOutOfStockCount;

    public DashboardLegacyAggregate(Long refreshRunId, int candidateCount, int outOfStockCount, int longTermOutOfStockCount) {
        this.refreshRunId = refreshRunId;
        this.candidateCount = candidateCount;
        this.outOfStockCount = outOfStockCount;
        this.longTermOutOfStockCount = longTermOutOfStockCount;
    }
}
