package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.OfficialPoShortCode;
import com.glv.gsysportal.dto.request.OfficialPoShortCodeRequest;
import com.glv.gsysportal.dto.response.OfficialPoShortCodeResponse;
import com.glv.gsysportal.exception.BrandCodeNotFoundException;
import com.glv.gsysportal.exception.DuplicateOfficialPoShortCodeException;
import com.glv.gsysportal.exception.InvalidOfficialPoShortCodeException;
import com.glv.gsysportal.exception.OfficialPoShortCodeNotFoundException;
import com.glv.gsysportal.exception.SupplierCodeNotFoundException;
import com.glv.gsysportal.repository.legacy.OfficialPoPreflightReadRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoShortCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * BR-08 (docs/gulliver-20260917-confirmed-business-rules.md): Official PO
 * Short Code Master CRUD - mirrors {@code SupplierRegionClassificationService}'s
 * shape exactly (ADMIN only, Legacy READ ONLY existence check for the
 * underlying Supplier/Brand Code, active-only Uniqueness).
 */
@Service
public class OfficialPoShortCodeService {

    private static final Set<String> VALID_TYPES = Set.of(OfficialPoShortCode.TYPE_SUPPLIER, OfficialPoShortCode.TYPE_BRAND);

    private final OfficialPoShortCodeRepository repository;
    private final OfficialPoPreflightReadRepository legacyMasterReadRepository;

    public OfficialPoShortCodeService(OfficialPoShortCodeRepository repository,
                                       OfficialPoPreflightReadRepository legacyMasterReadRepository) {
        this.repository = repository;
        this.legacyMasterReadRepository = legacyMasterReadRepository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<OfficialPoShortCodeResponse> list() {
        return repository.findAllByOrderByCodeTypeAscBusinessCodeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public OfficialPoShortCodeResponse create(OfficialPoShortCodeRequest request, String performedBy) {
        validate(request);
        rejectDuplicate(request.codeType(), request.businessCode());

        OffsetDateTime now = OffsetDateTime.now();
        OfficialPoShortCode code = new OfficialPoShortCode();
        applyRequest(code, request);
        code.setCreatedBy(performedBy);
        code.setCreatedAt(now);
        code.setUpdatedBy(performedBy);
        code.setUpdatedAt(now);

        return toResponse(repository.save(code));
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public OfficialPoShortCodeResponse update(Long id, OfficialPoShortCodeRequest request, String performedBy) {
        OfficialPoShortCode code = repository.findById(id)
                .orElseThrow(() -> new OfficialPoShortCodeNotFoundException(id));
        validate(request);
        boolean identityChanged = !code.getCodeType().equals(request.codeType())
                || !Objects.equals(code.getBusinessCode(), request.businessCode());
        if (identityChanged && request.active()) {
            rejectDuplicate(request.codeType(), request.businessCode());
        }

        applyRequest(code, request);
        code.setUpdatedBy(performedBy);
        code.setUpdatedAt(OffsetDateTime.now());

        return toResponse(repository.save(code));
    }

    private void applyRequest(OfficialPoShortCode code, OfficialPoShortCodeRequest request) {
        code.setCodeType(request.codeType());
        code.setBusinessCode(request.businessCode());
        code.setShortCode(request.shortCode() == null ? null : request.shortCode().trim().toUpperCase());
        code.setActive(request.active());
    }

    private void validate(OfficialPoShortCodeRequest request) {
        if (!VALID_TYPES.contains(request.codeType())) {
            throw new InvalidOfficialPoShortCodeException("codeType must be SUPPLIER or BRAND: " + request.codeType());
        }
        if (request.shortCode() == null || request.shortCode().trim().length() != 3) {
            throw new InvalidOfficialPoShortCodeException("shortCode must be exactly 3 characters: " + request.shortCode());
        }
        if (OfficialPoShortCode.TYPE_SUPPLIER.equals(request.codeType())) {
            if (!legacyMasterReadRepository.supplierExists(request.businessCode())) {
                throw new SupplierCodeNotFoundException(request.businessCode());
            }
        } else if (legacyMasterReadRepository.findBrandName(request.businessCode()) == null) {
            throw new BrandCodeNotFoundException(request.businessCode());
        }
    }

    private void rejectDuplicate(String codeType, String businessCode) {
        if (repository.existsByCodeTypeAndBusinessCodeAndActiveTrue(codeType, businessCode)) {
            throw new DuplicateOfficialPoShortCodeException(codeType, businessCode);
        }
    }

    private OfficialPoShortCodeResponse toResponse(OfficialPoShortCode c) {
        return new OfficialPoShortCodeResponse(
                c.getId(), c.getCodeType(), c.getBusinessCode(), c.getShortCode(), c.isActive(),
                c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt()
        );
    }
}
