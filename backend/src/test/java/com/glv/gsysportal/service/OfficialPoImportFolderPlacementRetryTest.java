package com.glv.gsysportal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glv.gsysportal.domain.IdempotentOperation;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.repository.legacy.LegacyPoConcurrencyReadRepository;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.PortalUserRepository;
import com.glv.gsysportal.service.integration.OfficialPoImportFolderAdapter;
import com.glv.gsysportal.service.integration.OfficialPoImportFolderWriteException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 9-B §7 "Retry時の重複処理防止" - real Service-level proof (not just
 * the entity-level State Model, {@code OfficialPoIntegrationRequestTest}'s
 * own scope) that a placement FAILURE followed by a retry call actually
 * reaches SUBMITTED. {@link com.glv.gsysportal.service.integration.LocalFilesystemImportFolderAdapter}
 * (the only real Adapter active here) has no failure mode of its own to
 * trigger, so this is a pure Mockito unit test mocking
 * {@link OfficialPoImportFolderAdapter} directly - same idiom as
 * {@code EmailSendServiceRetryTest}.
 */
class OfficialPoImportFolderPlacementRetryTest {

    private static final Long ORDER_ID = 77L;
    private static final String ADMIN = "admin-tester";

    private PortalOrder order() {
        PortalOrder order = new PortalOrder();
        order.setId(ORDER_ID);
        order.setCurrentRevisionNo(null); // targetRevisionNo -> 1
        return order;
    }

    private OfficialPoIntegrationRequest generatedRequest() {
        OfficialPoIntegrationRequest r = new OfficialPoIntegrationRequest();
        r.setPortalOrderId(ORDER_ID);
        r.setRevisionNo(1);
        r.setOfficialPoNo("SUPA-OUTD-TEST");
        r.setStatus(OfficialPoIntegrationRequest.STATUS_GENERATED);
        r.setGeneratedFileKey("some-file-key.xlsx");
        return r;
    }

    @Test
    void placementFailureThenRetrySucceedsAndReachesSubmitted() {
        PortalOrderRepository portalOrderRepository = mock(PortalOrderRepository.class);
        OfficialPoIntegrationRequestRepository integrationRequestRepository = mock(OfficialPoIntegrationRequestRepository.class);
        AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
        OfficialPoPreflightService preflightService = mock(OfficialPoPreflightService.class);
        OfficialPoExcelGenerationService excelGenerationService = mock(OfficialPoExcelGenerationService.class);
        OfficialPoImportFolderAdapter importFolderAdapter = mock(OfficialPoImportFolderAdapter.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        LegacyPoConcurrencyReadRepository legacyReadRepository = mock(LegacyPoConcurrencyReadRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortalUserRepository portalUserRepository = mock(PortalUserRepository.class);

        PortalOrder order = order();
        OfficialPoIntegrationRequest request = generatedRequest();
        when(portalOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(integrationRequestRepository.findByPortalOrderIdAndRevisionNo(ORDER_ID, 1)).thenReturn(Optional.of(request));
        when(integrationRequestRepository.save(any(OfficialPoIntegrationRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(excelGenerationService.load("some-file-key.xlsx")).thenReturn(new byte[]{1, 2, 3});

        IdempotentOperation op = new IdempotentOperation("OFFICIAL_PO_FILE_PLACEMENT", ORDER_ID.toString(),
                ORDER_ID + "-SUPA-OUTD-TEST-1", OffsetDateTime.now());
        op.setId(555L);
        when(idempotencyService.claim(eq("OFFICIAL_PO_FILE_PLACEMENT"), eq(ORDER_ID.toString()), anyString()))
                .thenReturn(new IdempotencyService.IdempotencyClaim(op, true));

        OfficialPoIntegrationService service = new OfficialPoIntegrationService(portalOrderRepository,
                integrationRequestRepository, auditEventRepository, preflightService, excelGenerationService,
                importFolderAdapter, idempotencyService, legacyReadRepository, objectMapper, portalUserRepository);

        // First attempt fails.
        doThrow(new OfficialPoImportFolderWriteException("disk full", new java.io.IOException("disk full")))
                .when(importFolderAdapter).place(any(byte[].class), anyString());

        OfficialPoIntegrationResponse first = service.placeToImportFolder(ORDER_ID, ADMIN);
        assertEquals("FAILED", first.status());
        assertEquals(OfficialPoIntegrationRequest.STATUS_FAILED, request.getStatus());

        // Retry (same Request, now FAILED) succeeds.
        org.mockito.Mockito.reset(importFolderAdapter);

        OfficialPoIntegrationResponse second = service.placeToImportFolder(ORDER_ID, ADMIN);

        assertEquals("SUBMITTED", second.status());
        assertEquals(OfficialPoIntegrationRequest.STATUS_SUBMITTED, request.getStatus());
    }
}
