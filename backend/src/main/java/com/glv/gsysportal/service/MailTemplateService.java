package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.MailTemplate;
import com.glv.gsysportal.dto.request.MailTemplateRequest;
import com.glv.gsysportal.dto.response.MailTemplateResponse;
import com.glv.gsysportal.exception.BrandCodeNotFoundException;
import com.glv.gsysportal.exception.InvalidLanguageException;
import com.glv.gsysportal.exception.InvalidTemplateTypeException;
import com.glv.gsysportal.exception.MailTemplateNotFoundException;
import com.glv.gsysportal.exception.SupplierCodeNotFoundException;
import com.glv.gsysportal.repository.legacy.OfficialPoPreflightReadRepository;
import com.glv.gsysportal.repository.prototype.MailTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/** Phase 7-C3 6章/12章/13章: Mail Template Master CRUD. ADMIN only (enforced
 * at the Controller). */
@Service
public class MailTemplateService {

    private static final Set<String> VALID_TEMPLATE_TYPES = Set.of(
            MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER, MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER_REVISION,
            MailTemplate.TEMPLATE_TYPE_FOLLOW_UP, MailTemplate.TEMPLATE_TYPE_CANCELLATION);
    private static final Set<String> VALID_LANGUAGES = Set.of(
            com.glv.gsysportal.domain.SupplierContact.LANGUAGE_JA, com.glv.gsysportal.domain.SupplierContact.LANGUAGE_EN);

    private final MailTemplateRepository repository;
    private final OfficialPoPreflightReadRepository legacyMasterReadRepository;

    public MailTemplateService(MailTemplateRepository repository,
                                OfficialPoPreflightReadRepository legacyMasterReadRepository) {
        this.repository = repository;
        this.legacyMasterReadRepository = legacyMasterReadRepository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<MailTemplateResponse> list() {
        return repository.findAllByOrderByTemplateTypeAscSupplierCodeAscBrandCodeAsc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public MailTemplateResponse create(MailTemplateRequest request, String performedBy) {
        validate(request);

        OffsetDateTime now = OffsetDateTime.now();
        MailTemplate template = new MailTemplate();
        applyRequest(template, request);
        template.setCreatedBy(performedBy);
        template.setCreatedAt(now);
        template.setUpdatedBy(performedBy);
        template.setUpdatedAt(now);

        return toResponse(repository.save(template));
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public MailTemplateResponse update(Long id, MailTemplateRequest request, String performedBy) {
        MailTemplate template = repository.findById(id).orElseThrow(() -> new MailTemplateNotFoundException(id));
        validate(request);

        applyRequest(template, request);
        template.setUpdatedBy(performedBy);
        template.setUpdatedAt(OffsetDateTime.now());

        return toResponse(repository.save(template));
    }

    private void applyRequest(MailTemplate template, MailTemplateRequest request) {
        template.setTemplateName(request.templateName());
        template.setTemplateType(request.templateType());
        template.setSupplierCode(request.supplierCode());
        template.setBrandCode(request.brandCode());
        template.setLanguage(request.language());
        template.setSubjectTemplate(request.subjectTemplate());
        template.setBodyTemplate(request.bodyTemplate());
        template.setAttachmentType(request.attachmentType());
        template.setActive(request.active());
    }

    private void validate(MailTemplateRequest request) {
        if (!VALID_TEMPLATE_TYPES.contains(request.templateType())) {
            throw new InvalidTemplateTypeException(request.templateType());
        }
        if (!VALID_LANGUAGES.contains(request.language())) {
            throw new InvalidLanguageException(request.language());
        }
        // supplierCode/brandCode are both optional (the "language default"
        // tier, 7-C3 7章) - a Brand-only combination without a Supplier
        // makes no sense in the Resolution model, so brandCode requires
        // supplierCode to be set too.
        if (request.supplierCode() != null && !legacyMasterReadRepository.supplierExists(request.supplierCode())) {
            throw new SupplierCodeNotFoundException(request.supplierCode());
        }
        if (request.brandCode() != null && legacyMasterReadRepository.findBrandName(request.brandCode()) == null) {
            throw new BrandCodeNotFoundException(request.brandCode());
        }
    }

    private MailTemplateResponse toResponse(MailTemplate t) {
        return new MailTemplateResponse(
                t.getId(), t.getTemplateName(), t.getTemplateType(), t.getSupplierCode(), t.getBrandCode(),
                t.getLanguage(), t.getSubjectTemplate(), t.getBodyTemplate(), t.getAttachmentType(), t.isActive(),
                t.getCreatedBy(), t.getCreatedAt(), t.getUpdatedBy(), t.getUpdatedAt()
        );
    }
}
