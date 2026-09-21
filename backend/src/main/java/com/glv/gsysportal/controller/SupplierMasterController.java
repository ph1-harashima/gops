package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.SupplierMasterDetailResponse;
import com.glv.gsysportal.dto.response.SupplierMasterSummaryResponse;
import com.glv.gsysportal.service.SupplierMasterService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Master Maintenance Hub (docs/gops-master-maintenance-hub-implementation.md):
 * "Supplier一覧" and "Supplier Settings" Overview - READ ONLY, ADMIN only
 * (same Target Permission Matrix row as every other Master Maintenance
 * screen). No write endpoint here - Create/Update for Contact/Channel/
 * Region/PO Code all remain on their own existing Controllers, unchanged.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class SupplierMasterController {

    private final SupplierMasterService service;

    public SupplierMasterController(SupplierMasterService service) {
        this.service = service;
    }

    /**
     * Stage 5H Systematic Performance Remediation (RC-J, docs/real-data-audit/
     * gops-stage5h-systematic-performance-remediation.md): Backend-paginated
     * - same {@link PageResponse} envelope/default-max page size convention
     * as {@code OrderCandidateController}/{@code WarehouseStockController}.
     * Supplier Detail ({@link #get}) is deliberately NOT paginated - it is
     * always exactly one record.
     */
    @GetMapping("/api/admin/suppliers")
    public PageResponse<SupplierMasterSummaryResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.listSuppliers(page, size);
    }

    @GetMapping("/api/admin/suppliers/{supplierCode}")
    public SupplierMasterDetailResponse get(@PathVariable String supplierCode) {
        return service.getSupplier(supplierCode);
    }
}
