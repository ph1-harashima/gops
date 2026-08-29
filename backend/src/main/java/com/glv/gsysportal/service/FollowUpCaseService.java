package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.FollowUpCase;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CloseFollowUpCaseRequest;
import com.glv.gsysportal.dto.request.CreateFollowUpCaseRequest;
import com.glv.gsysportal.dto.request.UpdateFollowUpCaseRequest;
import com.glv.gsysportal.dto.response.FollowUpCaseResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.FollowUpCaseAlreadyClosedException;
import com.glv.gsysportal.exception.FollowUpCaseNotFoundException;
import com.glv.gsysportal.exception.InvalidFollowUpReasonException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.FollowUpCaseRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Follow-up Case CRUD/Close (Phase 7-C7A 9章/10章/12章). Creation is always
 * an explicit human Business Action - no code path in this codebase ever
 * auto-creates a Case from a Fulfillment calculation.
 */
@Service
public class FollowUpCaseService {

    private static final Set<String> VALID_REASONS = Set.of(
            FollowUpCase.REASON_DELIVERY_OVERDUE, FollowUpCase.REASON_PARTIAL_DELIVERY,
            FollowUpCase.REASON_NO_ARRIVAL, FollowUpCase.REASON_QUANTITY_DIFFERENCE, FollowUpCase.REASON_OTHER);

    private final PortalOrderRepository portalOrderRepository;
    private final PortalOrderRevisionRepository revisionRepository;
    private final FollowUpCaseRepository followUpCaseRepository;
    private final AuditEventRepository auditEventRepository;

    public FollowUpCaseService(PortalOrderRepository portalOrderRepository,
                                PortalOrderRevisionRepository revisionRepository,
                                FollowUpCaseRepository followUpCaseRepository,
                                AuditEventRepository auditEventRepository) {
        this.portalOrderRepository = portalOrderRepository;
        this.revisionRepository = revisionRepository;
        this.followUpCaseRepository = followUpCaseRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public FollowUpCaseResponse create(Long orderId, CreateFollowUpCaseRequest request, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        if (request.reason() == null || !VALID_REASONS.contains(request.reason())) {
            throw new InvalidFollowUpReasonException(request.reason());
        }

        OffsetDateTime now = OffsetDateTime.now();

        FollowUpCase followUpCase = new FollowUpCase();
        followUpCase.setPortalOrderId(orderId);
        followUpCase.setOrderRevisionId(order.getCurrentRevisionNo() == null ? null
                : revisionRepository.findByPortalOrderIdAndRevisionNo(orderId, order.getCurrentRevisionNo())
                        .map(r -> r.getId()).orElse(null));
        // Snapshotted at creation time - never re-read from portal_order at
        // render time (V12 migration comment / FollowUpCase Javadoc).
        followUpCase.setOfficialPoNo(order.getOfficialPoNo());
        followUpCase.setSkuCode(request.skuCode());
        followUpCase.setStatus(FollowUpCase.STATUS_OPEN);
        followUpCase.setReason(request.reason());
        followUpCase.setNote(request.note());
        followUpCase.setCreatedBy(performedBy);
        followUpCase.setCreatedAt(now);
        followUpCase.setUpdatedBy(performedBy);
        followUpCase.setUpdatedAt(now);

        FollowUpCase saved = followUpCaseRepository.save(followUpCase);

        AuditEvent created = new AuditEvent(orderId, null, AuditEvent.FOLLOW_UP_CREATED,
                "reason", null, request.reason(), performedBy, now);
        created.setNote(request.skuCode() == null ? null : "SKU=" + request.skuCode());
        auditEventRepository.save(created);

        return toResponse(saved);
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<FollowUpCaseResponse> list(Long orderId) {
        if (!portalOrderRepository.existsById(orderId)) {
            throw new DraftNotFoundException(orderId);
        }
        return followUpCaseRepository.findByPortalOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(FollowUpCaseService::toResponse)
                .toList();
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public FollowUpCaseResponse updateNote(Long id, UpdateFollowUpCaseRequest request, String performedBy) {
        FollowUpCase followUpCase = requireCase(id);
        if (FollowUpCase.STATUS_CLOSED.equals(followUpCase.getStatus())) {
            throw new FollowUpCaseAlreadyClosedException(id);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String oldNote = followUpCase.getNote();
        followUpCase.setNote(request.note());
        followUpCase.setUpdatedBy(performedBy);
        followUpCase.setUpdatedAt(now);
        FollowUpCase saved = followUpCaseRepository.save(followUpCase);

        if (!java.util.Objects.equals(oldNote, request.note())) {
            auditEventRepository.save(new AuditEvent(followUpCase.getPortalOrderId(), null, AuditEvent.FOLLOW_UP_UPDATED,
                    "note", oldNote, request.note(), performedBy, now));
        }

        return toResponse(saved);
    }

    /** ADMIN only (enforced at the Controller, 7-C7A 20章). */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public FollowUpCaseResponse close(Long id, CloseFollowUpCaseRequest request, String performedBy) {
        FollowUpCase followUpCase = requireCase(id);
        if (FollowUpCase.STATUS_CLOSED.equals(followUpCase.getStatus())) {
            throw new FollowUpCaseAlreadyClosedException(id);
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (request.note() != null) {
            followUpCase.setNote(request.note());
        }
        followUpCase.setStatus(FollowUpCase.STATUS_CLOSED);
        followUpCase.setClosedBy(performedBy);
        followUpCase.setClosedAt(now);
        followUpCase.setUpdatedBy(performedBy);
        followUpCase.setUpdatedAt(now);
        FollowUpCase saved = followUpCaseRepository.save(followUpCase);

        auditEventRepository.save(new AuditEvent(followUpCase.getPortalOrderId(), null, AuditEvent.FOLLOW_UP_CLOSED,
                null, null, null, performedBy, now));

        return toResponse(saved);
    }

    /** Called by {@link FollowUpMailPreviewService} on a successful
     * (non-BLOCKED) Preview - the meaningful signal that an inquiry was
     * actually prepared, since no real Send exists to trigger this off of
     * (7-C7A 13章's Javadoc on {@link FollowUpCase#STATUS_INQUIRY_PREPARED}). */
    @Transactional(transactionManager = "prototypeTransactionManager")
    void markInquiryPrepared(FollowUpCase followUpCase, String performedBy) {
        if (!FollowUpCase.STATUS_OPEN.equals(followUpCase.getStatus())) {
            return; // already INQUIRY_PREPARED (idempotent) or CLOSED (guarded by the caller)
        }
        followUpCase.setStatus(FollowUpCase.STATUS_INQUIRY_PREPARED);
        followUpCase.setUpdatedBy(performedBy);
        followUpCase.setUpdatedAt(OffsetDateTime.now());
        followUpCaseRepository.save(followUpCase);
    }

    FollowUpCase requireCase(Long id) {
        return followUpCaseRepository.findById(id).orElseThrow(() -> new FollowUpCaseNotFoundException(id));
    }

    private static FollowUpCaseResponse toResponse(FollowUpCase c) {
        return new FollowUpCaseResponse(
                c.getId(), c.getPortalOrderId(), c.getOrderRevisionId(), c.getOfficialPoNo(), c.getSkuCode(),
                c.getStatus(), c.getReason(), c.getNote(), c.getCreatedBy(), c.getCreatedAt(),
                c.getUpdatedBy(), c.getUpdatedAt(), c.getClosedBy(), c.getClosedAt()
        );
    }
}
