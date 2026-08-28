package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.MailPreviewResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.MailPreviewService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phase 7-C3 9章: Mail Preview only - there is no Send endpoint this Phase
 * (10章's Gate). Any authenticated user may preview (same visibility as
 * Official PO Integration's GET, 7-C3 13章 - viewing is not destructive). */
@RestController
public class MailPreviewController {

    private final MailPreviewService mailPreviewService;
    private final CurrentUserProvider currentUserProvider;

    public MailPreviewController(MailPreviewService mailPreviewService, CurrentUserProvider currentUserProvider) {
        this.mailPreviewService = mailPreviewService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/orders/{id}/mail-preview")
    public MailPreviewResponse preview(@PathVariable Long id) {
        return mailPreviewService.preview(id, currentUserProvider.currentUsername());
    }
}
