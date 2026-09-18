package com.glv.gsysportal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.response.OfficialPoImportConfirmationDiff;
import com.glv.gsysportal.dto.response.OfficialPoImportConfirmationResponse;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OfficialPoPreflightIssue;
import com.glv.gsysportal.dto.response.OfficialPoPreflightResult;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.DuplicateOfficialPoNumberException;
import com.glv.gsysportal.exception.IntegrationRequestRequiredException;
import com.glv.gsysportal.exception.InvalidIntegrationIntentException;
import com.glv.gsysportal.exception.InvalidOfficialPoNumberException;
import com.glv.gsysportal.exception.OfficialPoAlreadySubmittedException;
import com.glv.gsysportal.exception.OfficialPoExcelNotGeneratedException;
import com.glv.gsysportal.exception.OfficialPoNotGeneratedException;
import com.glv.gsysportal.exception.OfficialPoNotSubmittedException;
import com.glv.gsysportal.exception.OfficialPoNumberRequiredException;
import com.glv.gsysportal.exception.OfficialPoPreflightBlockedException;
import com.glv.gsysportal.exception.OfficialPoReissueNotRequiredException;
import com.glv.gsysportal.exception.OrderNotApprovedException;
import com.glv.gsysportal.repository.legacy.LegacyPoConcurrencyReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyPoConcurrencyHeaderRow;
import com.glv.gsysportal.repository.legacy.row.LegacyPoConcurrencyLineRow;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.repository.prototype.OrderEmailRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.PortalUserRepository;
import com.glv.gsysportal.domain.PortalUser;
import com.glv.gsysportal.exception.OfficialPoPdfNotGeneratedException;
import com.glv.gsysportal.service.excel.OfficialPoExcelDownload;
import com.glv.gsysportal.service.excel.OfficialPoFileNaming;
import com.glv.gsysportal.service.excel.OfficialPoPdfDownload;
import com.glv.gsysportal.service.integration.OfficialPoImportFolderAdapter;
import com.glv.gsysportal.service.integration.OfficialPoImportFolderWriteException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 7-C2A: Official PO Integration Request Business Action
 * (docs/official-po-integration-detailed-design.md 13章/22.3章 step 1-4's
 * Foundation). Everything this Phase does is Portal-DB-local plus Legacy
 * READ ONLY Preflight - no File is written anywhere, no Legacy row is
 * touched (7-C2A 0章).
 */
@Service
public class OfficialPoIntegrationService {

    /** Excel-contract "no more than 30 characters" is the ONLY confirmed
     * length rule (Legacy Const.LEN_TR_PO_PO_NO) - see
     * {@code InvalidOfficialPoNumberException}'s Javadoc for why nothing
     * stricter is enforced here. */
    static final int MAX_OFFICIAL_PO_NO_LENGTH = 30;

    /** Phase 9-B: {@link IdempotencyService} operation type for Import
     * Folder placement - Idempotency key is {@code orderId + "-" + officialPoNo
     * + "-" + revisionNo} (unique per actual hand-off attempt target). */
    static final String OPERATION_TYPE_FILE_PLACEMENT = "OFFICIAL_PO_FILE_PLACEMENT";

    private final PortalOrderRepository portalOrderRepository;
    private final OfficialPoIntegrationRequestRepository integrationRequestRepository;
    private final AuditEventRepository auditEventRepository;
    private final OfficialPoPreflightService preflightService;
    private final OfficialPoExcelGenerationService excelGenerationService;
    private final OfficialPoPdfGenerationService pdfGenerationService;
    private final OfficialPoImportFolderAdapter importFolderAdapter;
    private final IdempotencyService idempotencyService;
    private final LegacyPoConcurrencyReadRepository legacyReadRepository;
    private final ObjectMapper objectMapper;
    private final PortalUserRepository portalUserRepository;
    private final OrderEmailRepository orderEmailRepository;

    public OfficialPoIntegrationService(PortalOrderRepository portalOrderRepository,
                                         OfficialPoIntegrationRequestRepository integrationRequestRepository,
                                         AuditEventRepository auditEventRepository,
                                         OfficialPoPreflightService preflightService,
                                         OfficialPoExcelGenerationService excelGenerationService,
                                         OfficialPoPdfGenerationService pdfGenerationService,
                                         OfficialPoImportFolderAdapter importFolderAdapter,
                                         IdempotencyService idempotencyService,
                                         LegacyPoConcurrencyReadRepository legacyReadRepository,
                                         ObjectMapper objectMapper,
                                         PortalUserRepository portalUserRepository,
                                         OrderEmailRepository orderEmailRepository) {
        this.portalOrderRepository = portalOrderRepository;
        this.integrationRequestRepository = integrationRequestRepository;
        this.auditEventRepository = auditEventRepository;
        this.preflightService = preflightService;
        this.excelGenerationService = excelGenerationService;
        this.pdfGenerationService = pdfGenerationService;
        this.importFolderAdapter = importFolderAdapter;
        this.idempotencyService = idempotencyService;
        this.legacyReadRepository = legacyReadRepository;
        this.objectMapper = objectMapper;
        this.portalUserRepository = portalUserRepository;
        this.orderEmailRepository = orderEmailRepository;
    }

    /**
     * "G-SYS連携準備" (7-C2A 14章): ensures an Integration Request row exists
     * for this Order's current revision (creating it on the first call only -
     * Idempotency key = portalOrderId+revisionNo, 7-C2A 4章), then (re-)runs
     * Preflight against Legacy READ ONLY every call, including repeat calls
     * on an already-existing Request - Master data can change between calls,
     * so a stale PASS should never be trusted indefinitely.
     *
     * <p>Phase 7-C5 16章: {@code revisionNo} is the SAME concept as
     * {@link PortalOrder#getCurrentRevisionNo()} - not a separate numbering
     * scheme. This Request is always created while the Order is APPROVED,
     * i.e. BEFORE the Demo Send that will actually crystallize the next
     * {@code portal_order_revision} row (docs/supplier-response-revision-workflow.md
     * 2章), so the target revision is "one past whatever was last sent":
     * {@code currentRevisionNo == null} (never sent) -> 1, matching 7-C2A's
     * original hardcoded behavior exactly for a first-time Order; otherwise
     * {@code currentRevisionNo + 1}, correctly targeting the upcoming
     * correction's Revision once an Order has been sent, corrected, and
     * re-approved.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public OfficialPoIntegrationResponse requestIntegration(Long orderId, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        if (!PortalOrder.STATUS_APPROVED.equals(order.getStatus())) {
            throw new OrderNotApprovedException(orderId, order.getStatus());
        }
        int targetRevisionNo = targetRevisionNo(order);

        OffsetDateTime now = OffsetDateTime.now();
        boolean isNewRequest = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(orderId, targetRevisionNo).isEmpty();

        OfficialPoIntegrationRequest request = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(orderId, targetRevisionNo)
                .orElseGet(() -> {
                    OfficialPoIntegrationRequest r = new OfficialPoIntegrationRequest();
                    r.setPortalOrderId(orderId);
                    r.setRevisionNo(targetRevisionNo);
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

    /**
     * Gap Analysis C-2 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
     * "Official POを再発行" - a human-confirmed action (never automatic, C-3),
     * enabled only when {@link #isReissueRequired} agrees a reissue is
     * actually pending (same detection Phase 6's banner uses - UI and
     * enforcement share this one computation so they can never disagree).
     * Marks the current ACTIVE Request SUPERSEDED, then reuses
     * {@link #requestIntegration} verbatim to create the new ACTIVE Request
     * at the next target Revision (Preflight re-run included) - never a
     * second, parallel "create a Request" code path.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public OfficialPoIntegrationResponse reissue(Long orderId, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        if (!PortalOrder.STATUS_APPROVED.equals(order.getStatus())) {
            throw new OrderNotApprovedException(orderId, order.getStatus());
        }
        OfficialPoIntegrationRequest current = integrationRequestRepository
                .findFirstByPortalOrderIdOrderByRevisionNoDesc(orderId)
                .orElseThrow(() -> new IntegrationRequestRequiredException(orderId, targetRevisionNo(order)));
        if (!isReissueRequired(current)) {
            throw new OfficialPoReissueNotRequiredException(orderId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        int previousRevisionNo = current.getRevisionNo();
        current.markSuperseded("Reissued for a corrected Revision", performedBy, now);
        integrationRequestRepository.save(current);

        OfficialPoIntegrationResponse created = requestIntegration(orderId, performedBy);

        auditEventRepository.save(new AuditEvent(orderId, null, AuditEvent.OFFICIAL_PO_REISSUED,
                "revisionNo", String.valueOf(previousRevisionNo), String.valueOf(created.revisionNo()), performedBy, now));
        return created;
    }

    /** Gap Analysis C-3: the SAME computation both the "Official POの再発行が
     * 必要です" banner (via {@link #toResponse}'s {@code reissueRequired}
     * field) and {@link #reissue}'s own precondition use. True only when the
     * current ACTIVE Document was actually issued (GENERATED/SUBMITTED/
     * CONFIRMED - a still-PENDING Request was never handed to anyone, so
     * there is nothing yet to invalidate) AND at least one
     * {@code ORDER_REVISION_CREATED} correction has happened since (Phase
     * 7-C5's existing "修正版を作成" Action) - reusing the existing Revision/
     * Correction Audit trail rather than inventing a new Qty-comparison
     * Business Rule (docs 7章's explicit "既存Revisionモデルを利用"
     * instruction). */
    private boolean isReissueRequired(OfficialPoIntegrationRequest r) {
        if (!OfficialPoIntegrationRequest.LIFECYCLE_ACTIVE.equals(r.getLifecycleStatus())) {
            return false;
        }
        if (!isIssued(r.getStatus())) {
            return false;
        }
        OffsetDateTime referenceTime = r.getSubmittedAt() != null ? r.getSubmittedAt()
                : r.getGeneratedAt() != null ? r.getGeneratedAt() : r.getRequestedAt();
        if (referenceTime == null) {
            return false;
        }
        return auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(r.getPortalOrderId()).stream()
                .anyMatch(e -> AuditEvent.ORDER_REVISION_CREATED.equals(e.getEventType())
                        && e.getPerformedAt().isAfter(referenceTime));
    }

    private static boolean isIssued(String status) {
        return OfficialPoIntegrationRequest.STATUS_GENERATED.equals(status)
                || OfficialPoIntegrationRequest.STATUS_SUBMITTED.equals(status)
                || OfficialPoIntegrationRequest.STATUS_CONFIRMED.equals(status);
    }

    /** Gap Analysis C-2 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
     * Revision History - every Official PO Integration Request ever created
     * for this Order (old Revisions are never deleted, only marked
     * SUPERSEDED/CANCELLED by {@link #reissue}/{@code cancel}). */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<com.glv.gsysportal.dto.response.OfficialPoRevisionHistoryEntry> getRevisionHistory(Long orderId) {
        if (!portalOrderRepository.existsById(orderId)) {
            throw new DraftNotFoundException(orderId);
        }
        return integrationRequestRepository.findByPortalOrderIdOrderByRevisionNoAsc(orderId).stream()
                .map(this::toRevisionHistoryEntry)
                .toList();
    }

    private com.glv.gsysportal.dto.response.OfficialPoRevisionHistoryEntry toRevisionHistoryEntry(OfficialPoIntegrationRequest r) {
        String requestedByDisplayName = r.getRequestedBy() == null ? null
                : portalUserRepository.findByUsername(r.getRequestedBy()).map(PortalUser::getDisplayName).orElse(null);
        String emailSendStatus = orderEmailRepository
                .findByPortalOrderIdAndRevisionNo(r.getPortalOrderId(), r.getRevisionNo())
                .map(com.glv.gsysportal.domain.OrderEmail::getStatus)
                .orElse(null);
        return new com.glv.gsysportal.dto.response.OfficialPoRevisionHistoryEntry(
                r.getRevisionNo(), r.getRequestedAt(), r.getRequestedBy(), requestedByDisplayName,
                r.getOfficialPoNo(), r.getStatus(), r.getGeneratedFileKey() != null, r.getPdfFileKey() != null,
                r.getLifecycleStatus(), r.getLifecycleReason(), r.getLifecycleChangedBy(), r.getLifecycleChangedAt(),
                emailSendStatus
        );
    }

    /**
     * Phase 7-C5 16章's "one past whatever was last sent" target Revision
     * computation, extracted so {@code LegacyPoConcurrencyService} (7-C6)
     * targets the exact SAME Revision a Baseline/Integration Request would -
     * a Legacy PO Baseline captured against the wrong Revision would silently
     * defeat 7-C6 16章's "Rev1のBaselineをRev2へ流用しない" guarantee.
     */
    static int targetRevisionNo(PortalOrder order) {
        return order.getCurrentRevisionNo() == null ? 1 : order.getCurrentRevisionNo() + 1;
    }

    /** Phase 7-C6 12章/13章: ADMIN explicitly records whether this Order's
     * upcoming Official PO Integration targets a brand-new G-SYS PO or an
     * update to an existing one. Requires an Integration Request to already
     * exist for the current target Revision (mirrors Baseline Capture's own
     * precondition, 7-C6 9章) - there is nothing to annotate otherwise. */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public OfficialPoIntegrationResponse setIntegrationIntent(Long orderId, String intent, String performedBy) {
        if (!OfficialPoIntegrationRequest.INTENT_NEW.equals(intent) && !OfficialPoIntegrationRequest.INTENT_UPDATE.equals(intent)) {
            throw new InvalidIntegrationIntentException(intent);
        }
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        int targetRevisionNo = targetRevisionNo(order);
        OfficialPoIntegrationRequest request = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(orderId, targetRevisionNo)
                .orElseThrow(() -> new IntegrationRequestRequiredException(orderId, targetRevisionNo));

        request.setIntegrationIntent(intent);
        request.setUpdatedAt(OffsetDateTime.now());
        return toResponse(integrationRequestRepository.save(request));
    }

    /**
     * "PO番号入力/確定UI" (Production PO Workflow §A/Phase 9-A). Requires an
     * existing Integration Request (PO No. belongs to a specific revision's
     * hand-off attempt, same precondition as {@link #setIntegrationIntent}),
     * and that it has not yet been SUBMITTED to the Legacy Import Folder
     * (locked thereafter). Only length is validated - no ID Code/separator
     * structure (Working Assumption: G-SYS has no confirmed Business Rule
     * beyond the 30-character maximum, so Portal must not invent one).
     * {@link AuditEvent#OFFICIAL_PO_NUMBER_CONFIRMED} is written only when
     * the number itself actually changes.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public OfficialPoIntegrationResponse confirmOfficialPoNumber(Long orderId, ConfirmOfficialPoNumberRequest body, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        int targetRevisionNo = targetRevisionNo(order);
        OfficialPoIntegrationRequest request = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(orderId, targetRevisionNo)
                .orElseThrow(() -> new IntegrationRequestRequiredException(orderId, targetRevisionNo));
        requireEditable(request, orderId);

        String officialPoNo = body.officialPoNo() == null ? null : body.officialPoNo().trim();
        if (officialPoNo == null || officialPoNo.isEmpty() || officialPoNo.length() > MAX_OFFICIAL_PO_NO_LENGTH) {
            throw new InvalidOfficialPoNumberException(officialPoNo);
        }

        String previousPoNo = request.getOfficialPoNo();
        OffsetDateTime now = OffsetDateTime.now();

        request.setOfficialPoNo(officialPoNo);
        request.setDeliveryWeek(body.deliveryWeek());
        request.setDeliveryDate(body.deliveryDate());
        request.setShipVia(body.shipVia());
        request.setShipTerm(body.shipTerm());
        request.setPaymentTerm(body.paymentTerm());
        request.setUpdatedAt(now);
        order.setOfficialPoNo(officialPoNo);

        OfficialPoIntegrationRequest saved;
        try {
            saved = integrationRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateOfficialPoNumberException(officialPoNo);
        }

        if (!officialPoNo.equals(previousPoNo)) {
            auditEventRepository.save(new AuditEvent(orderId, null,
                    AuditEvent.OFFICIAL_PO_NUMBER_CONFIRMED, "officialPoNo", previousPoNo, officialPoNo, performedBy, now));
        }
        return toResponse(saved);
    }

    /**
     * Official PO Excel generation (Production PO Workflow §B/Phase 9-A):
     * the first real caller of {@link OfficialPoExcelGenerator} - every
     * prior Phase only exercised it from a Contract Test. Idempotent:
     * calling again once already GENERATED (or later) returns the current
     * state unchanged rather than throwing - a smooth double-click UX, the
     * entity's own state-machine guard still protects any other ordering
     * mistake. Gate: requires a confirmed Official PO No. and a Preflight
     * result that is not BLOCKED.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public OfficialPoIntegrationResponse generateExcel(Long orderId, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        int targetRevisionNo = targetRevisionNo(order);
        OfficialPoIntegrationRequest request = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(orderId, targetRevisionNo)
                .orElseThrow(() -> new IntegrationRequestRequiredException(orderId, targetRevisionNo));

        if (!OfficialPoIntegrationRequest.STATUS_PENDING.equals(request.getStatus())) {
            return toResponse(request);
        }
        if (request.getOfficialPoNo() == null) {
            throw new OfficialPoNumberRequiredException(orderId);
        }
        if (OfficialPoPreflightResult.RESULT_BLOCKED.equals(request.getPreflightResult())) {
            throw new OfficialPoPreflightBlockedException(orderId);
        }

        String fileKey = excelGenerationService.generateAndStore(order, request);
        OffsetDateTime now = OffsetDateTime.now();
        request.markGenerated(fileKey, now);
        OfficialPoIntegrationRequest saved = integrationRequestRepository.save(request);

        auditEventRepository.save(new AuditEvent(orderId, null,
                AuditEvent.OFFICIAL_PO_EXCEL_GENERATED, null, null, fileKey, performedBy, now));
        return toResponse(saved);
    }

    /**
     * Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
     * "G-OPS Standard Official PO PDF" - same Gate as Excel generation
     * (confirmed PO No., Preflight not BLOCKED) but INDEPENDENT of the
     * Integration Status state machine (PDF never drives PENDING/GENERATED/
     * SUBMITTED/CONFIRMED/FAILED - that axis stays exclusively about the
     * Excel -> Import Folder -> G-SYS hand-off). Unlike Excel, always
     * re-generates on every call (no "already GENERATED, no-op" short
     * circuit) - there is no separate PDF state machine to protect from a
     * stale re-generate, and Phase 5 (Reissue) needs to regenerate this
     * freely per Revision.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public OfficialPoIntegrationResponse generatePdf(Long orderId, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        int targetRevisionNo = targetRevisionNo(order);
        OfficialPoIntegrationRequest request = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(orderId, targetRevisionNo)
                .orElseThrow(() -> new IntegrationRequestRequiredException(orderId, targetRevisionNo));

        if (request.getOfficialPoNo() == null) {
            throw new OfficialPoNumberRequiredException(orderId);
        }
        if (OfficialPoPreflightResult.RESULT_BLOCKED.equals(request.getPreflightResult())) {
            throw new OfficialPoPreflightBlockedException(orderId);
        }

        String fileKey = pdfGenerationService.generateAndStore(order, request);
        OffsetDateTime now = OffsetDateTime.now();
        request.setPdfFileKey(fileKey);
        request.setPdfGeneratedAt(now);
        request.setUpdatedAt(now);
        OfficialPoIntegrationRequest saved = integrationRequestRepository.save(request);

        auditEventRepository.save(new AuditEvent(orderId, null,
                AuditEvent.OFFICIAL_PO_PDF_GENERATED, null, null, fileKey, performedBy, now));
        return toResponse(saved);
    }

    /** Streams the stored PDF bytes for staff review - mirrors
     * {@link #downloadExcel}'s own shape exactly. */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public OfficialPoPdfDownload downloadPdf(Long orderId) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        OfficialPoIntegrationRequest request = integrationRequestRepository
                .findFirstByPortalOrderIdOrderByRevisionNoDesc(orderId)
                .orElseThrow(() -> new OfficialPoPdfNotGeneratedException(orderId));
        if (request.getPdfFileKey() == null) {
            throw new OfficialPoPdfNotGeneratedException(orderId);
        }
        byte[] bytes = pdfGenerationService.load(request.getPdfFileKey());
        String fileName = OfficialPoFileNaming.buildFileName(order.getSupplierCode(), order.getBrandCode(),
                request.getPdfGeneratedAt() == null ? null : request.getPdfGeneratedAt().toLocalDate(),
                request.getOfficialPoNo(), request.getRevisionNo(), "pdf");
        return new OfficialPoPdfDownload(bytes, fileName);
    }

    /**
     * "Import Folderへ配置" (Production PO Workflow §C/Phase 9-B). Requires
     * the Excel to already be GENERATED (a retry from FAILED is allowed -
     * the same stored bytes are re-placed, never regenerated). Idempotent
     * via {@link IdempotencyService}: a concurrent or repeat call for the
     * same (orderId, officialPoNo, revisionNo) never places the File twice
     * (§7 "Import Folder二重配置防止").
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public OfficialPoIntegrationResponse placeToImportFolder(Long orderId, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        int targetRevisionNo = targetRevisionNo(order);
        OfficialPoIntegrationRequest request = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(orderId, targetRevisionNo)
                .orElseThrow(() -> new IntegrationRequestRequiredException(orderId, targetRevisionNo));

        if (OfficialPoIntegrationRequest.STATUS_SUBMITTED.equals(request.getStatus())
                || OfficialPoIntegrationRequest.STATUS_CONFIRMED.equals(request.getStatus())) {
            return toResponse(request); // already placed - idempotent no-op
        }
        if (!OfficialPoIntegrationRequest.STATUS_GENERATED.equals(request.getStatus())
                && !OfficialPoIntegrationRequest.STATUS_FAILED.equals(request.getStatus())) {
            throw new OfficialPoNotGeneratedException(orderId);
        }

        String idempotencyKey = orderId + "-" + request.getOfficialPoNo() + "-" + targetRevisionNo;
        IdempotencyService.IdempotencyClaim claim = idempotencyService.claim(
                OPERATION_TYPE_FILE_PLACEMENT, orderId.toString(), idempotencyKey);
        if (!claim.claimed()) {
            return toResponse(request); // another attempt already in flight/succeeded
        }

        OffsetDateTime now = OffsetDateTime.now();
        byte[] excelBytes = excelGenerationService.load(request.getGeneratedFileKey());
        // Gap Analysis B-3/B-4 (docs/gulliver-20260917-phase1-gap-analysis.md
        // 7章): human-identifiable name (Supplier/Brand/Date/PO No./3-digit
        // Revision), same shared builder downloadExcel() uses - WORKING
        // ASSUMPTION, Import Contract-safe (see OfficialPoFileNaming's
        // Javadoc for the re-confirmed Legacy Fact backing that claim).
        String fileName = OfficialPoFileNaming.buildFileName(order.getSupplierCode(), order.getBrandCode(),
                now.toLocalDate(), request.getOfficialPoNo(), targetRevisionNo, "xlsx");

        try {
            importFolderAdapter.place(excelBytes, fileName);
        } catch (OfficialPoImportFolderWriteException e) {
            request.markFailed("IMPORT_FOLDER_WRITE_FAILED", e.getMessage(), now);
            OfficialPoIntegrationRequest failed = integrationRequestRepository.save(request);
            idempotencyService.markFailed(claim.operation().getId(), "IMPORT_FOLDER_WRITE_FAILED");
            auditEventRepository.save(new AuditEvent(orderId, null,
                    AuditEvent.OFFICIAL_PO_FILE_PLACEMENT_FAILED, null, null, e.getMessage(), performedBy, now));
            return toResponse(failed);
        }

        request.markSubmitted(now);
        OfficialPoIntegrationRequest saved = integrationRequestRepository.save(request);
        idempotencyService.markSucceeded(claim.operation().getId());
        auditEventRepository.save(new AuditEvent(orderId, null,
                AuditEvent.OFFICIAL_PO_FILE_PLACED, null, null, fileName, performedBy, now));
        return toResponse(saved);
    }

    /**
     * "G-SYS取込確認" (Production PO Workflow §E/Phase 9-C): a manual,
     * on-demand Legacy READ ONLY check (design doc §9's Success Detection -
     * a background poller is deliberately NOT built this Phase, §10's
     * "Production環境には接続しない" applies equally to not reaching for
     * network resources on a schedule in this environment). Requires status
     * SUBMITTED (or later - CONFIRMED just re-confirms, a harmless no-op
     * re-check). Never writes to Legacy in any way.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public OfficialPoImportConfirmationResponse confirmImport(Long orderId, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        int targetRevisionNo = targetRevisionNo(order);
        OfficialPoIntegrationRequest request = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(orderId, targetRevisionNo)
                .orElseThrow(() -> new IntegrationRequestRequiredException(orderId, targetRevisionNo));

        if (OfficialPoIntegrationRequest.STATUS_CONFIRMED.equals(request.getStatus())) {
            return new OfficialPoImportConfirmationResponse(true, null, List.of(), toResponse(request));
        }
        if (!OfficialPoIntegrationRequest.STATUS_SUBMITTED.equals(request.getStatus())) {
            throw new OfficialPoNotSubmittedException(orderId);
        }

        Optional<LegacyPoConcurrencyHeaderRow> header = legacyReadRepository.findPoHeader(request.getOfficialPoNo());
        if (header.isEmpty() || !"OFFICIAL".equals(header.get().status())) {
            return new OfficialPoImportConfirmationResponse(false,
                    OfficialPoImportConfirmationResponse.REASON_NOT_YET_IMPORTED, List.of(), toResponse(request));
        }

        Map<String, Integer> expected = new LinkedHashMap<>();
        for (PortalOrderDetail detail : order.getDetails()) {
            if (!detail.isRemoved()) {
                expected.merge(detail.getSku(), detail.getOrderQty(), Integer::sum);
            }
        }
        Map<String, Integer> actual = new LinkedHashMap<>();
        for (LegacyPoConcurrencyLineRow line : legacyReadRepository.findPoLines(request.getOfficialPoNo())) {
            actual.merge(line.skuCode(), line.orderedQty(), Integer::sum);
        }

        List<OfficialPoImportConfirmationDiff> diffs = diffLines(expected, actual);
        if (!diffs.isEmpty()) {
            return new OfficialPoImportConfirmationResponse(false,
                    OfficialPoImportConfirmationResponse.REASON_MISMATCH, diffs, toResponse(request));
        }

        OffsetDateTime now = OffsetDateTime.now();
        request.markConfirmed(now);
        OfficialPoIntegrationRequest saved = integrationRequestRepository.save(request);
        auditEventRepository.save(new AuditEvent(orderId, null,
                AuditEvent.OFFICIAL_PO_IMPORT_CONFIRMED, null, null, request.getOfficialPoNo(), performedBy, now));
        return new OfficialPoImportConfirmationResponse(true, null, List.of(), toResponse(saved));
    }

    private static List<OfficialPoImportConfirmationDiff> diffLines(Map<String, Integer> expected, Map<String, Integer> actual) {
        List<OfficialPoImportConfirmationDiff> diffs = new ArrayList<>();
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            Integer actualQty = actual.get(e.getKey());
            if (!e.getValue().equals(actualQty)) {
                diffs.add(new OfficialPoImportConfirmationDiff(e.getKey(), e.getValue(), actualQty));
            }
        }
        for (Map.Entry<String, Integer> e : actual.entrySet()) {
            if (!expected.containsKey(e.getKey())) {
                diffs.add(new OfficialPoImportConfirmationDiff(e.getKey(), null, e.getValue()));
            }
        }
        return diffs;
    }

    /** Streams the stored Excel bytes for staff review (design doc §14's
     * "生成結果" - never exposes the storage key itself to the caller).
     * Gap Analysis B-3: the returned file name is the same human-identifiable
     * format {@link #placeToImportFolder} uses for the Import Folder copy -
     * one shared naming source ({@link OfficialPoFileNaming}), not two. */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public OfficialPoExcelDownload downloadExcel(Long orderId) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        OfficialPoIntegrationRequest request = integrationRequestRepository
                .findFirstByPortalOrderIdOrderByRevisionNoDesc(orderId)
                .orElseThrow(() -> new OfficialPoExcelNotGeneratedException(orderId));
        if (request.getGeneratedFileKey() == null) {
            throw new OfficialPoExcelNotGeneratedException(orderId);
        }
        byte[] bytes = excelGenerationService.load(request.getGeneratedFileKey());
        String fileName = OfficialPoFileNaming.buildFileName(order.getSupplierCode(), order.getBrandCode(),
                request.getGeneratedAt() == null ? null : request.getGeneratedAt().toLocalDate(),
                request.getOfficialPoNo(), request.getRevisionNo(), "xlsx");
        return new OfficialPoExcelDownload(bytes, fileName);
    }

    /** PENDING/GENERATED are still editable; SUBMITTED/CONFIRMED/FAILED are
     * locked (FAILED means a Legacy hand-off attempt was already made for
     * whatever was submitted - correcting the number now would desync the
     * Retry from what staff believe they're retrying). */
    private static void requireEditable(OfficialPoIntegrationRequest request, Long orderId) {
        if (!OfficialPoIntegrationRequest.STATUS_PENDING.equals(request.getStatus())
                && !OfficialPoIntegrationRequest.STATUS_GENERATED.equals(request.getStatus())) {
            throw new OfficialPoAlreadySubmittedException(orderId, request.getStatus());
        }
    }

    private OfficialPoIntegrationResponse toResponse(OfficialPoIntegrationRequest r) {
        OfficialPoPreflightResult preflight = r.getPreflightResult() == null ? null
                : new OfficialPoPreflightResult(r.getPreflightResult(), readIssuesJson(r.getPreflightIssuesJson()));
        String requestedByDisplayName = r.getRequestedBy() == null ? null
                : portalUserRepository.findByUsername(r.getRequestedBy()).map(PortalUser::getDisplayName).orElse(null);
        return new OfficialPoIntegrationResponse(
                r.getPortalOrderId(), r.getRevisionNo(), r.getStatus(), r.getOfficialPoNo(),
                r.getRequestedBy(), requestedByDisplayName, r.getRequestedAt(), preflight,
                r.getGeneratedAt(), r.getSubmittedAt(), r.getConfirmedAt(), r.getFailedAt(),
                r.getErrorCode(), r.getErrorMessage(), r.getIntegrationIntent(),
                r.getDeliveryWeek(), r.getDeliveryDate(), r.getShipVia(), r.getShipTerm(), r.getPaymentTerm(),
                r.getGeneratedFileKey() != null,
                r.getPdfFileKey() != null,
                r.getLifecycleStatus(),
                r.getLifecycleReason(),
                isReissueRequired(r)
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
