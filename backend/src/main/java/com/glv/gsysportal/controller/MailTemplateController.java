package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.request.MailTemplateRequest;
import com.glv.gsysportal.dto.response.MailTemplateResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.MailTemplateService;
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

/** Phase 7-C3 12章/13章: Mail Template Master admin screen's API. ADMIN only
 * (same rationale as {@link SupplierContactController}). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class MailTemplateController {

    private final MailTemplateService service;
    private final CurrentUserProvider currentUserProvider;

    public MailTemplateController(MailTemplateService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/admin/mail-templates")
    public List<MailTemplateResponse> list() {
        return service.list();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/admin/mail-templates")
    public MailTemplateResponse create(@Valid @RequestBody MailTemplateRequest request) {
        return service.create(request, currentUserProvider.currentUsername());
    }

    @PutMapping("/api/admin/mail-templates/{id}")
    public MailTemplateResponse update(@PathVariable Long id, @Valid @RequestBody MailTemplateRequest request) {
        return service.update(id, request, currentUserProvider.currentUsername());
    }
}
