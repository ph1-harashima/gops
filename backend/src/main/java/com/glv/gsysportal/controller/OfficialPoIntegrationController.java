package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.request.SetIntegrationIntentRequest;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.OfficialPoIntegrationService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 7-C2A 6章: explicit Business Action for Official PO Integration -
 * never a generic Status update API, matching this codebase's established
 * convention ({@link OrderApprovalController}). "G-SYS連携準備" only creates
 * an Integration Request and runs Preflight (7-C2A 14章) - it never writes
 * to Legacy in any way (no File, no Legacy DB row).
 *
 * <p>Phase 9-A adds PO No. confirmation and Excel generation - the Excel
 * itself is stored purely in Portal (no Legacy/Import Folder write yet;
 * that is Phase 9-B).
 */
@RestController
public class OfficialPoIntegrationController {

    private final OfficialPoIntegrationService integrationService;
    private final CurrentUserProvider currentUserProvider;

    public OfficialPoIntegrationController(OfficialPoIntegrationService integrationService,
                                            CurrentUserProvider currentUserProvider) {
        this.integrationService = integrationService;
        this.currentUserProvider = currentUserProvider;
    }

    /** Order Detail's "G-SYS正式PO連携" Section (7-C2A 13章) - any
     * authenticated user may view it, matching every other read endpoint in
     * this codebase (only the Action below is ADMIN-restricted). */
    @GetMapping("/api/orders/{id}/official-po")
    public OfficialPoIntegrationResponse get(@PathVariable Long id) {
        return integrationService.getIntegration(id);
    }

    /** "G-SYS連携準備". ADMIN only (7-C2A 6章: the standing recommendation for
     * the entry point into real G-SYS Integration - self-approval Scope for
     * OPERATOR stays a separate, still-undecided CUSTOMER REVIEW item and is
     * not conflated with this gate). Order must be APPROVED (409
     * ORDER_NOT_APPROVED otherwise). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/official-po/request")
    public OfficialPoIntegrationResponse request(@PathVariable Long id) {
        return integrationService.requestIntegration(id, currentUserProvider.currentUsername());
    }

    /** Phase 7-C6 12章/13章: "NEW"/"UPDATE" - ADMIN only, requires an
     * Integration Request to already exist for the current target Revision. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/api/orders/{id}/official-po/intent")
    public OfficialPoIntegrationResponse setIntent(@PathVariable Long id, @RequestBody SetIntegrationIntentRequest request) {
        return integrationService.setIntegrationIntent(id, request.intent(), currentUserProvider.currentUsername());
    }

    /** "PO番号入力/確定UI" (Phase 9-A). ADMIN only, editable while
     * PENDING/GENERATED - locked once SUBMITTED. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/api/orders/{id}/official-po/number")
    public OfficialPoIntegrationResponse confirmNumber(@PathVariable Long id, @RequestBody ConfirmOfficialPoNumberRequest body) {
        return integrationService.confirmOfficialPoNumber(id, body, currentUserProvider.currentUsername());
    }

    /** Official PO Excel generation (Phase 9-A). ADMIN only. Idempotent -
     * see {@code OfficialPoIntegrationService#generateExcel}'s Javadoc. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/official-po/generate")
    public OfficialPoIntegrationResponse generate(@PathVariable Long id) {
        return integrationService.generateExcel(id, currentUserProvider.currentUsername());
    }

    /** "Import Folderへ配置" (Phase 9-B). ADMIN only. Idempotent/Retry-safe -
     * see {@code OfficialPoIntegrationService#placeToImportFolder}'s Javadoc. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/official-po/place")
    public OfficialPoIntegrationResponse place(@PathVariable Long id) {
        return integrationService.placeToImportFolder(id, currentUserProvider.currentUsername());
    }

    /** Downloads the generated Official PO Excel for staff review (Phase
     * 9-A). ADMIN only, matching the other write-adjacent actions on this
     * Controller (the GET above stays open to all authenticated users - this
     * one exposes actual file content, kept ADMIN-only defensively). */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/orders/{id}/official-po/excel")
    public ResponseEntity<byte[]> downloadExcel(@PathVariable Long id) {
        byte[] bytes = integrationService.downloadExcel(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("official-po-" + id + ".xlsx").build().toString())
                .body(bytes);
    }
}
