package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.request.SetIntegrationIntentRequest;
import com.glv.gsysportal.dto.response.OfficialPoImportConfirmationResponse;
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

    /** "G-SYS取込確認" (Phase 9-C). ADMIN only. Strictly READ ONLY on Legacy -
     * see {@code OfficialPoIntegrationService#confirmImport}'s Javadoc. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/official-po/confirm-import")
    public OfficialPoImportConfirmationResponse confirmImport(@PathVariable Long id) {
        return integrationService.confirmImport(id, currentUserProvider.currentUsername());
    }

    /** Downloads the generated Official PO Excel for staff review (Phase
     * 9-A). ADMIN only, matching the other write-adjacent actions on this
     * Controller (the GET above stays open to all authenticated users - this
     * one exposes actual file content, kept ADMIN-only defensively). */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/orders/{id}/official-po/excel")
    public ResponseEntity<byte[]> downloadExcel(@PathVariable Long id) {
        // Gap Analysis B-3 (docs/gulliver-20260917-phase1-gap-analysis.md
        // 7章): human-identifiable name (Supplier/Brand/Date/PO No./Revision),
        // computed once in the Service and shared with the Import Folder
        // placement's own file name - no longer this Controller's own ad hoc
        // "official-po-{id}.xlsx".
        var download = integrationService.downloadExcel(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.fileName()).build().toString())
                .body(download.bytes());
    }

    /** Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
     * "G-OPS Standard Official PO PDF" generation. ADMIN only, same Gate as
     * Excel generation (confirmed PO No., Preflight not BLOCKED) but always
     * re-generates (no separate PDF state machine to protect). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/official-po/pdf/generate")
    public OfficialPoIntegrationResponse generatePdf(@PathVariable Long id) {
        return integrationService.generatePdf(id, currentUserProvider.currentUsername());
    }

    /** Downloads the generated Official PO PDF for staff review. ADMIN only,
     * mirrors {@link #downloadExcel}. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/orders/{id}/official-po/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        var download = integrationService.downloadPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.fileName()).build().toString())
                .body(download.bytes());
    }

    /** Gap Analysis C-2/C-3 (docs/gulliver-20260917-phase1-gap-analysis.md
     * 7章/8章): "Official POを再発行" - ADMIN only, human-confirmed (never
     * automatic - C-3). Refused (409) unless a reissue is actually pending -
     * see {@code OfficialPoIntegrationService#reissue}'s Javadoc. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/official-po/reissue")
    public OfficialPoIntegrationResponse reissue(@PathVariable Long id) {
        return integrationService.reissue(id, currentUserProvider.currentUsername());
    }

    /** Gap Analysis C-2: Revision History - any authenticated user may view
     * it, matching every other read endpoint on this Controller. */
    @GetMapping("/api/orders/{id}/official-po/revisions")
    public java.util.List<com.glv.gsysportal.dto.response.OfficialPoRevisionHistoryEntry> revisionHistory(@PathVariable Long id) {
        return integrationService.getRevisionHistory(id);
    }
}
