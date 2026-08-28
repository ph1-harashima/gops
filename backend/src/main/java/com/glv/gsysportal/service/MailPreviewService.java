package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.MailTemplate;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalUser;
import com.glv.gsysportal.domain.SupplierContact;
import com.glv.gsysportal.dto.response.MailPreviewIssue;
import com.glv.gsysportal.dto.response.MailPreviewResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
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
 * Phase 7-C3 9章: Mail Preview - the only capability this Phase exposes (no
 * Send API exists, per 10章's Gate never being satisfiable this Phase -
 * Integration CONFIRMED requires 7-C2B+). Never sends real mail, never
 * touches SYS_SEND_MAIL, never opens an SMTP connection (19章).
 */
@Service
public class MailPreviewService {

    static final String CODE_OFFICIAL_PO_NO_NOT_ASSIGNED = "OFFICIAL_PO_NO_NOT_ASSIGNED";
    static final String CODE_SUPPLIER_CONTACT_NOT_FOUND = "SUPPLIER_CONTACT_NOT_FOUND";
    static final String CODE_ADMIN_CC_NOT_FOUND = "ADMIN_CC_NOT_FOUND";
    static final String CODE_MAIL_TEMPLATE_NOT_FOUND = "MAIL_TEMPLATE_NOT_FOUND";
    static final String CODE_MAIL_TEMPLATE_AMBIGUOUS = "MAIL_TEMPLATE_AMBIGUOUS";

    private final PortalOrderRepository portalOrderRepository;
    private final PortalUserRepository portalUserRepository;
    private final OfficialPoIntegrationRequestRepository integrationRequestRepository;
    private final SupplierContactResolutionService contactResolutionService;
    private final MailTemplateResolutionService templateResolutionService;
    private final AdminCcResolutionService adminCcResolutionService;
    private final MailTemplateRenderer renderer;
    private final AuditEventRepository auditEventRepository;

    public MailPreviewService(PortalOrderRepository portalOrderRepository,
                               PortalUserRepository portalUserRepository,
                               OfficialPoIntegrationRequestRepository integrationRequestRepository,
                               SupplierContactResolutionService contactResolutionService,
                               MailTemplateResolutionService templateResolutionService,
                               AdminCcResolutionService adminCcResolutionService,
                               MailTemplateRenderer renderer,
                               AuditEventRepository auditEventRepository) {
        this.portalOrderRepository = portalOrderRepository;
        this.portalUserRepository = portalUserRepository;
        this.integrationRequestRepository = integrationRequestRepository;
        this.contactResolutionService = contactResolutionService;
        this.templateResolutionService = templateResolutionService;
        this.adminCcResolutionService = adminCcResolutionService;
        this.renderer = renderer;
        this.auditEventRepository = auditEventRepository;
    }

    /** POST, not GET - mirrors PoPreviewController's own reasoning (the
     * Backend re-resolves Contact/Template/Admin-CC against current data on
     * every call, it is not a pure fetch). Available to any authenticated
     * user, same visibility as the Official PO Integration GET (7-C3 13章 -
     * viewing a Preview is not itself a destructive action). */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public MailPreviewResponse preview(Long orderId, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        PortalUser sender = portalUserRepository.findByUsername(performedBy)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + performedBy));

        List<MailPreviewIssue> issues = new ArrayList<>();

        String officialPoNo = order.getOfficialPoNo();
        if (officialPoNo == null) {
            issues.add(new MailPreviewIssue(CODE_OFFICIAL_PO_NO_NOT_ASSIGNED, MailPreviewIssue.SEVERITY_BLOCKED,
                    "Order " + orderId + " has no Official PO No. assigned yet (7-C2A's Gate) - the Subject/"
                            + "Body cannot embed a real PO No., so rendering is blocked rather than substituting "
                            + "the Portal-internal prototypePoNo."));
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
                    "No enabled ADMIN account has an email address set - the Admin CC Rule (7-C3 3章) has "
                            + "nothing to add. Not blocking (CC is supplementary), but worth noting."));
        }

        // Template language: the first resolved TO Contact's language, since
        // that is who the mail is actually addressed to. Falls back to "ja"
        // when no TO Contact resolved at all - the overall Preview is
        // already BLOCKED in that case regardless of what this picks.
        String language = toContacts.isEmpty() ? SupplierContact.LANGUAGE_JA : toContacts.get(0).getLanguage();
        MailTemplateResolution templateResolution = templateResolutionService.resolve(
                MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER, order.getSupplierCode(), order.getBrandCode(), language);
        switch (templateResolution.kind()) {
            case NOT_FOUND -> issues.add(new MailPreviewIssue(CODE_MAIL_TEMPLATE_NOT_FOUND, MailPreviewIssue.SEVERITY_BLOCKED,
                    "No active PURCHASE_ORDER Mail Template resolved for supplier=" + order.getSupplierCode()
                            + " brand=" + order.getBrandCode() + " language=" + language
                            + " - register one in the Mail Template Master."));
            case AMBIGUOUS -> issues.add(new MailPreviewIssue(CODE_MAIL_TEMPLATE_AMBIGUOUS, MailPreviewIssue.SEVERITY_BLOCKED,
                    "Multiple active PURCHASE_ORDER Mail Templates matched at the same Resolution priority - "
                            + "cannot pick one automatically. Deactivate all but one."));
            case RESOLVED -> { /* no issue */ }
        }

        boolean blocked = issues.stream().anyMatch(i -> MailPreviewIssue.SEVERITY_BLOCKED.equals(i.severity()));

        String subject = null;
        String body = null;
        if (!blocked) {
            int revisionNo = integrationRequestRepository.findFirstByPortalOrderIdOrderByRevisionNoDesc(orderId)
                    .map(r -> r.getRevisionNo()).orElse(1);
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("supplierName", order.getSupplierNameSnapshot());
            variables.put("contactName", toContacts.get(0).getContactName());
            variables.put("poNo", officialPoNo);
            variables.put("orderDate", String.valueOf(order.getOrderDate()));
            variables.put("requestedDelivery", order.getRequestedDelivery() == null ? "-" : String.valueOf(order.getRequestedDelivery()));
            variables.put("senderName", sender.getDisplayName());
            variables.put("senderEmail", sender.getEmail() == null ? "" : sender.getEmail());
            variables.put("revisionNo", String.valueOf(revisionNo));

            MailTemplate template = templateResolution.template();
            subject = renderer.render(template.getSubjectTemplate(), variables);
            body = renderer.render(template.getBodyTemplate(), variables);
        }

        List<String> to = toContacts.stream().map(SupplierContact::getEmail).toList();
        List<String> cc = new ArrayList<>(ccContacts.stream().map(SupplierContact::getEmail).toList());
        cc.addAll(adminCcEmails);

        MailPreviewResponse.AttachmentSummary attachment = new MailPreviewResponse.AttachmentSummary(
                officialPoNo != null ? "OFFICIAL_PO_EXCEL" : null,
                officialPoNo != null ? officialPoNo + ".xlsx" : null,
                false);

        auditEventRepository.save(new AuditEvent(orderId, null, AuditEvent.MAIL_PREVIEW_GENERATED,
                null, null, blocked ? "BLOCKED" : "OK", performedBy, OffsetDateTime.now()));

        return new MailPreviewResponse(sender.getEmail(), to, cc, subject, body, attachment, issues);
    }
}
