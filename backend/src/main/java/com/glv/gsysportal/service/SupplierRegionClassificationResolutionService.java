package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.SupplierRegionClassification;
import com.glv.gsysportal.repository.prototype.SupplierRegionClassificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
