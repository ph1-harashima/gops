package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.ManufacturerChannel;
import com.glv.gsysportal.repository.prototype.ManufacturerChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Phase 9-D: resolves which Channel (EMAIL/EDI) a Supplier[+Brand] actually
 * uses - same priority as {@code SupplierContactResolutionService} (brand-
 * specific match wins over supplier-only, no further fallback). Returns
 * {@code null} (never a guessed default) when no Master row exists yet -
 * the caller treats that as "unresolved" (design doc's explicit "自動推定は
 * 慎重にする" principle, already established for Integration Intent, 7-C6
 * 13章).
 */
@Service
public class ManufacturerChannelResolutionService {

    private final ManufacturerChannelRepository repository;

    public ManufacturerChannelResolutionService(ManufacturerChannelRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public String resolve(String supplierCode, String brandCode) {
        if (brandCode != null) {
            Optional<ManufacturerChannel> brandSpecific =
                    repository.findFirstBySupplierCodeAndBrandCodeAndActiveTrue(supplierCode, brandCode);
            if (brandSpecific.isPresent()) {
                return brandSpecific.get().getChannel();
            }
        }
        return repository.findFirstBySupplierCodeAndBrandCodeIsNullAndActiveTrue(supplierCode)
                .map(ManufacturerChannel::getChannel)
                .orElse(null);
    }
}
