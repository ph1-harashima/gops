package com.glv.gsysportal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OfficialPoPreflightIssue;
import com.glv.gsysportal.dto.response.OfficialPoPreflightResult;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.OrderNotApprovedException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 7-C2A: Official PO Integration Request Business Action
 * (docs/official-po-integration-detailed-design.md 13章/22.3章 step 1-4's
 * Foundation). Everything this Phase does is Portal-DB-local plus Legacy
 * READ ONLY Preflight - no File is written anywhere, no Legacy row is
 * touched (7-C2A 0章).
 */
@Service
public class OfficialPoIntegrationService {

    /** No Revision Workflow exists yet (7-C5 territory) - every Request this
     * Phase uses Revision 1 (7-C2A 5章). */
    private static final int CURRENT_REVISION_NO = 1;

    private final PortalOrderRepository portalOrderRepository;
    private final OfficialPoIntegrationRequestRepository integrationRequestRepository;
    private final AuditEventRepository auditEventRepository;
    private final OfficialPoPreflightService preflightService;
    private final ObjectMapper objectMapper;

    public OfficialPoIntegrationService(PortalOrderRepository portalOrderRepository,
                                         OfficialPoIntegrationRequestRepository integrationRequestRepository,
                                         AuditEventRepository auditEventRepository,
                                         OfficialPoPreflightService preflightService,
                                         ObjectMapper objectMapper) {
        this.portalOrderRepository = portalOrderRepository;
        this.integrationRequestRepository = integrationRequestRepository;
        this.auditEventRepository = auditEventRepository;
        this.preflightService = preflightService;
        this.objectMapper = objectMapper;
    }

    /**
     * "G-SYS連携準備" (7-C2A 14章): ensures an Integration Request row exists
     * for this Order's current revision (creating it on the first call only -
     * Idempotency key = portalOrderId+revisionNo, 7-C2A 4章), then (re-)runs
     * Preflight against Legacy READ ONLY every call, including repeat calls
     * on an already-existing Request - Master data can change between calls,
     * so a stale PASS should never be trusted indefinitely.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public OfficialPoIntegrationResponse requestIntegration(Long orderId, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        if (!PortalOrder.STATUS_APPROVED.equals(order.getStatus())) {
            throw new OrderNotApprovedException(orderId, order.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean isNewRequest = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(orderId, CURRENT_REVISION_NO).isEmpty();

        OfficialPoIntegrationRequest request = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(orderId, CURRENT_REVISION_NO)
                .orElseGet(() -> {
                    OfficialPoIntegrationRequest r = new OfficialPoIntegrationRequest();
                    r.setPortalOrderId(orderId);
                    r.setRevisionNo(CURRENT_REVISION_NO);
                    r.setRequestedBy(performedBy);
                    r.setRequestedAt(now);
                    r.setCreatedAt(now);
                    r.setUpdatedAt(now);
                    return r;
                });

        OfficialPoPreflightResult preflight = preflightService.run(order);
        request.setPreflightResult(preflight.result());
        request.setPreflightIssuesJson(writeIssuesJson(preflight.issues()));
        request.setPreflightAt(now);
        request.setUpdatedAt(now);

        OfficialPoIntegrationRequest saved = integrationRequestRepository.save(request);

        if (isNewRequest) {
            auditEventRepository.save(new AuditEvent(orderId, null,
                    AuditEvent.OFFICIAL_PO_INTEGRATION_REQUESTED, null, null, null, performedBy, now));
        }
        AuditEvent precheck = new AuditEvent(orderId, null,
                AuditEvent.PRECHECK_COMPLETED, null, null, preflight.result(), performedBy, now);
        precheck.setNote(summarize(preflight));
        auditEventRepository.save(precheck);

        return toResponse(saved);
    }

    /** Order Detail's "G-SYS正式PO連携" Section (7-C2A 13章) - read-only,
     * available to any authenticated user (unlike the request Action itself). */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public OfficialPoIntegrationResponse getIntegration(Long orderId) {
        if (!portalOrderRepository.existsById(orderId)) {
            throw new DraftNotFoundException(orderId);
        }
        return integrationRequestRepository.findFirstByPortalOrderIdOrderByRevisionNoDesc(orderId)
                .map(this::toResponse)
                .orElseGet(() -> OfficialPoIntegrationResponse.notRequested(orderId));
    }

    private OfficialPoIntegrationResponse toResponse(OfficialPoIntegrationRequest r) {
        OfficialPoPreflightResult preflight = r.getPreflightResult() == null ? null
                : new OfficialPoPreflightResult(r.getPreflightResult(), readIssuesJson(r.getPreflightIssuesJson()));
        return new OfficialPoIntegrationResponse(
                r.getPortalOrderId(), r.getRevisionNo(), r.getStatus(), r.getOfficialPoNo(),
                r.getRequestedBy(), r.getRequestedAt(), preflight,
                r.getGeneratedAt(), r.getSubmittedAt(), r.getConfirmedAt(), r.getFailedAt(),
                r.getErrorCode(), r.getErrorMessage()
        );
    }

    private static String summarize(OfficialPoPreflightResult preflight) {
        return preflight.result() + " (" + preflight.issues().size() + " issue(s))";
    }

    private String writeIssuesJson(List<OfficialPoPreflightIssue> issues) {
        try {
            return objectMapper.writeValueAsString(issues);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Preflight issues", e);
        }
    }

    private List<OfficialPoPreflightIssue> readIssuesJson(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, OfficialPoPreflightIssue.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize Preflight issues", e);
        }
    }
}
