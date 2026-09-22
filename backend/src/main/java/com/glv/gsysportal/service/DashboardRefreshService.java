package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.DashboardBrandLegacyAggregate;
import com.glv.gsysportal.domain.DashboardLegacyAggregate;
import com.glv.gsysportal.domain.DashboardRefreshRun;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository.DashboardStockAggregateRow;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import com.glv.gsysportal.repository.prototype.DashboardAggregateCurrentRepository;
import com.glv.gsysportal.repository.prototype.DashboardBrandLegacyAggregateRepository;
import com.glv.gsysportal.repository.prototype.DashboardLegacyAggregateRepository;
import com.glv.gsysportal.repository.prototype.DashboardRefreshRunRepository;
import com.glv.gsysportal.service.SupplierRegionClassificationResolutionService.RegionClassificationLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 5K (docs/real-data-audit/gops-stage5k-dashboard-read-model-implementation.md):
 * runs the Dashboard/Brand List Read Model Refresh - the exact same,
 * unmodified {@code calc4} pipeline {@link DashboardService} used to run
 * synchronously per-request (Stage 5E RC-A), now run on a schedule
 * ({@code DashboardRefreshScheduler}) or on demand ({@code
 * DashboardAdminController}'s Manual Refresh) and published to the Portal
 * DB Read Model tables (V34 migration) instead of returned directly.
 *
 * <p>Single-flight: a Postgres session-level advisory lock
 * ({@code pg_try_advisory_lock}), held on one dedicated JDBC {@link
 * Connection} borrowed directly from the Prototype {@link DataSource} for
 * the whole refresh duration - deliberately NOT a transaction-scoped
 * {@code pg_try_advisory_xact_lock}, because the RUNNING row (below) must
 * be visible in its own committed transaction while the refresh is still
 * in progress, and a FAILED row must still be written (a separate,
 * short transaction) even when the calc4 computation itself throws - both
 * of which are impossible if the entire refresh were one Portal
 * transaction. The {@code uq_dashboard_refresh_run_one_running} unique
 * index (V34) is the DB-level backstop if this lock is ever bypassed
 * (e.g. multiple application instances racing at the very first tick
 * before either has taken the lock).
 *
 * <p>Atomic publication: aggregate rows are written and the run is marked
 * SUCCEEDED, then {@link DashboardAggregateCurrentRepository#activate}
 * flips the single "current version" pointer - all in ONE transaction
 * (Stage 5J §7). Dashboard/Brand List never read {@code
 * dashboard_refresh_run}/{@code dashboard_*_aggregate} rows directly, only
 * via that pointer, so a reader can never observe a partially-published
 * Brand mix.
 */
@Service
public class DashboardRefreshService {

    private static final Logger log = LoggerFactory.getLogger(DashboardRefreshService.class);

    /** Arbitrary, fixed 64-bit advisory lock key for this one Refresh job -
     * no other feature in this codebase uses Postgres advisory locks
     * (checked), so collision with another lock key is not a concern. */
    private static final long ADVISORY_LOCK_KEY = 5300340012L;

    private final DataSource prototypeDataSource;
    private final PlatformTransactionManager prototypeTransactionManager;
    private final DashboardRefreshRunRepository refreshRunRepository;
    private final DashboardLegacyAggregateRepository legacyAggregateRepository;
    private final DashboardBrandLegacyAggregateRepository brandLegacyAggregateRepository;
    private final DashboardAggregateCurrentRepository aggregateCurrentRepository;
    private final LegacyStockReadRepository legacyStockReadRepository;
    private final RecommendedQtyCalculator recommendedQtyCalculator;
    private final SupplierRegionClassificationResolutionService regionResolutionService;
    private final long timeoutSeconds;
    private final long retentionDays;

    public DashboardRefreshService(DataSource prototypeDataSource,
                                    PlatformTransactionManager prototypeTransactionManager,
                                    DashboardRefreshRunRepository refreshRunRepository,
                                    DashboardLegacyAggregateRepository legacyAggregateRepository,
                                    DashboardBrandLegacyAggregateRepository brandLegacyAggregateRepository,
                                    DashboardAggregateCurrentRepository aggregateCurrentRepository,
                                    LegacyStockReadRepository legacyStockReadRepository,
                                    RecommendedQtyCalculator recommendedQtyCalculator,
                                    SupplierRegionClassificationResolutionService regionResolutionService,
                                    @Value("${app.dashboard.refresh.timeout-seconds:120}") long timeoutSeconds,
                                    @Value("${app.dashboard.refresh.retention-days:30}") long retentionDays) {
        this.prototypeDataSource = prototypeDataSource;
        this.prototypeTransactionManager = prototypeTransactionManager;
        this.refreshRunRepository = refreshRunRepository;
        this.legacyAggregateRepository = legacyAggregateRepository;
        this.brandLegacyAggregateRepository = brandLegacyAggregateRepository;
        this.aggregateCurrentRepository = aggregateCurrentRepository;
        this.legacyStockReadRepository = legacyStockReadRepository;
        this.recommendedQtyCalculator = recommendedQtyCalculator;
        this.regionResolutionService = regionResolutionService;
        this.timeoutSeconds = timeoutSeconds;
        this.retentionDays = retentionDays;
    }

    /**
     * Attempts one Refresh. Returns normally whether it ran, was skipped
     * (another refresh already in flight, §18 "skip / already running"),
     * or failed (failure is recorded, never thrown to the caller - a
     * Scheduler tick or Manual Refresh request must not propagate a
     * Legacy-side exception up into a 500).
     */
    public RefreshOutcome refresh(String triggerType, String initiatedBy) {
        reclaimOrphanedRuns();
        try (Connection lockConnection = prototypeDataSource.getConnection()) {
            if (!tryAcquireLock(lockConnection)) {
                log.info("refresh skipped/already-running trigger={}", triggerType);
                return RefreshOutcome.skippedOutcome();
            }
            try {
                return doRefresh(triggerType, initiatedBy);
            } finally {
                releaseLock(lockConnection);
            }
        } catch (SQLException e) {
            log.error("refresh failed to acquire Portal connection for advisory lock trigger={}", triggerType, e);
            return RefreshOutcome.failed(e.getMessage());
        }
    }

    private RefreshOutcome doRefresh(String triggerType, String initiatedBy) {
        long startNanos = System.nanoTime();
        Long runId = startRun(triggerType, initiatedBy);
        log.info("refresh start runId={} trigger={}", runId, triggerType);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<LegacyComputationResult> future = executor.submit(this::computeLegacyAggregates);
            LegacyComputationResult result;
            try {
                result = future.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException te) {
                // Best-effort only: this stops OUR thread from waiting
                // forever and lets the run be marked FAILED/retried on the
                // next tick (Stage 5J §16/§19 "no infinite wait"). It
                // cannot force-cancel an already-issued Legacy SELECT
                // in-flight on the worker thread/JDBC connection - see
                // "Known Limitations" in the Stage 5K doc.
                future.cancel(true);
                throw new IllegalStateException("Legacy computation exceeded timeout of " + timeoutSeconds + "s", te);
            } catch (java.util.concurrent.ExecutionException ee) {
                Throwable cause = ee.getCause();
                throw (cause instanceof RuntimeException re) ? re : new IllegalStateException(cause);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for Legacy computation", ie);
            }

            publishSuccess(runId, result.stockAggregateByBrand(), result.candidateCountByBrand(),
                    result.brandNames(), result.evaluatedItemCount(), result.formulaErrorCount());

            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            int overallCandidateCount = result.candidateCountByBrand().values().stream().mapToInt(Integer::intValue).sum();
            log.info("refresh success runId={} durationMs={} evaluatedItemCount={} candidateCount={} "
                            + "formulaErrorCount={} publishedVersion={}",
                    runId, durationMs, result.evaluatedItemCount(), overallCandidateCount, result.formulaErrorCount(), runId);
            return RefreshOutcome.succeeded(runId);
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.error("refresh failure runId={} durationMs={}", runId, durationMs, e);
            markFailed(runId, e);
            return RefreshOutcome.failed(e.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    /** Runs on the dedicated worker thread (see {@link #doRefresh}'s
     * timeout wrapper) - pure Legacy reads (own short-lived {@code
     * legacyTransactionManager}-scoped transactions per repository call,
     * same as {@code DashboardService} always used) plus the unmodified
     * calc4 evaluation loop. No Portal write happens here. */
    private LegacyComputationResult computeLegacyAggregates() {
        Map<String, int[]> stockAggregateByBrand = new HashMap<>(); // brand -> [outOfStock, longTermOutOfStock]
        List<DashboardStockAggregateRow> stockRows = legacyStockReadRepository.findDashboardStockAggregateByBrand();
        for (DashboardStockAggregateRow row : stockRows) {
            stockAggregateByBrand.put(row.brandCd(), new int[]{row.outOfStockCount(), row.longTermOutOfStockCount()});
        }

        List<LegacyStockRow> candidateInputs = legacyStockReadRepository.findDashboardCandidateInputs();
        RegionClassificationLookup regionLookup = regionResolutionService.loadAll();
        Map<String, Integer> candidateCountByBrand = new HashMap<>();
        int evaluatedItemCount = 0;
        int formulaErrorCount = 0;
        for (LegacyStockRow row : candidateInputs) {
            if (row.brandCd() == null) {
                continue;
            }
            evaluatedItemCount++;
            Integer recommendedQty = recommendedQtyCalculator.calc4(row, regionLookup);
            // Approximate RC-D observability signal (Stage 5J §16/§17,
            // Addendum §11): a non-blank FORMULA_11 that still produced no
            // recommendation is the closest proxy for "FormulaParser could
            // not evaluate this row" available without modifying
            // FormulaParser/RecommendedQtyCalculator to expose a more
            // precise signal - it can also be null for an unrelated reason
            // (e.g. an unconfirmed Domestic Rule), so this is a trend
            // indicator, not an exact count (not a hard gate, Stage 5K §16
            // Acceptance Criteria).
            if (recommendedQty == null && row.formula11() != null && !row.formula11().isBlank()) {
                formulaErrorCount++;
            }
            if (recommendedQty != null && recommendedQty > 0) {
                candidateCountByBrand.merge(row.brandCd(), 1, Integer::sum);
            }
        }
        Map<String, String> brandNames = legacyStockReadRepository.findAllBrandNames();
        return new LegacyComputationResult(stockAggregateByBrand, candidateCountByBrand, brandNames, evaluatedItemCount, formulaErrorCount);
    }

    private record LegacyComputationResult(Map<String, int[]> stockAggregateByBrand,
                                             Map<String, Integer> candidateCountByBrand,
                                             Map<String, String> brandNames,
                                             int evaluatedItemCount,
                                             int formulaErrorCount) {
    }

    /** Stage 5J §16 "Application restart during refresh": a RUNNING row
     * older than the timeout is orphaned (the advisory lock itself is
     * already safely released on connection close/app restart - this only
     * cleans up the row so it stops blocking the DB-level single-flight
     * backstop, {@code uq_dashboard_refresh_run_one_running}). */
    private void reclaimOrphanedRuns() {
        TransactionTemplate tx = new TransactionTemplate(prototypeTransactionManager);
        Integer reclaimed = tx.execute(status ->
                refreshRunRepository.failOrphanedRunsStartedBefore(OffsetDateTime.now().minusSeconds(timeoutSeconds)));
        if (reclaimed != null && reclaimed > 0) {
            log.warn("reclaimed {} orphaned RUNNING refresh run(s)", reclaimed);
        }
    }

    private Long startRun(String triggerType, String initiatedBy) {
        TransactionTemplate tx = new TransactionTemplate(prototypeTransactionManager);
        return tx.execute(status -> {
            DashboardRefreshRun run = new DashboardRefreshRun();
            run.setStatus(DashboardRefreshRun.STATUS_RUNNING);
            run.setTriggerType(triggerType);
            run.setCalculationStartedAt(OffsetDateTime.now());
            run.setInitiatedBy(initiatedBy);
            return refreshRunRepository.saveAndFlush(run).getId();
        });
    }

    /** Stage 5J §7 steps 2-3, one transaction: write the Overall + Brand
     * aggregate rows (not yet visible to any Dashboard read - nothing
     * joins through to them except the pointer table), mark the run
     * SUCCEEDED, then flip {@code dashboard_aggregate_current} - all
     * committed together. */
    private void publishSuccess(Long runId, Map<String, int[]> stockAggregateByBrand,
                                 Map<String, Integer> candidateCountByBrand, Map<String, String> brandNames,
                                 int evaluatedItemCount, int formulaErrorCount) {
        TransactionTemplate tx = new TransactionTemplate(prototypeTransactionManager);
        tx.executeWithoutResult(status -> {
            // Stage 5K-R (docs/real-data-audit/
            // gops-stage5kr-null-brand-remediation-and-final-verification.md):
            // DashboardStockAggregateQuery.sql's GROUP BY brand_cd legitimately
            // produces a null-keyed row whenever a non-deleted item has no
            // Brand assigned (confirmed on the real Production Snapshot: 10 of
            // ~47,280 items) - a null brandCode must never become a
            // dashboard_brand_legacy_aggregate row (V34's own NOT NULL
            // constraint), matching the pre-Stage-5K live
            // DashboardService.buildBrandRows' own "if (row.brandCd() != null)"
            // guard exactly. candidateCountByBrand never contains a null key
            // in the first place (computeLegacyAggregates already skips a null
            // brandCd row before it is ever added there, mirroring the OLD
            // computeCandidateCountByBrand's own equivalent skip) - only
            // stockAggregateByBrand needs this guard.
            java.util.Set<String> allBrandCodes = new java.util.LinkedHashSet<>();
            allBrandCodes.addAll(stockAggregateByBrand.keySet());
            allBrandCodes.addAll(candidateCountByBrand.keySet());
            allBrandCodes.remove(null);

            int overallCandidateCount = 0;
            int overallOutOfStock = 0;
            int overallLongTermOutOfStock = 0;
            List<DashboardBrandLegacyAggregate> brandRows = new ArrayList<>();
            for (String brandCode : allBrandCodes) {
                int candidateCount = candidateCountByBrand.getOrDefault(brandCode, 0);
                int[] stock = stockAggregateByBrand.getOrDefault(brandCode, new int[]{0, 0});
                overallCandidateCount += candidateCount;
                overallOutOfStock += stock[0];
                overallLongTermOutOfStock += stock[1];
                String brandName = brandNames.getOrDefault(brandCode, brandCode);
                brandRows.add(new DashboardBrandLegacyAggregate(runId, brandCode, brandName, candidateCount, stock[0], stock[1]));
            }
            // The null-Brand items' stock contribution is still folded into
            // the Overall total (pre-Stage-5K parity - they simply never get
            // their own Brand row). candidateCountByBrand has no null-key
            // counterpart to add back (see comment above).
            int[] nullBrandStock = stockAggregateByBrand.get(null);
            if (nullBrandStock != null) {
                overallOutOfStock += nullBrandStock[0];
                overallLongTermOutOfStock += nullBrandStock[1];
            }
            brandLegacyAggregateRepository.saveAll(brandRows);
            legacyAggregateRepository.save(new DashboardLegacyAggregate(
                    runId, overallCandidateCount, overallOutOfStock, overallLongTermOutOfStock));

            DashboardRefreshRun run = refreshRunRepository.findById(runId).orElseThrow();
            run.setStatus(DashboardRefreshRun.STATUS_SUCCEEDED);
            run.setCalculationCompletedAt(OffsetDateTime.now());
            run.setEvaluatedItemCount(evaluatedItemCount);
            run.setCandidateCount(overallCandidateCount);
            run.setFormulaErrorCount(formulaErrorCount);
            refreshRunRepository.save(run);

            aggregateCurrentRepository.activate(runId);
        });
        purgeOldRuns(runId);
    }

    /** Stage 5K §20 Retention - runs after each successful publish (no
     * separate scheduled job needed at this cadence, §6). Always excludes
     * the run that was just activated. A purge failure is logged, not
     * propagated - it must never turn an otherwise-successful Refresh into
     * a reported failure. */
    private void purgeOldRuns(long activeRunId) {
        try {
            TransactionTemplate tx = new TransactionTemplate(prototypeTransactionManager);
            Integer deleted = tx.execute(status -> refreshRunRepository.deleteFinishedRunsStartedBeforeExcept(
                    OffsetDateTime.now().minusDays(retentionDays), activeRunId));
            if (deleted != null && deleted > 0) {
                log.info("purged {} refresh_run row(s) older than {} days", deleted, retentionDays);
            }
        } catch (RuntimeException e) {
            log.warn("refresh retention purge failed - not fatal to the Refresh that just succeeded", e);
        }
    }

    /** Stage 5J §16 "Refresh failure": {@code dashboard_aggregate_current}
     * is never touched here - the previous SUCCEEDED run stays active. */
    private void markFailed(Long runId, Exception cause) {
        TransactionTemplate tx = new TransactionTemplate(prototypeTransactionManager);
        tx.executeWithoutResult(status -> {
            DashboardRefreshRun run = refreshRunRepository.findById(runId).orElseThrow();
            run.setStatus(DashboardRefreshRun.STATUS_FAILED);
            run.setCalculationCompletedAt(OffsetDateTime.now());
            String message = cause.getMessage();
            run.setErrorMessage(message == null ? cause.getClass().getName() : message);
            refreshRunRepository.save(run);
        });
    }

    private boolean tryAcquireLock(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, ADVISORY_LOCK_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private void releaseLock(Connection connection) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            ps.setLong(1, ADVISORY_LOCK_KEY);
            ps.executeQuery();
        } catch (SQLException e) {
            // The lock is session-scoped - closing the Connection (caller's
            // try-with-resources) releases it regardless if this explicit
            // unlock itself fails, so this is logged, not rethrown.
            log.warn("failed to explicitly release advisory lock - it will still be released when the connection closes", e);
        }
    }

    public record RefreshOutcome(boolean ran, boolean skipped, Long refreshRunId, String errorMessage) {
        static RefreshOutcome succeeded(Long runId) {
            return new RefreshOutcome(true, false, runId, null);
        }

        static RefreshOutcome skippedOutcome() {
            return new RefreshOutcome(false, true, null, null);
        }

        static RefreshOutcome failed(String errorMessage) {
            return new RefreshOutcome(true, false, null, errorMessage);
        }
    }
}
