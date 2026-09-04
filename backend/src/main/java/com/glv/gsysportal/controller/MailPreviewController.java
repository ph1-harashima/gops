package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.MailPreviewResponse;
import com.glv.gsysportal.dto.response.OrderEmailResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.EmailSendService;
import com.glv.gsysportal.service.MailPreviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phase 7-C3 9章: Mail Preview - any authenticated user may preview (same
 * visibility as Official PO Integration's GET, 7-C3 13章 - viewing is not
 * destructive). Phase 9-E adds the real Send Action (ADMIN only) and its
 * Status view, both reusing Preview's own resolution (see
 * {@code EmailSendService}'s Javadoc). */
@RestController
public class MailPreviewController {

    private final MailPreviewService mailPreviewService;
    private final EmailSendService emailSendService;
    private final CurrentUserProvider currentUserProvider;

    public MailPreviewController(MailPreviewService mailPreviewService,
                                  EmailSendService emailSendService,
                                  CurrentUserProvider currentUserProvider) {
        this.mailPreviewService = mailPreviewService;
        this.emailSendService = emailSendService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/orders/{id}/mail-preview")
    public MailPreviewResponse preview(@PathVariable Long id) {
        return mailPreviewService.preview(id, currentUserProvider.currentUsername());
    }

    /** Order Detail's Email Send status - any authenticated user. */
    @GetMapping("/api/orders/{id}/email")
    public OrderEmailResponse getEmailStatus(@PathVariable Long id) {
        return emailSendService.getStatus(id);
    }

    /** "送信" (Phase 9-E). ADMIN only. Idempotent/Retry-safe - see
     * {@code EmailSendService#send}'s Javadoc. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/email/send")
    public OrderEmailResponse sendEmail(@PathVariable Long id) {
        return emailSendService.send(id, currentUserProvider.currentUsername());
    }
}
