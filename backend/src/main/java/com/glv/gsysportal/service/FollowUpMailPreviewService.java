package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.FollowUpCase;
import com.glv.gsysportal.domain.MailTemplate;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalUser;
import com.glv.gsysportal.domain.SupplierContact;
import com.glv.gsysportal.dto.response.MailPreviewIssue;
import com.glv.gsysportal.dto.response.MailPreviewResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.FollowUpCaseAlreadyClosedException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.PortalUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 7-C7A 13章: Follow-up Mail Preview - reuses the Phase 7-C3 Mail
 * Template Foundation ({@link MailTemplate#TEMPLATE_TYPE_FOLLOW_UP} already
 * existed as a stored-but-unresolved candidate type; this is the first Phase
 * that actually resolves against it). Preview only, same as
 * {@link MailPreviewService} - no Send API exists, real mail is never sent.
 */
@Service
public class FollowUpMailPreviewService {

    static final String CODE_OFFICIAL_PO_NO_NOT_ASSIGNED = "OFFICIAL_PO_NO_NOT_ASSIGNED";
    static final String CODE_SUPPLIER_CONTACT_NOT_FOUND = "SUPPLIER_CONTACT_NOT_FOUND";
    static final String CODE_ADMIN_CC_NOT_FOUND = "ADMIN_CC_NOT_FOUND";
    static final String CODE_MAIL_TEMPLATE_NOT_FOUND = "MAIL_TEMPLATE_NOT_FOUND";
    static final String CODE_MAIL_TEMPLATE_AMBIGUOUS = "MAIL_TEMPLATE_AMBIGUOUS";

    private final PortalOrderRepository portalOrderRepository;
    private final PortalUserRepository portalUserRepository;
    private final FollowUpCaseService followUpCaseService;
    private final SupplierContactResolutionService contactResolutionService;
    private final MailTemplateResolutionService templateResolutionService;
    private final AdminCcResolutionService adminCcResolutionService;
    private final MailTemplateRenderer renderer;
    private final AuditEventRepository auditEventRepository;

    public FollowUpMailPreviewService(PortalOrderRepository portalOrderRepository,
                                       PortalUserRepository portalUserRepository,
                                       FollowUpCaseService followUpCaseService,
                                       SupplierContactResolutionService contactResolutionService,
                                       MailTemplateResolutionService templateResolutionService,
                                       AdminCcResolutionService adminCcResolutionService,
                                       MailTemplateRenderer renderer,
                                       AuditEventRepository auditEventRepository) {
        this.portalOrderRepository = portalOrderRepository;
        this.portalUserRepository = portalUserRepository;
        this.followUpCaseService = followUpCaseService;
        this.contactResolutionService = contactResolutionService;
        this.templateResolutionService = templateResolutionService;
        this.adminCcResolutionService = adminCcResolutionService;
        this.renderer = renderer;
        this.auditEventRepository = auditEventRepository;
    }

    /** Any authenticated user (mirrors MailPreviewService's own visibility -
     * viewing a Preview is not itself destructive). A CLOSED Case can no
     * longer be Previewed (7-C7A 10章 - a Case is done once CLOSED). On a
     * non-BLOCKED render, the Case transitions OPEN -> INQUIRY_PREPARED
     * (idempotent past that point, see {@link FollowUpCaseService#markInquiryPrepared}). */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public MailPreviewResponse preview(Long orderId, Long followUpCaseId, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        FollowUpCase followUpCase = followUpCaseService.requireCase(followUpCaseId);
        if (FollowUpCase.STATUS_CLOSED.equals(followUpCase.getStatus())) {
            throw new FollowUpCaseAlreadyClosedException(followUpCaseId);
        }
        PortalUser sender = portalUserRepository.findByUsername(performedBy)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + performedBy));

        List<MailPreviewIssue> issues = new ArrayList<>();

        String officialPoNo = order.getOfficialPoNo();
        if (officialPoNo == null) {
            issues.add(new MailPreviewIssue(CODE_OFFICIAL_PO_NO_NOT_ASSIGNED, MailPreviewIssue.SEVERITY_BLOCKED,
                    "Order " + orderId + " has no Official PO No. assigned yet - the Subject/Body cannot embed a "
                            + "real PO No., so rendering is blocked."));
        }

        List<SupplierContact> toContacts = contactResolutionService.resolve(
                order.getSupplierCode(), order.getBrandCode(), SupplierContact.CONTACT_TYPE_TO);
        if (toContacts.isEmpty()) {
            issues.add(new MailPreviewIssue(CODE_SUPPLIER_CONTACT_NOT_FOUND, MailPreviewIssue.SEVERITY_BLOCKED,
                    "No active TO Supplier Contact resolved for supplier=" + order.getSupplierCode()
                            + " brand=" + order.getBrandCode() + " - register one in the Supplier Contact Master."));
        }
        List<SupplierContact> ccContacts = contactResolutionService.resolve(
                order.getSupplierCode(), order.getBrandCode(), SupplierContact.CONTACT_TYPE_CC);

        List<String> adminCcEmails = adminCcResolutionService.resolveAdminCcEmails();
        if (adminCcEmails.isEmpty()) {
            issues.add(new MailPreviewIssue(CODE_ADMIN_CC_NOT_FOUND, MailPreviewIssue.SEVERITY_WARNING,
                    "No enabled ADMIN account has an email address set - not blocking (CC is supplementary)."));
        }

        String language = toContacts.isEmpty() ? SupplierContact.LANGUAGE_JA : toContacts.get(0).getLanguage();
        MailTemplateResolution templateResolution = templateResolutionService.resolve(
                MailTemplate.TEMPLATE_TYPE_FOLLOW_UP, order.getSupplierCode(), order.getBrandCode(), language);
        switch (templateResolution.kind()) {
            case NOT_FOUND -> issues.add(new MailPreviewIssue(CODE_MAIL_TEMPLATE_NOT_FOUND, MailPreviewIssue.SEVERITY_BLOCKED,
                    "No active FOLLOW_UP Mail Template resolved for supplier=" + order.getSupplierCode()
                            + " brand=" + order.getBrandCode() + " language=" + language
                            + " - register one in the Mail Template Master."));
            case AMBIGUOUS -> issues.add(new MailPreviewIssue(CODE_MAIL_TEMPLATE_AMBIGUOUS, MailPreviewIssue.SEVERITY_BLOCKED,
                    "Multiple active FOLLOW_UP Mail Templates matched at the same Resolution priority - "
                            + "cannot pick one automatically. Deactivate all but one."));
            case RESOLVED -> { /* no issue */ }
        }

        boolean blocked = issues.stream().anyMatch(i -> MailPreviewIssue.SEVERITY_BLOCKED.equals(i.severity()));

        String subject = null;
        String body = null;
        if (!blocked) {
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("supplierName", order.getSupplierNameSnapshot());
            variables.put("contactName", toContacts.get(0).getContactName());
            variables.put("poNo", officialPoNo);
            variables.put("skuCode", followUpCase.getSkuCode() == null ? "-" : followUpCase.getSkuCode());
            variables.put("reason", followUpCase.getReason());
            variables.put("note", followUpCase.getNote() == null ? "" : followUpCase.getNote());
            variables.put("senderName", sender.getDisplayName());
            variables.put("senderEmail", sender.getEmail() == null ? "" : sender.getEmail());

            MailTemplate template = templateResolution.template();
            subject = renderer.render(template.getSubjectTemplate(), variables);
            body = renderer.render(template.getBodyTemplate(), variables);
        }

        List<String> to = toContacts.stream().map(SupplierContact::getEmail).toList();
        List<String> cc = new ArrayList<>(ccContacts.stream().map(SupplierContact::getEmail).toList());
        cc.addAll(adminCcEmails);

        MailPreviewResponse.AttachmentSummary attachment = new MailPreviewResponse.AttachmentSummary(null, null, false);

        OffsetDateTime now = OffsetDateTime.now();
        auditEventRepository.save(new AuditEvent(orderId, null, AuditEvent.MAIL_PREVIEW_GENERATED,
                null, null, blocked ? "BLOCKED" : "OK", performedBy, now));

        if (!blocked) {
            followUpCaseService.markInquiryPrepared(followUpCase, performedBy);
        }

        return new MailPreviewResponse(sender.getEmail(), to, cc, subject, body, attachment, issues);
    }
}
