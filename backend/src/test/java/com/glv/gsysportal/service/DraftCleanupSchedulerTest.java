package com.glv.gsysportal.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Draft Lifecycle Cleanup Audit §8/§14 (N/O): pure Mockito unit test (same
 * idiom {@code DashboardRefreshServiceTest} already established) - no
 * Spring context, no DB, exercising only the enabled-flag gate and the
 * per-item error isolation §6 requires.
 */
class DraftCleanupSchedulerTest {

    /** N: cleanup scheduler disabled -> 自動cleanupされない。 */
    @Test
    void scheduledCleanupDoesNothingWhenDisabled() {
        DraftCleanupService service = mock(DraftCleanupService.class);
        DraftCleanupScheduler scheduler = new DraftCleanupScheduler(service, false);

        scheduler.scheduledCleanup();

        verify(service, never()).currentCutoff();
        verify(service, never()).findCandidateIds(any());
        verify(service, never()).cleanupOneIfStillEligible(anyLong(), any());
    }

    /** O: cleanup scheduler enabled -> 対象だけ処理される。 */
    @Test
    void scheduledCleanupProcessesEveryCandidateWhenEnabled() {
        DraftCleanupService service = mock(DraftCleanupService.class);
        OffsetDateTime cutoff = OffsetDateTime.now();
        when(service.currentCutoff()).thenReturn(cutoff);
        when(service.findCandidateIds(cutoff)).thenReturn(List.of(1L, 2L));
        when(service.cleanupOneIfStillEligible(1L, cutoff)).thenReturn(DraftCleanupService.CleanupOutcome.DELETED);
        when(service.cleanupOneIfStillEligible(2L, cutoff)).thenReturn(DraftCleanupService.CleanupOutcome.SKIPPED_HAS_ACTIVITY);
        DraftCleanupScheduler scheduler = new DraftCleanupScheduler(service, true);

        scheduler.scheduledCleanup();

        verify(service, times(1)).cleanupOneIfStillEligible(eq(1L), eq(cutoff));
        verify(service, times(1)).cleanupOneIfStillEligible(eq(2L), eq(cutoff));
    }

    /** §6: succeeded/skipped/failed are tallied separately and correctly. */
    @Test
    void runCleanupTalliesSucceededSkippedAndFailedSeparately() {
        DraftCleanupService service = mock(DraftCleanupService.class);
        OffsetDateTime cutoff = OffsetDateTime.now();
        when(service.currentCutoff()).thenReturn(cutoff);
        when(service.findCandidateIds(cutoff)).thenReturn(List.of(1L, 2L, 3L));
        when(service.cleanupOneIfStillEligible(1L, cutoff)).thenReturn(DraftCleanupService.CleanupOutcome.DELETED);
        when(service.cleanupOneIfStillEligible(2L, cutoff)).thenReturn(DraftCleanupService.CleanupOutcome.SKIPPED_TOO_RECENT);
        when(service.cleanupOneIfStillEligible(3L, cutoff)).thenThrow(new RuntimeException("simulated DB error"));
        DraftCleanupScheduler scheduler = new DraftCleanupScheduler(service, true);

        DraftCleanupScheduler.CleanupRunSummary summary = scheduler.runCleanup();

        assertEquals(3, summary.candidateCount());
        assertEquals(1, summary.succeeded());
        assertEquals(1, summary.skipped());
        assertEquals(1, summary.failed());
        assertEquals(List.of(3L), summary.failedIds());
    }

    /** §6: one failing Draft (id=2 here) must never stop the batch - id=3
     * is still attempted afterward. */
    @Test
    void oneFailureDoesNotStopProcessingOtherCandidates() {
        DraftCleanupService service = mock(DraftCleanupService.class);
        OffsetDateTime cutoff = OffsetDateTime.now();
        when(service.currentCutoff()).thenReturn(cutoff);
        when(service.findCandidateIds(cutoff)).thenReturn(List.of(1L, 2L, 3L));
        when(service.cleanupOneIfStillEligible(1L, cutoff)).thenReturn(DraftCleanupService.CleanupOutcome.DELETED);
        when(service.cleanupOneIfStillEligible(2L, cutoff)).thenThrow(new RuntimeException("simulated failure"));
        when(service.cleanupOneIfStillEligible(3L, cutoff)).thenReturn(DraftCleanupService.CleanupOutcome.DELETED);
        DraftCleanupScheduler scheduler = new DraftCleanupScheduler(service, true);

        DraftCleanupScheduler.CleanupRunSummary summary = scheduler.runCleanup();

        verify(service, times(1)).cleanupOneIfStillEligible(eq(3L), eq(cutoff));
        assertEquals(2, summary.succeeded());
        assertEquals(1, summary.failed());
    }
}
