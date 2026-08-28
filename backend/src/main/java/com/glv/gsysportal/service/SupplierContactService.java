package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.SupplierContact;
import com.glv.gsysportal.dto.request.SupplierContactRequest;
import com.glv.gsysportal.dto.response.SupplierContactResponse;
import com.glv.gsysportal.exception.BrandCodeNotFoundException;
import com.glv.gsysportal.exception.DuplicateSupplierContactException;
import com.glv.gsysportal.exception.InvalidContactTypeException;
import com.glv.gsysportal.exception.InvalidEmailFormatException;
import com.glv.gsysportal.exception.InvalidLanguageException;
import com.glv.gsysportal.exception.SupplierCodeNotFoundException;
import com.glv.gsysportal.exception.SupplierContactNotFoundException;
import com.glv.gsysportal.repository.legacy.OfficialPoPreflightReadRepository;
import com.glv.gsysportal.repository.prototype.SupplierContactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Phase 7-C3 2章/4章/12章/13章: Supplier Contact Master CRUD. ADMIN only
 * (enforced at the Controller). Backend-enforced Validation - never relies
 * on Frontend alone (4章).
 */
@Service
public class SupplierContactService {

    // Standard-enough email shape check - not RFC 5322 exhaustive, matches
    // this codebase's general preference for pragmatic, readable validation
    // over an exotic regex (see e.g. PoPreviewValidator's simplicity).
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> VALID_CONTACT_TYPES = Set.of(
            SupplierContact.CONTACT_TYPE_TO, SupplierContact.CONTACT_TYPE_CC);
    private static final Set<String> VALID_LANGUAGES = Set.of(
            SupplierContact.LANGUAGE_JA, SupplierContact.LANGUAGE_EN);

    private final SupplierContactRepository repository;
    private final OfficialPoPreflightReadRepository legacyMasterReadRepository;

    public SupplierContactService(SupplierContactRepository repository,
                                   OfficialPoPreflightReadRepository legacyMasterReadRepository) {
        this.repository = repository;
        this.legacyMasterReadRepository = legacyMasterReadRepository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<SupplierContactResponse> list() {
        return repository.findAllByOrderBySupplierCodeAscBrandCodeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public SupplierContactResponse create(SupplierContactRequest request, String performedBy) {
        validate(request);
        rejectDuplicate(request.supplierCode(), request.brandCode(), request.email());

        OffsetDateTime now = OffsetDateTime.now();
        SupplierContact contact = new SupplierContact();
        applyRequest(contact, request);
        contact.setCreatedBy(performedBy);
        contact.setCreatedAt(now);
        contact.setUpdatedBy(performedBy);
        contact.setUpdatedAt(now);

        return toResponse(repository.save(contact));
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public SupplierContactResponse update(Long id, SupplierContactRequest request, String performedBy) {
        SupplierContact contact = repository.findById(id).orElseThrow(() -> new SupplierContactNotFoundException(id));
        validate(request);
        // Only re-check for a duplicate if the identifying tuple actually
        // changed away from this row's own current values - otherwise a
        // routine "just editing contactName" update would spuriously
        // collide with itself.
        boolean identityChanged = !contact.getSupplierCode().equals(request.supplierCode())
                || !java.util.Objects.equals(contact.getBrandCode(), request.brandCode())
                || !contact.getEmail().equalsIgnoreCase(request.email());
        if (identityChanged && request.active()) {
            rejectDuplicate(request.supplierCode(), request.brandCode(), request.email());
        }

        applyRequest(contact, request);
        contact.setUpdatedBy(performedBy);
        contact.setUpdatedAt(OffsetDateTime.now());

        return toResponse(repository.save(contact));
    }

    private void applyRequest(SupplierContact contact, SupplierContactRequest request) {
        contact.setSupplierCode(request.supplierCode());
        contact.setBrandCode(request.brandCode());
        contact.setContactName(request.contactName());
        contact.setEmail(request.email());
        contact.setContactType(request.contactType());
        contact.setLanguage(request.language());
        contact.setRegion(request.region());
        contact.setProcurementType(request.procurementType());
        contact.setPrimary(request.primary());
        contact.setActive(request.active());
    }

    private void validate(SupplierContactRequest request) {
        if (!EMAIL_PATTERN.matcher(request.email()).matches()) {
            throw new InvalidEmailFormatException(request.email());
        }
        if (!VALID_CONTACT_TYPES.contains(request.contactType())) {
            throw new InvalidContactTypeException(request.contactType());
        }
        if (!VALID_LANGUAGES.contains(request.language())) {
            throw new InvalidLanguageException(request.language());
        }
        if (!legacyMasterReadRepository.supplierExists(request.supplierCode())) {
            throw new SupplierCodeNotFoundException(request.supplierCode());
        }
        if (request.brandCode() != null && legacyMasterReadRepository.findBrandName(request.brandCode()) == null) {
            throw new BrandCodeNotFoundException(request.brandCode());
        }
    }

    private void rejectDuplicate(String supplierCode, String brandCode, String email) {
        boolean duplicate = brandCode == null
                ? repository.existsBySupplierCodeAndBrandCodeIsNullAndEmailIgnoreCaseAndActiveTrue(supplierCode, email)
                : repository.existsBySupplierCodeAndBrandCodeAndEmailIgnoreCaseAndActiveTrue(supplierCode, brandCode, email);
        if (duplicate) {
            throw new DuplicateSupplierContactException(supplierCode, brandCode, email);
        }
    }

    private SupplierContactResponse toResponse(SupplierContact c) {
        return new SupplierContactResponse(
                c.getId(), c.getSupplierCode(), c.getBrandCode(), c.getContactName(), c.getEmail(),
                c.getContactType(), c.getLanguage(), c.getRegion(), c.getProcurementType(),
                c.isPrimary(), c.isActive(), c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt()
        );
    }
}
