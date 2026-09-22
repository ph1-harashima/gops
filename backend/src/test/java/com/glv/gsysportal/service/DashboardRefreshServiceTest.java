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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage 5K: pure Mockito unit test (same idiom {@code
 * EmailSendServiceRetryTest}/{@code MailTemplateResolutionServiceTest}
 * already established in this codebase) exercising {@link
 * DashboardRefreshService}'s three core guarantees without a real
 * Legacy/Portal DB - single-flight, last-known-good on failure, and the
 * Overall-equals-sum-of-Brand invariant on success (Stage 5K §9/§24/§28).
 * {@link DashboardServiceIntegrationTest} separately covers the real,
 * end-to-end path against the running Legacy Demo/Portal containers.
 */
class DashboardRefreshServiceTest {

    private DataSource dataSource;
    private Connection lockConnection;
    private PlatformTransactionManager transactionManager;
    private DashboardRefreshRunRepository refreshRunRepository;
    private DashboardLegacyAggregateRepository legacyAggregateRepository;
    private DashboardBrandLegacyAggregateRepository brandLegacyAggregateRepository;
    private DashboardAggregateCurrentRepository aggregateCurrentRepository;
    private LegacyStockReadRepository legacyStockReadRepository;
    private RecommendedQtyCalculator recommendedQtyCalculator;
    private SupplierRegionClassificationResolutionService regionResolutionService;
    private DashboardRefreshService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        lockConnection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(lockConnection);
        stubAdvisoryLockAcquired(true);

        // TransactionTemplate just needs a PlatformTransactionManager that
        // hands back a usable TransactionStatus and no-ops commit/rollback -
        // the repositories inside each callback are themselves mocked, so
        // no real transaction/connection is needed for this test's scope.
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(inv -> new DefaultTransactionStatus(null, true, true, false, false, null));

        refreshRunRepository = mock(DashboardRefreshRunRepository.class);
        legacyAggregateRepository = mock(DashboardLegacyAggregateRepository.class);
        brandLegacyAggregateRepository = mock(DashboardBrandLegacyAggregateRepository.class);
        aggregateCurrentRepository = mock(DashboardAggregateCurrentRepository.class);
        legacyStockReadRepository = mock(LegacyStockReadRepository.class);
        recommendedQtyCalculator = mock(RecommendedQtyCalculator.class);
        regionResolutionService = mock(SupplierRegionClassificationResolutionService.class);

        when(refreshRunRepository.saveAndFlush(any(DashboardRefreshRun.class))).thenAnswer(inv -> {
            DashboardRefreshRun run = inv.getArgument(0);
            run.setId(1L);
            return run;
        });
        when(refreshRunRepository.findById(1L)).thenAnswer(inv -> {
            DashboardRefreshRun run = new DashboardRefreshRun();
            run.setId(1L);
            run.setStatus(DashboardRefreshRun.STATUS_RUNNING);
            return java.util.Optional.of(run);
        });

        service = new DashboardRefreshService(dataSource, transactionManager, refreshRunRepository,
                legacyAggregateRepository, brandLegacyAggregateRepository, aggregateCurrentRepository,
                legacyStockReadRepository, recommendedQtyCalculator, regionResolutionService, 30L, 30L);
    }

    private void stubAdvisoryLockAcquired(boolean acquired) throws Exception {
        PreparedStatement lockStatement = mock(PreparedStatement.class);
        ResultSet lockResultSet = mock(ResultSet.class);
        when(lockResultSet.next()).thenReturn(true);
        when(lockResultSet.getBoolean(1)).thenReturn(acquired);
        when(lockStatement.executeQuery()).thenReturn(lockResultSet);
        when(lockConnection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(lockStatement);

        PreparedStatement unlockStatement = mock(PreparedStatement.class);
        when(unlockStatement.executeQuery()).thenReturn(mock(ResultSet.class));
        when(lockConnection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(unlockStatement);
    }

    /** Stage 5J §18: "Refresh A実行中にRefresh Bが来た場合: skip / already
     * running、等、安全な結果とする" - when the advisory lock is not
     * acquired, no run is even started, no Legacy query is issued. */
    @Test
    void refreshSkipsWhenAdvisoryLockNotAcquired() throws Exception {
        stubAdvisoryLockAcquired(false);

        DashboardRefreshService.RefreshOutcome outcome = service.refresh(DashboardRefreshRun.TRIGGER_SCHEDULED, null);

        assertTrue(outcome.skipped());
        verify(refreshRunRepository, never()).saveAndFlush(any());
        verify(legacyStockReadRepository, never()).findDashboardCandidateInputs();
    }

    /** Stage 5J §7/§16 "last-known-good": a Legacy-side failure marks the
     * run FAILED and MUST NOT touch {@code dashboard_aggregate_current} -
     * the previous successful run stays active. */
    @Test
    void refreshMarksFailedAndNeverActivatesPointerOnLegacyFailure() {
        when(legacyStockReadRepository.findDashboardStockAggregateByBrand())
                .thenThrow(new RuntimeException("Legacy connection lost (simulated)"));

        DashboardRefreshService.RefreshOutcome outcome = service.refresh(DashboardRefreshRun.TRIGGER_MANUAL, "admin01");

        assertFalse(outcome.skipped());
        ArgumentCaptor<DashboardRefreshRun> savedRun = ArgumentCaptor.forClass(DashboardRefreshRun.class);
        verify(refreshRunRepository, times(1)).save(savedRun.capture()); // markFailed's write (startRun itself uses saveAndFlush)
        assertEquals(DashboardRefreshRun.STATUS_FAILED, savedRun.getValue().getStatus());
        verify(aggregateCurrentRepository, never()).activate(anyLong());
    }

    /** Stage 5J §9's Overall-independent-row decision only holds meaning
     * if Overall is actually computed as the sum of the same per-Brand
     * numbers being published - this guards that invariant directly at
     * the point of construction (Stage 5K §9 "Overall = Brand aggregates
     * となるべきKPIはautomated testで保証"). */
    @Test
    void refreshPublishesOverallAsSumOfBrandRowsAndActivatesPointerOnSuccess() {
        when(legacyStockReadRepository.findDashboardStockAggregateByBrand()).thenReturn(List.of(
                new DashboardStockAggregateRow("BR_A", 3, 1),
                new DashboardStockAggregateRow("BR_B", 2, 0)
        ));
        LegacyStockRow rowA1 = candidateRow("SKU-A1", "BR_A", "1");
        LegacyStockRow rowA2 = candidateRow("SKU-A2", "BR_A", "1");
        LegacyStockRow rowB1 = candidateRow("SKU-B1", "BR_B", "1");
        when(legacyStockReadRepository.findDashboardCandidateInputs()).thenReturn(List.of(rowA1, rowA2, rowB1));
        when(legacyStockReadRepository.findAllBrandNames()).thenReturn(Map.of("BR_A", "Brand A", "BR_B", "Brand B"));
        RegionClassificationLookup lookup = mock(RegionClassificationLookup.class);
        when(regionResolutionService.loadAll()).thenReturn(lookup);
        when(recommendedQtyCalculator.calc4(eq(rowA1), any(RegionClassificationLookup.class))).thenReturn(5);
        when(recommendedQtyCalculator.calc4(eq(rowA2), any(RegionClassificationLookup.class))).thenReturn(0); // not a candidate
        when(recommendedQtyCalculator.calc4(eq(rowB1), any(RegionClassificationLookup.class))).thenReturn(2);

        DashboardRefreshService.RefreshOutcome outcome = service.refresh(DashboardRefreshRun.TRIGGER_STARTUP, null);

        assertFalse(outcome.skipped());
        assertEquals(1L, outcome.refreshRunId());

        ArgumentCaptor<List<DashboardBrandLegacyAggregate>> brandRowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(brandLegacyAggregateRepository).saveAll(brandRowsCaptor.capture());
        List<DashboardBrandLegacyAggregate> brandRows = brandRowsCaptor.getValue();
        int candidateSum = brandRows.stream().mapToInt(DashboardBrandLegacyAggregate::getCandidateCount).sum();
        int outOfStockSum = brandRows.stream().mapToInt(DashboardBrandLegacyAggregate::getOutOfStockCount).sum();

        ArgumentCaptor<DashboardLegacyAggregate> overallCaptor = ArgumentCaptor.forClass(DashboardLegacyAggregate.class);
        verify(legacyAggregateRepository).save(overallCaptor.capture());
        assertEquals(candidateSum, overallCaptor.getValue().getCandidateCount(), "Overall candidateCount must equal the sum of Brand rows");
        assertEquals(2, overallCaptor.getValue().getCandidateCount(), "BR_A:1 (rowA1 only, rowA2's calc4=0 is not a candidate) + BR_B:1");
        assertEquals(outOfStockSum, overallCaptor.getValue().getOutOfStockCount(), "Overall outOfStockCount must equal the sum of Brand rows");

        verify(aggregateCurrentRepository).activate(1L);
    }

    /**
     * Stage 5K-R (docs/real-data-audit/
     * gops-stage5kr-null-brand-remediation-and-final-verification.md):
     * reproduces the Production Snapshot defect Stage 5K-V found - 10 of
     * the real ~47,280 non-deleted items have {@code brand_cd IS NULL}.
     * {@code DashboardStockAggregateQuery.sql}'s {@code GROUP BY brand_cd}
     * legitimately produces a {@code null}-keyed row for these, which the
     * pre-Stage-5K live {@code DashboardService.buildBrandRows} explicitly
     * skipped ({@code if (row.brandCd() != null)}) when building the
     * per-Brand breakdown, while still folding the row's stock counts into
     * the plain Overall sum. This test fails against the pre-fix Stage 5K
     * code (which passes {@code null} straight through into a
     * {@code DashboardBrandLegacyAggregate} row - in a real Postgres
     * connection this hits {@code brand_code NOT NULL}, and even here at
     * the mock boundary the captured row list contains a null-brandCode
     * entry, which this test explicitly asserts against) and passes after
     * restoring that same guard.
     */
    @Test
    void refreshExcludesNullBrandCdFromBrandRowsButKeepsItsStockInOverall() {
        when(legacyStockReadRepository.findDashboardStockAggregateByBrand()).thenReturn(List.of(
                new DashboardStockAggregateRow("BR_A", 3, 1),
                new DashboardStockAggregateRow("BR_B", 2, 0),
                new DashboardStockAggregateRow(null, 1, 1) // the 10 real null-brand_cd items, aggregated
        ));
        LegacyStockRow rowA1 = candidateRow("SKU-A1", "BR_A", "1");
        LegacyStockRow rowB1 = candidateRow("SKU-B1", "BR_B", "1");
        LegacyStockRow rowNull = candidateRow("SKU-N1", null, "1"); // must never reach calc4/candidateCountByBrand as a key
        when(legacyStockReadRepository.findDashboardCandidateInputs()).thenReturn(List.of(rowA1, rowB1, rowNull));
        when(legacyStockReadRepository.findAllBrandNames()).thenReturn(Map.of("BR_A", "Brand A", "BR_B", "Brand B"));
        RegionClassificationLookup lookup = mock(RegionClassificationLookup.class);
        when(regionResolutionService.loadAll()).thenReturn(lookup);
        when(recommendedQtyCalculator.calc4(eq(rowA1), any(RegionClassificationLookup.class))).thenReturn(5);
        when(recommendedQtyCalculator.calc4(eq(rowB1), any(RegionClassificationLookup.class))).thenReturn(2);

        DashboardRefreshService.RefreshOutcome outcome = service.refresh(DashboardRefreshRun.TRIGGER_MANUAL, "admin01");

        assertFalse(outcome.skipped(), "a null brand_cd must never fail the Refresh (pre-Stage-5K parity)");
        assertEquals(1L, outcome.refreshRunId());

        ArgumentCaptor<List<DashboardBrandLegacyAggregate>> brandRowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(brandLegacyAggregateRepository).saveAll(brandRowsCaptor.capture());
        List<DashboardBrandLegacyAggregate> brandRows = brandRowsCaptor.getValue();

        assertEquals(2, brandRows.size(), "exactly BR_A and BR_B - no row for the null Brand");
        assertTrue(brandRows.stream().noneMatch(r -> r.getBrandCode() == null),
                "no Brand Aggregate row may ever have a null brandCode (V34's own NOT NULL constraint)");

        ArgumentCaptor<DashboardLegacyAggregate> overallCaptor = ArgumentCaptor.forClass(DashboardLegacyAggregate.class);
        verify(legacyAggregateRepository).save(overallCaptor.capture());
        // Overall = SUM(non-null Brand rows) + the null-brand stock contribution -
        // NOT simply SUM(Brand rows), since the null-brand row is deliberately
        // excluded from brandRows above (Stage 5K-R §11's explicit instruction:
        // do not force a stale "Overall == SUM(Brand)" invariant onto this
        // real-data shape).
        assertEquals(3 + 2 + 1, overallCaptor.getValue().getOutOfStockCount(),
                "Overall outOfStockCount includes BR_A(3) + BR_B(2) + the null-brand items(1)");
        assertEquals(1 + 0 + 1, overallCaptor.getValue().getLongTermOutOfStockCount(),
                "Overall longTermOutOfStockCount includes BR_A(1) + BR_B(0) + the null-brand items(1)");
        // candidateCount has no null-brand term to add back - computeLegacyAggregates
        // already skips a null brandCd row entirely before it ever reaches
        // candidateCountByBrand (matching the OLD computeCandidateCountByBrand's
        // own "if (row.brandCd() == null) continue;" precedent).
        assertEquals(2, overallCaptor.getValue().getCandidateCount(), "BR_A:1 + BR_B:1, the null-brand row was never a candidate at all");

        verify(aggregateCurrentRepository).activate(1L);
    }

    private static LegacyStockRow candidateRow(String itemCd, String brandCd, String formula11) {
        return new LegacyStockRow(itemCd, null, brandCd, null, null, null, null,
                0, 0, null, 0, 0, 0, formula11, null, null, null, "SUP_X", null, null, null, null);
    }
}
