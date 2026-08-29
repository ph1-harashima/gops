package com.glv.gsysportal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.LegacyPoBaseline;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.response.LegacyPoBaselineResponse;
import com.glv.gsysportal.dto.response.LegacyPoConcurrencyResponse;
import com.glv.gsysportal.dto.response.LegacyPoDiffEntry;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OfficialPoPreflightResult;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.IntegrationRequestRequiredException;
import com.glv.gsysportal.exception.LegacyPoNotFoundForBaselineException;
import com.glv.gsysportal.exception.OfficialPoNotLinkedException;
import com.glv.gsysportal.repository.legacy.LegacyPoConcurrencyReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyPoConcurrencyHeaderRow;
import com.glv.gsysportal.repository.legacy.row.LegacyPoConcurrencyLineRow;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.LegacyPoBaselineRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Phase 7-C6: Excel / Legacy Concurrency Control Foundation
 * (docs/excel-legacy-concurrency-control.md). Detects whether the G-SYS
 * Official PO Portal previously confirmed (a captured {@link LegacyPoBaseline})
 * still matches what Legacy holds right now - a Legacy READ ONLY Optimistic
 * Concurrency check, never a WRITE of any kind (7-C6 25章 Safety).
 *
 * <p>Deliberately does NOT decide who "wins" a detected conflict (7-C6 21章
 * "Conflict Resolution... 今回、実装しない。Conflict検出のみ") and never
 * performs any actual Legacy Handoff (7-C2B territory, 7-C6 11章's
 * {@link #canProceedToHandoff} is a pure Service-level READ helper for that
 * future Phase to call, not a Controller endpoint this Phase exposes).
 */
@Service
public class LegacyPoConcurrencyService {

    private final PortalOrderRepository portalOrderRepository;
    private final OfficialPoIntegrationRequestRepository integrationRequestRepository;
    private final LegacyPoBaselineRepository baselineRepository;
    private final LegacyPoConcurrencyReadRepository legacyReadRepository;
    private final AuditEventRepository auditEventRepository;
    private final OfficialPoIntegrationService officialPoIntegrationService;
    private final ObjectMapper objectMapper;

    public LegacyPoConcurrencyService(PortalOrderRepository portalOrderRepository,
                                       OfficialPoIntegrationRequestRepository integrationRequestRepository,
                                       LegacyPoBaselineRepository baselineRepository,
                                       LegacyPoConcurrencyReadRepository legacyReadRepository,
                                       AuditEventRepository auditEventRepository,
                                       OfficialPoIntegrationService officialPoIntegrationService,
                                       ObjectMapper objectMapper) {
        this.portalOrderRepository = portalOrderRepository;
        this.integrationRequestRepository = integrationRequestRepository;
        this.baselineRepository = baselineRepository;
        this.legacyReadRepository = legacyReadRepository;
        this.auditEventRepository = auditEventRepository;
        this.officialPoIntegrationService = officialPoIntegrationService;
        this.objectMapper = objectMapper;
    }

    /**
     * "G-SYS現在状態を基準として記録" (7-C6 9章/20章 - label deliberately
     * avoids "同期"/"上書き"/"更新", which would wrongly imply a Legacy
     * WRITE). Preconditions (7-C6 9章): {@code officialPoNo != null}, the
     * Legacy PO must actually exist, and an Integration Request must already
     * exist for the Order's current target Revision. Always appends a NEW
     * {@link LegacyPoBaseline} row (re-capture is allowed and expected -
     * that entity's own Javadoc) and always Audits
     * {@link AuditEvent#LEGACY_PO_BASELINE_CAPTURED}.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public LegacyPoBaselineResponse captureBaseline(Long orderId, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        if (order.getOfficialPoNo() == null) {
            throw new OfficialPoNotLinkedException(orderId);
        }
        int targetRevisionNo = OfficialPoIntegrationService.targetRevisionNo(order);
        integrationRequestRepository.findByPortalOrderIdAndRevisionNo(orderId, targetRevisionNo)
                .orElseThrow(() -> new IntegrationRequestRequiredException(orderId, targetRevisionNo));

        String officialPoNo = order.getOfficialPoNo();
        LegacyPoConcurrencyHeaderRow header = legacyReadRepository.findPoHeader(officialPoNo)
                .orElseThrow(() -> new LegacyPoNotFoundForBaselineException(officialPoNo));
        List<LegacyPoConcurrencyLineRow> lines = legacyReadRepository.findPoLines(officialPoNo);
        LegacyPoSnapshot snapshot = LegacyPoSnapshotFactory.build(header, lines);

        String snapshotJson = writeSnapshotJson(snapshot);
        String fingerprint = LegacyPoFingerprintCalculator.compute(snapshotJson);

        OffsetDateTime now = OffsetDateTime.now();
        LegacyPoBaseline baseline = new LegacyPoBaseline();
        baseline.setPortalOrderId(orderId);
        baseline.setRevisionNo(targetRevisionNo);
        baseline.setOfficialPoNo(officialPoNo);
        baseline.setFingerprint(fingerprint);
        baseline.setSnapshotJson(snapshotJson);
        baseline.setCapturedBy(performedBy);
        baseline.setCapturedAt(now);
        LegacyPoBaseline saved = baselineRepository.save(baseline);

        auditEventRepository.save(new AuditEvent(orderId, null, AuditEvent.LEGACY_PO_BASELINE_CAPTURED,
                null, null, fingerprint, performedBy, now));

        return new LegacyPoBaselineResponse(orderId, targetRevisionNo, officialPoNo, fingerprint, performedBy, now);
    }

    /**
     * "G-SYSとの差異を確認" (7-C6 10章/20章). Read-only against both DBs, any
     * authenticated user (7-C6 22章). Audits
     * {@link AuditEvent#LEGACY_PO_CHANGE_DETECTED} ONLY when {@code CHANGED}
     * is actually found (7-C6 18章) - never for UNCHANGED/NOT_LINKED/
     * PO_NOT_FOUND/NOT_BASELINED. Known, documented trade-off: since this
     * method has no "already notified" memory of its own, repeated page
     * views while a real divergence persists will each write their own
     * CHANGED Audit row rather than deduplicating - accepted as simpler and
     * still truthful, over adding a persisted "last notified" cursor this
     * Phase does not otherwise need (docs/excel-legacy-concurrency-control.md
     * 18章 records this explicitly).
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public LegacyPoConcurrencyResponse compare(Long orderId) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        if (order.getOfficialPoNo() == null) {
            return LegacyPoConcurrencyResponse.notLinked(orderId);
        }
        String officialPoNo = order.getOfficialPoNo();
        int targetRevisionNo = OfficialPoIntegrationService.targetRevisionNo(order);

        // PO_NOT_FOUND is checked BEFORE NOT_BASELINED (7-C6 6章/8章): if the
        // Legacy PO does not currently exist at all, that fact is more
        // fundamentally informative than "we simply haven't captured a
        // Snapshot yet" - and capturing a Baseline is impossible for a PO
        // that does not exist (captureBaseline's own precondition), so
        // NOT_BASELINED could never otherwise be resolved for this Order
        // anyway until the PO actually appears in Legacy.
        Optional<LegacyPoConcurrencyHeaderRow> headerOpt = legacyReadRepository.findPoHeader(officialPoNo);
        if (headerOpt.isEmpty()) {
            return LegacyPoConcurrencyResponse.poNotFound(orderId, officialPoNo);
        }

        Optional<LegacyPoBaseline> baselineOpt = baselineRepository
                .findFirstByPortalOrderIdAndRevisionNoOrderByCapturedAtDesc(orderId, targetRevisionNo);
        if (baselineOpt.isEmpty()) {
            return LegacyPoConcurrencyResponse.notBaselined(orderId, officialPoNo);
        }
        LegacyPoBaseline baseline = baselineOpt.get();

        List<LegacyPoConcurrencyLineRow> lines = legacyReadRepository.findPoLines(officialPoNo);
        LegacyPoSnapshot currentSnapshot = LegacyPoSnapshotFactory.build(headerOpt.get(), lines);
        String currentJson = writeSnapshotJson(currentSnapshot);
        String currentFingerprint = LegacyPoFingerprintCalculator.compute(currentJson);

        if (currentFingerprint.equals(baseline.getFingerprint())) {
            return new LegacyPoConcurrencyResponse(orderId, officialPoNo, LegacyPoConcurrencyResponse.RESULT_UNCHANGED,
                    baseline.getRevisionNo(), baseline.getFingerprint(), baseline.getCapturedBy(), baseline.getCapturedAt(), List.of());
        }

        LegacyPoSnapshot baselineSnapshot = readSnapshotJson(baseline.getSnapshotJson());
        List<LegacyPoDiffEntry> diffs = LegacyPoDiffEngine.diff(baselineSnapshot, currentSnapshot);

        OffsetDateTime now = OffsetDateTime.now();
        AuditEvent changeDetected = new AuditEvent(orderId, null, AuditEvent.LEGACY_PO_CHANGE_DETECTED,
                null, baseline.getFingerprint(), currentFingerprint, "SYSTEM", now);
        changeDetected.setNote(diffs.size() + " field(s) differ");
        auditEventRepository.save(changeDetected);

        return new LegacyPoConcurrencyResponse(orderId, officialPoNo, LegacyPoConcurrencyResponse.RESULT_CHANGED,
                baseline.getRevisionNo(), baseline.getFingerprint(), baseline.getCapturedBy(), baseline.getCapturedAt(), diffs);
    }

    /**
     * Phase 7-C6 11章: Service-level "may 7-C2B proceed to a real Legacy
     * Handoff" gate - Preflight not BLOCKED AND Concurrency UNCHANGED. No
     * Controller endpoint this Phase (7-C6 11章 "Serviceレベルで...判定を
     * 作れるなら実装する" - a callable Service method suffices); exercised
     * only by tests until 7-C2B actually calls it.
     */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public boolean canProceedToHandoff(Long orderId) {
        OfficialPoIntegrationResponse integration = officialPoIntegrationService.getIntegration(orderId);
        boolean preflightOk = integration.preflight() != null
                && !OfficialPoPreflightResult.RESULT_BLOCKED.equals(integration.preflight().result());
        LegacyPoConcurrencyResponse concurrency = compare(orderId);
        boolean concurrencyOk = LegacyPoConcurrencyResponse.RESULT_UNCHANGED.equals(concurrency.result());
        return preflightOk && concurrencyOk;
    }

    private String writeSnapshotJson(LegacyPoSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Legacy PO Snapshot", e);
        }
    }

    private LegacyPoSnapshot readSnapshotJson(String json) {
        try {
            return objectMapper.readValue(json, LegacyPoSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize Legacy PO Snapshot", e);
        }
    }
}
