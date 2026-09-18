package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.request.UpdatePortalMailSettingsRequest;
import com.glv.gsysportal.dto.response.PortalMailSettingsResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.PortalMailSettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gap Analysis §11 (docs/gulliver-20260917-phase1-gap-analysis.md 11章):
 * Default CC Foundation admin screen - ADMIN only, both read and write,
 * mirroring {@link ManufacturerChannelController}'s own convention.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class PortalMailSettingsController {

    private final PortalMailSettingsService service;
    private final CurrentUserProvider currentUserProvider;

    public PortalMailSettingsController(PortalMailSettingsService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/admin/mail-settings")
    public PortalMailSettingsResponse get() {
        return service.get();
    }

    @PutMapping("/api/admin/mail-settings")
    public PortalMailSettingsResponse update(@RequestBody UpdatePortalMailSettingsRequest body) {
        return service.update(body.defaultCc(), currentUserProvider.currentUsername());
    }
}
