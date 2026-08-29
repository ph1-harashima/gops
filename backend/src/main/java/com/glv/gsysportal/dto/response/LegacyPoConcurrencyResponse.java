package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 7-C6 8章/10章: {@code GET /api/orders/{id}/official-po/concurrency}
 * result. {@code result} is an axis entirely separate from
 * {@link OfficialPoIntegrationResponse#status()} (Integration Workflow) and
 * {@link FulfillmentView#fulfillmentStatus()} (physical delivery progress) -
 * 7-C6 8章 "Integration Statusとは別軸。FAILEDに混ぜない" / 7-C6 17章
 * "Fulfillment表示のためにBaseline Snapshotを使わない".
 */
public record LegacyPoConcurrencyResponse(
        Long orderId,
        String officialPoNo,
        String result,
        Integer baselineRevisionNo,
        String baselineFingerprint,
        String capturedBy,
        OffsetDateTime capturedAt,
        List<LegacyPoDiffEntry> diffs) {

    /** No Baseline exists to diverge from - officialPoNo is null (7-C6 5章). */
    public static final String RESULT_NOT_LINKED = "NOT_LINKED";
    /** officialPoNo is set but no Legacy PO with that number exists (7-C6
     * 6章) - NOT automatically an error (could be a genuinely NEW PO not yet
     * imported into G-SYS). */
    public static final String RESULT_PO_NOT_FOUND = "PO_NOT_FOUND";
    /** officialPoNo is set and the Legacy PO exists, but no Baseline has ever
     * been captured for the Order's current target Revision (7-C6 16章 - a
     * Rev1 Baseline is never reused for Rev2). */
    public static final String RESULT_NOT_BASELINED = "NOT_BASELINED";
    /** Current Legacy Snapshot's fingerprint matches the Baseline exactly. */
    public static final String RESULT_UNCHANGED = "UNCHANGED";
    /** Current Legacy Snapshot differs from the Baseline - see {@link #diffs()}. */
    public static final String RESULT_CHANGED = "CHANGED";

    public static LegacyPoConcurrencyResponse notLinked(Long orderId) {
        return new LegacyPoConcurrencyResponse(orderId, null, RESULT_NOT_LINKED, null, null, null, null, List.of());
    }

    public static LegacyPoConcurrencyResponse poNotFound(Long orderId, String officialPoNo) {
        return new LegacyPoConcurrencyResponse(orderId, officialPoNo, RESULT_PO_NOT_FOUND, null, null, null, null, List.of());
    }

    public static LegacyPoConcurrencyResponse notBaselined(Long orderId, String officialPoNo) {
        return new LegacyPoConcurrencyResponse(orderId, officialPoNo, RESULT_NOT_BASELINED, null, null, null, null, List.of());
    }
}
