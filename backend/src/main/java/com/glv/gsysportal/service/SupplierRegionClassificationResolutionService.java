package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.SupplierRegionClassification;
import com.glv.gsysportal.repository.prototype.SupplierRegionClassificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gap Analysis §12 (docs/gulliver-20260917-phase1-gap-analysis.md 12章):
 * resolves which region classification (DOMESTIC/OVERSEAS) a Supplier[+Brand]
 * belongs to - same priority as {@code ManufacturerChannelResolutionService}
 * (brand-specific match wins over supplier-only, no further fallback).
 * Returns {@code null} (never a guessed default) when no Master row exists
 * yet.
 */
@Service
public class SupplierRegionClassificationResolutionService {

    private final SupplierRegionClassificationRepository repository;

    public SupplierRegionClassificationResolutionService(SupplierRegionClassificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public String resolve(String supplierCode, String brandCode) {
        if (brandCode != null) {
            Optional<SupplierRegionClassification> brandSpecific =
                    repository.findFirstBySupplierCodeAndBrandCodeAndActiveTrue(supplierCode, brandCode);
            if (brandSpecific.isPresent()) {
                return brandSpecific.get().getRegionClassification();
            }
        }
        return repository.findFirstBySupplierCodeAndBrandCodeIsNullAndActiveTrue(supplierCode)
                .map(SupplierRegionClassification::getRegionClassification)
                .orElse(null);
    }

    /**
     * Stage 5E Targeted Remediation (RC-A, docs/real-data-audit/
     * gops-stage5e-targeted-remediation.md): bulk variant for List/Dashboard
     * callers that must resolve region for many rows in one request - a
     * single query regardless of row count, instead of {@link #resolve}
     * being called once (or, before this Stage, twice) per row (Stage 5D's
     * confirmed N+1 root cause - up to 4 Portal round-trips per row at
     * Dashboard's full-catalog scale). Same brand-specific-over-supplier-only
     * priority as {@link #resolve}, evaluated in memory instead of per-call.
     */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public RegionClassificationLookup loadAll() {
        List<SupplierRegionClassification> all = repository.findAllByActiveTrue();
        Map<String, String> brandSpecific = new HashMap<>();
        Map<String, String> supplierOnly = new HashMap<>();
        for (SupplierRegionClassification c : all) {
            if (c.getBrandCode() != null) {
                brandSpecific.put(brandSpecificKey(c.getSupplierCode(), c.getBrandCode()), c.getRegionClassification());
            } else {
                supplierOnly.put(c.getSupplierCode(), c.getRegionClassification());
            }
        }
        return new RegionClassificationLookup(brandSpecific, supplierOnly);
    }

    private static String brandSpecificKey(String supplierCode, String brandCode) {
        return supplierCode + "|" + brandCode;
    }

    /** In-memory snapshot built once by {@link #loadAll()} - mirrors {@link #resolve}'s
     * own priority (brand-specific wins, supplier-only fallback, null if neither). */
    public record RegionClassificationLookup(Map<String, String> brandSpecific, Map<String, String> supplierOnly) {
        public String resolve(String supplierCode, String brandCode) {
            if (brandCode != null) {
                String value = brandSpecific.get(supplierCode + "|" + brandCode);
                if (value != null) {
                    return value;
                }
            }
            return supplierOnly.get(supplierCode);
        }
    }
}
