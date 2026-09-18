package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.request.SupplierRegionClassificationRequest;
import com.glv.gsysportal.dto.response.SupplierRegionClassificationResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.SupplierRegionClassificationService;
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
 * Gap Analysis §12 (docs/gulliver-20260917-phase1-gap-analysis.md 12章):
 * Region Classification Master admin screen's API - ADMIN only, both read
 * and write, mirroring {@link ManufacturerChannelController}'s own
 * convention exactly.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class SupplierRegionClassificationController {

    private final SupplierRegionClassificationService service;
    private final CurrentUserProvider currentUserProvider;

    public SupplierRegionClassificationController(SupplierRegionClassificationService service,
                                                    CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/admin/supplier-region-classifications")
    public List<SupplierRegionClassificationResponse> list() {
        return service.list();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/admin/supplier-region-classifications")
    public SupplierRegionClassificationResponse create(@Valid @RequestBody SupplierRegionClassificationRequest request) {
        return service.create(request, currentUserProvider.currentUsername());
    }

    @PutMapping("/api/admin/supplier-region-classifications/{id}")
    public SupplierRegionClassificationResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRegionClassificationRequest request) {
        return service.update(id, request, currentUserProvider.currentUsername());
    }
}
