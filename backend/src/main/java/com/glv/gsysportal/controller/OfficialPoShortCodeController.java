package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.request.OfficialPoShortCodeRequest;
import com.glv.gsysportal.dto.response.OfficialPoShortCodeResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.OfficialPoShortCodeService;
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

/** BR-08 (docs/gulliver-20260917-confirmed-business-rules.md): Official PO
 * Short Code Master admin screen's API - ADMIN only, mirroring
 * {@link SupplierRegionClassificationController}'s own convention exactly. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class OfficialPoShortCodeController {

    private final OfficialPoShortCodeService service;
    private final CurrentUserProvider currentUserProvider;

    public OfficialPoShortCodeController(OfficialPoShortCodeService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/admin/official-po-short-codes")
    public List<OfficialPoShortCodeResponse> list() {
        return service.list();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/admin/official-po-short-codes")
    public OfficialPoShortCodeResponse create(@Valid @RequestBody OfficialPoShortCodeRequest request) {
        return service.create(request, currentUserProvider.currentUsername());
    }

    @PutMapping("/api/admin/official-po-short-codes/{id}")
    public OfficialPoShortCodeResponse update(@PathVariable Long id, @Valid @RequestBody OfficialPoShortCodeRequest request) {
        return service.update(id, request, currentUserProvider.currentUsername());
    }
}
