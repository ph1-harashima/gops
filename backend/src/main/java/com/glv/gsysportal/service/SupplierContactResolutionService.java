package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.SupplierContact;
import com.glv.gsysportal.repository.prototype.SupplierContactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Phase 7-C3 5章: Supplier Contact Resolution. Priority: (1) supplier+brand
 * exact match, (2) supplier-only (brandCode IS NULL), (3) no further
 * fallback - never guessed. Only the first non-empty tier is used (the
 * supplier-only tier is never merged in alongside a non-empty brand-specific
 * tier).
 *
 * <p>Multiple ACTIVE rows within the winning tier are returned as multiple
 * recipients, not treated as "ambiguous" - Supplier-side multi-recipient
 * support is an explicit requirement (7-C3 2章: "Supplier側の複数宛先も将来
 * 拡張可能にする"). "Ambiguous" in the sense the design brief raises (7-C3
 * 5章) is deliberately reserved for Mail Template Resolution instead
 * (MailTemplateResolutionService), where a Template IS a single specific
 * text and cannot be pluralized the same way.
 */
@Service
public class SupplierContactResolutionService {

    private final SupplierContactRepository repository;

    public SupplierContactResolutionService(SupplierContactRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<SupplierContact> resolve(String supplierCode, String brandCode, String contactType) {
        if (brandCode != null) {
            List<SupplierContact> brandSpecific = repository
                    .findAllBySupplierCodeAndBrandCodeAndContactTypeAndActiveTrue(supplierCode, brandCode, contactType);
            if (!brandSpecific.isEmpty()) {
                return brandSpecific;
            }
        }
        return repository.findAllBySupplierCodeAndBrandCodeIsNullAndContactTypeAndActiveTrue(supplierCode, contactType);
    }
}
