package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.MailTemplate;
import com.glv.gsysportal.repository.prototype.MailTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Phase 7-C3 7章: Mail Template Resolution, specific-first:
 * (1) supplier+brand+language, (2) supplier+language (brandCode IS NULL),
 * (3) language default (supplierCode IS NULL AND brandCode IS NULL). The
 * first tier with any active match wins; if that tier has MORE THAN ONE
 * active match, Resolution is Ambiguous - a Template is one specific text
 * and can never be picked automatically among several equally-ranked
 * candidates (unlike Supplier Contact Resolution, which pluralizes
 * on purpose - see that class's Javadoc for why the two differ).
 */
@Service
public class MailTemplateResolutionService {

    private final MailTemplateRepository repository;

    public MailTemplateResolutionService(MailTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public MailTemplateResolution resolve(String templateType, String supplierCode, String brandCode, String language) {
        if (brandCode != null) {
            List<MailTemplate> tier = repository.findAllByTemplateTypeAndSupplierCodeAndBrandCodeAndLanguageAndActiveTrue(
                    templateType, supplierCode, brandCode, language);
            MailTemplateResolution fromTier = pickFromTier(tier);
            if (fromTier != null) {
                return fromTier;
            }
        }
        List<MailTemplate> supplierTier = repository
                .findAllByTemplateTypeAndSupplierCodeAndBrandCodeIsNullAndLanguageAndActiveTrue(templateType, supplierCode, language);
        MailTemplateResolution fromSupplierTier = pickFromTier(supplierTier);
        if (fromSupplierTier != null) {
            return fromSupplierTier;
        }

        List<MailTemplate> defaultTier = repository
                .findAllByTemplateTypeAndSupplierCodeIsNullAndBrandCodeIsNullAndLanguageAndActiveTrue(templateType, language);
        MailTemplateResolution fromDefaultTier = pickFromTier(defaultTier);
        return fromDefaultTier != null ? fromDefaultTier : MailTemplateResolution.notFound();
    }

    /** Null means "this tier was empty, fall through to the next tier" -
     * distinct from a real result (RESOLVED/AMBIGUOUS), which stops the
     * search. */
    private static MailTemplateResolution pickFromTier(List<MailTemplate> tier) {
        if (tier.isEmpty()) {
            return null;
        }
        if (tier.size() > 1) {
            return MailTemplateResolution.ambiguous();
        }
        return MailTemplateResolution.resolved(tier.get(0));
    }
}
