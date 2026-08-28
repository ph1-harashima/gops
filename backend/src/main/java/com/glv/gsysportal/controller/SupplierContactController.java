package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.request.SupplierContactRequest;
import com.glv.gsysportal.dto.response.SupplierContactResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.SupplierContactService;
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
 * Phase 7-C3 12章/13章: Supplier Contact Master admin screen's API. ADMIN
 * only, both read and write (matches Phase 7-B's Target Permission Matrix -
 * "Master管理（Supplier Contact / Mail Template）" is ADMIN-only, no OPERATOR
 * row at all - docs/target-production-procurement-workflow.md 4章).
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class SupplierContactController {

    private final SupplierContactService service;
    private final CurrentUserProvider currentUserProvider;

    public SupplierContactController(SupplierContactService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/admin/supplier-contacts")
    public List<SupplierContactResponse> list() {
        return service.list();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/admin/supplier-contacts")
    public SupplierContactResponse create(@Valid @RequestBody SupplierContactRequest request) {
        return service.create(request, currentUserProvider.currentUsername());
    }

    @PutMapping("/api/admin/supplier-contacts/{id}")
    public SupplierContactResponse update(@PathVariable Long id, @Valid @RequestBody SupplierContactRequest request) {
        return service.update(id, request, currentUserProvider.currentUsername());
    }
}
