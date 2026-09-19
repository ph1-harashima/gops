package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.OfficialPoNotGeneratedException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.IdempotentOperationRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 9-B: Import Folder Integration (Production PO Workflow §13 Phase 2).
 * {@link com.glv.gsysportal.service.integration.LocalFilesystemImportFolderAdapter}
 * writes to a real local directory this Phase - Contract Test-style
 * verification of the actual File written, not just the DB state, matching
 * this codebase's "never trust a mock alone for a Filesystem/Legacy
 * boundary" convention (LegacyReadOnlyIntegrationTest's own precedent).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OfficialPoImportFolderPlacementIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001";
    private static final String OPERATOR = "tester01";
    private static final String ADMIN = "admin-tester";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private OfficialPoIntegrationService integrationService;
    @Autowired
    private OfficialPoIntegrationRequestRepository integrationRequestRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private IdempotentOperationRepository idempotentOperationRepository;
    @Autowired
    private Environment environment;
    @Value("${app.official-po.import-folder.base-dir}")
    private String importFolderBaseDir;

    private Path uploadDir() {
        String profile = environment.getActiveProfiles()[0];
        return Paths.get(importFolderBaseDir, profile, "upload");
    }

    private PortalOrder readyForPlacement() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        PortalOrder order = statusTransitionService.approve(draft.id(), ADMIN);
        // BR-08: Official PO No. is auto-numbered by requestIntegration
        // itself now - each call gets its own unique value from the real
        // per-Supplier x Brand sequence, so no test-provided suffix is
        // needed to keep this class's Orders distinguishable anymore.
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.confirmOfficialPoNumber(order.getId(),
                new ConfirmOfficialPoNumberRequest("WK36", "2026-09-05", null, null, null), ADMIN);
        integrationService.generateExcel(order.getId(), ADMIN);
        return order;
    }

    /** Gap Analysis B-3/B-4 (docs/gulliver-20260917-phase1-gap-analysis.md
     * 7章): the Import Folder file name no longer embeds the Portal orderId
     * (format is now {@code OfficialPO_{Supplier}_{Brand}_{Date}_{PONo}_{Revision}.xlsx},
     * OfficialPoFileNaming) - each test's own unique PO No. is the
     * distinguishing marker instead, matching this shared, cross-run
     * accumulating upload directory's existing "filter to just this test's
     * own file" need. */
    private long countUploadedFilesFor(String officialPoNo) throws IOException {
        if (!Files.exists(uploadDir())) {
            return 0;
        }
        try (Stream<Path> files = Files.list(uploadDir())) {
            return files.filter(p -> p.getFileName().toString().contains("_" + officialPoNo + "_")).count();
        }
    }

    @Test
    void placeRequiresGeneratedExcelFirst() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        PortalOrder order = statusTransitionService.approve(draft.id(), ADMIN);
        integrationService.requestIntegration(order.getId(), ADMIN);

        assertThrows(OfficialPoNotGeneratedException.class,
                () -> integrationService.placeToImportFolder(order.getId(), ADMIN));
    }

    @Test
    void placeWritesExactlyOneFileAndMarksSubmitted() throws IOException {
        PortalOrder order = readyForPlacement();

        OfficialPoIntegrationResponse response = integrationService.placeToImportFolder(order.getId(), ADMIN);

        assertEquals("SUBMITTED", response.status());
        assertEquals(1, countUploadedFilesFor(order.getOfficialPoNo()));

        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        assertTrue(trail.stream().anyMatch(e -> AuditEvent.OFFICIAL_PO_FILE_PLACED.equals(e.getEventType())));
    }

    @Test
    void doublePlaceIsIdempotent_writesOnlyOneFile() throws IOException {
        PortalOrder order = readyForPlacement();

        integrationService.placeToImportFolder(order.getId(), ADMIN);
        OfficialPoIntegrationResponse second = integrationService.placeToImportFolder(order.getId(), ADMIN);

        assertEquals("SUBMITTED", second.status());
        assertEquals(1, countUploadedFilesFor(order.getOfficialPoNo()), "double-click must not write a second File");

        long placedAuditCount = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .filter(e -> AuditEvent.OFFICIAL_PO_FILE_PLACED.equals(e.getEventType())).count();
        assertEquals(1, placedAuditCount);
    }

    @Test
    void idempotencyClaimIsPersisted() {
        PortalOrder order = readyForPlacement();

        integrationService.placeToImportFolder(order.getId(), ADMIN);

        String key = order.getId() + "-" + order.getOfficialPoNo() + "-1";
        assertTrue(idempotentOperationRepository
                .findByOperationTypeAndIdempotencyKey("OFFICIAL_PO_FILE_PLACEMENT", key)
                .isPresent());
    }
}
