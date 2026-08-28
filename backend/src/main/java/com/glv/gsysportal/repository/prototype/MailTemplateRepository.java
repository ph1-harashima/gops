package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.MailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MailTemplateRepository extends JpaRepository<MailTemplate, Long> {

    List<MailTemplate> findAllByOrderByTemplateTypeAscSupplierCodeAscBrandCodeAsc();

    // Template Resolution's 3 specific-first tiers (7-C3 7章). Each tier is
    // its own derived-query method (rather than one query with OR'd null
    // checks) so "brandCode IS NULL" / "supplierCode IS NULL" are expressed
    // correctly - a plain equality parameter bound to null always evaluates
    // false in JPQL, never matching a NULL column.
    List<MailTemplate> findAllByTemplateTypeAndSupplierCodeAndBrandCodeAndLanguageAndActiveTrue(
            String templateType, String supplierCode, String brandCode, String language);

    List<MailTemplate> findAllByTemplateTypeAndSupplierCodeAndBrandCodeIsNullAndLanguageAndActiveTrue(
            String templateType, String supplierCode, String language);

    List<MailTemplate> findAllByTemplateTypeAndSupplierCodeIsNullAndBrandCodeIsNullAndLanguageAndActiveTrue(
            String templateType, String language);
}
