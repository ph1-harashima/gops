package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.request.CloseFollowUpCaseRequest;
import com.glv.gsysportal.dto.request.CreateFollowUpCaseRequest;
import com.glv.gsysportal.dto.request.CreateReorderDraftRequest;
import com.glv.gsysportal.dto.request.UpdateFollowUpCaseRequest;
import com.glv.gsysportal.dto.response.FollowUpCaseResponse;
import com.glv.gsysportal.dto.response.MailPreviewResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.FollowUpCaseService;
import com.glv.gsysportal.service.FollowUpMailPreviewService;
import com.glv.gsysportal.service.ReorderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 7-C7A 12章/13章/16章/20章: Follow-up Case Business Actions. Creation
 * and Note-update are open to any authenticated user (OPERATOR-permitted,
 * 20章); Close and Reorder Draft creation are ADMIN only (matches this
 * codebase's established convention that every Workflow-moving Action
 * beyond plain editing is ADMIN-gated - {@link OrderApprovalController} et al).
 */
@RestController
public class FollowUpCaseController {

    private final FollowUpCaseService followUpCaseService;
    private final FollowUpMailPreviewService followUpMailPreviewService;
    private final ReorderService reorderService;
    private final CurrentUserProvider currentUserProvider;

    public FollowUpCaseController(FollowUpCaseService followUpCaseService,
                                   FollowUpMailPreviewService followUpMailPreviewService,
                                   ReorderService reorderService,
                                   CurrentUserProvider currentUserProvider) {
        this.followUpCaseService = followUpCaseService;
        this.followUpMailPreviewService = followUpMailPreviewService;
        this.reorderService = reorderService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/orders/{id}/follow-up-cases")
    public List<FollowUpCaseResponse> list(@PathVariable Long id) {
        return followUpCaseService.list(id);
    }

    /** "問い合わせ対象にする" - always explicit, never auto-generated. */
    @PostMapping("/api/orders/{id}/follow-up-cases")
    public ResponseEntity<FollowUpCaseResponse> create(@PathVariable Long id, @RequestBody CreateFollowUpCaseRequest request) {
        FollowUpCaseResponse response = followUpCaseService.create(id, request, currentUserProvider.currentUsername());
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/api/follow-up-cases/{id}")
    public FollowUpCaseResponse updateNote(@PathVariable Long id, @RequestBody UpdateFollowUpCaseRequest request) {
        return followUpCaseService.updateNote(id, request, currentUserProvider.currentUsername());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/follow-up-cases/{id}/close")
    public FollowUpCaseResponse close(@PathVariable Long id, @RequestBody(required = false) CloseFollowUpCaseRequest request) {
        CloseFollowUpCaseRequest body = request != null ? request : new CloseFollowUpCaseRequest(null);
        return followUpCaseService.close(id, body, currentUserProvider.currentUsername());
    }

    /** Preview only - no Send API exists (7-C7A 13章). Any authenticated
     * user (mirrors MailPreviewController's own visibility). */
    @PostMapping("/api/orders/{id}/follow-up-cases/{caseId}/mail-preview")
    public MailPreviewResponse preview(@PathVariable Long id, @PathVariable Long caseId) {
        return followUpMailPreviewService.preview(id, caseId, currentUserProvider.currentUsername());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/follow-up-cases/{id}/reorder-draft")
    public ResponseEntity<OrderDraftResponse> createReorderDraft(@PathVariable Long id, @RequestBody CreateReorderDraftRequest request) {
        OrderDraftResponse response = reorderService.createReorderDraft(id, request, currentUserProvider.currentUsername());
        return ResponseEntity.status(201).body(response);
    }
}
