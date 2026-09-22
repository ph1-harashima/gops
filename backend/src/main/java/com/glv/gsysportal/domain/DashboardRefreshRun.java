package com.glv.gsysportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Stage 5K (docs/real-data-audit/gops-stage5k-dashboard-read-model-implementation.md,
 * V34 migration): one row per Dashboard Read Model Refresh attempt - both
 * the history and the source of {@link DashboardAggregateCurrent}'s
 * "current version" pointer. Never updated after {@link #status} leaves
 * RUNNING except by the same run that created it (no other write path
 * exists) - see {@code DashboardRefreshService}.
 */
@Entity
@Table(name = "dashboard_refresh_run")
@Getter
@Setter
@NoArgsConstructor
public class DashboardRefreshRun {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String TRIGGER_SCHEDULED = "SCHEDULED";
    public static final String TRIGGER_STARTUP = "STARTUP";
    public static final String TRIGGER_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    @Column(name = "calculation_started_at", nullable = false)
    private OffsetDateTime calculationStartedAt;

    @Column(name = "calculation_completed_at")
    private OffsetDateTime calculationCompletedAt;

    @Column(name = "evaluated_item_count")
    private Integer evaluatedItemCount;

    @Column(name = "candidate_count")
    private Integer candidateCount;

    /** RC-D observability (Stage 5J §16/§17; Addendum §11 closed RC-D on the
     * finding that every known [AJ]-pattern FORMULA_11 row is soft-deleted
     * and therefore already excluded from this population at SQL level) -
     * expected to stay at or near 0 in steady state; not a hard gate. */
    @Column(name = "formula_error_count", nullable = false)
    private int formulaErrorCount = 0;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "initiated_by", length = 50)
    private String initiatedBy;
}
