package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.SupplierRegionClassification;
import com.glv.gsysportal.dto.request.SupplierRegionClassificationRequest;
import com.glv.gsysportal.dto.response.SupplierRegionClassificationResponse;
import com.glv.gsysportal.exception.BrandCodeNotFoundException;
import com.glv.gsysportal.exception.DuplicateSupplierRegionClassificationException;
import com.glv.gsysportal.exception.InvalidSupplierRegionClassificationException;
import com.glv.gsysportal.exception.SupplierCodeNotFoundException;
import com.glv.gsysportal.exception.SupplierRegionClassificationNotFoundException;
import com.glv.gsysportal.repository.legacy.OfficialPoPreflightReadRepository;
import com.glv.gsysportal.repository.prototype.SupplierRegionClassificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Gap Analysis §12 (docs/gulliver-20260917-phase1-gap-analysis.md 12章):
 * Region Classification Master CRUD - mirrors
 * {@code ManufacturerChannelService}'s shape exactly (ADMIN only, Backend-
 * enforced Validation, same Legacy READ ONLY Supplier/Brand existence check).
 */
@Service
public class SupplierRegionClassificationService {

    private static final Set<String> VALID_VALUES = Set.of(
            SupplierRegionClassification.DOMESTIC, SupplierRegionClassification.OVERSEAS);

    private final SupplierRegionClassificationRepository repository;
    private final OfficialPoPreflightReadRepository legacyMasterReadRepository;

    public SupplierRegionClassificationService(SupplierRegionClassificationRepository repository,
                                                OfficialPoPreflightReadRepository legacyMasterReadRepository) {
        this.repository = repository;
        this.legacyMasterReadRepository = legacyMasterReadRepository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<SupplierRegionClassificationResponse> list() {
        return repository.findAllByOrderBySupplierCodeAscBrandCodeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public SupplierRegionClassificationResponse create(SupplierRegionClassificationRequest request, String performedBy) {
        validate(request);
        rejectDuplicate(request.supplierCode(), request.brandCode());

        OffsetDateTime now = OffsetDateTime.now();
        SupplierRegionClassification classification = new SupplierRegionClassification();
        applyRequest(classification, request);
        classification.setCreatedBy(performedBy);
        classification.setCreatedAt(now);
        classification.setUpdatedBy(performedBy);
        classification.setUpdatedAt(now);

        return toResponse(repository.save(classification));
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public SupplierRegionClassificationResponse update(Long id, SupplierRegionClassificationRequest request, String performedBy) {
        SupplierRegionClassification classification = repository.findById(id)
                .orElseThrow(() -> new SupplierRegionClassificationNotFoundException(id));
        validate(request);
        boolean identityChanged = !classification.getSupplierCode().equals(request.supplierCode())
                || !Objects.equals(classification.getBrandCode(), request.brandCode());
        if (identityChanged && request.active()) {
            rejectDuplicate(request.supplierCode(), request.brandCode());
        }

        applyRequest(classification, request);
        classification.setUpdatedBy(performedBy);
        classification.setUpdatedAt(OffsetDateTime.now());

        return toResponse(repository.save(classification));
    }

    private void applyRequest(SupplierRegionClassification classification, SupplierRegionClassificationRequest request) {
        classification.setSupplierCode(request.supplierCode());
        classification.setBrandCode(request.brandCode());
        classification.setRegionClassification(request.regionClassification());
        classification.setActive(request.active());
    }

    private void validate(SupplierRegionClassificationRequest request) {
        if (!VALID_VALUES.contains(request.regionClassification())) {
            throw new InvalidSupplierRegionClassificationException(request.regionClassification());
        }
        if (!legacyMasterReadRepository.supplierExists(request.supplierCode())) {
            throw new SupplierCodeNotFoundException(request.supplierCode());
        }
        if (request.brandCode() != null && legacyMasterReadRepository.findBrandName(request.brandCode()) == null) {
            throw new BrandCodeNotFoundException(request.brandCode());
        }
    }

    private void rejectDuplicate(String supplierCode, String brandCode) {
        boolean duplicate = brandCode == null
                ? repository.existsBySupplierCodeAndBrandCodeIsNullAndActiveTrue(supplierCode)
                : repository.existsBySupplierCodeAndBrandCodeAndActiveTrue(supplierCode, brandCode);
        if (duplicate) {
            throw new DuplicateSupplierRegionClassificationException(supplierCode, brandCode);
        }
    }

    private SupplierRegionClassificationResponse toResponse(SupplierRegionClassification c) {
        return new SupplierRegionClassificationResponse(
                c.getId(), c.getSupplierCode(), c.getBrandCode(), c.getRegionClassification(), c.isActive(),
                c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt()
        );
    }
}
