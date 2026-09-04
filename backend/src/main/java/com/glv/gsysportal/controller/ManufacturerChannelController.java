package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.request.ManufacturerChannelRequest;
import com.glv.gsysportal.dto.response.ManufacturerChannelResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.ManufacturerChannelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 9-D: Manufacturer Channel Master admin screen's API - ADMIN only,
 * both read and write, mirroring {@link SupplierContactController}'s own
 * convention exactly (same Master-CRUD shape as Supplier Contact / Mail
 * Template).
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class ManufacturerChannelController {

    private final ManufacturerChannelService service;
    private final CurrentUserProvider currentUserProvider;

    public ManufacturerChannelController(ManufacturerChannelService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/admin/manufacturer-channels")
    public List<ManufacturerChannelResponse> list() {
        return service.list();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/admin/manufacturer-channels")
    public ManufacturerChannelResponse create(@Valid @RequestBody ManufacturerChannelRequest request) {
        return service.create(request, currentUserProvider.currentUsername());
    }

    @PutMapping("/api/admin/manufacturer-channels/{id}")
    public ManufacturerChannelResponse update(@PathVariable Long id, @Valid @RequestBody ManufacturerChannelRequest request) {
        return service.update(id, request, currentUserProvider.currentUsername());
    }
}
