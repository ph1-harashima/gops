package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.response.ManufacturerCommunicationResponse;
import org.springframework.stereotype.Component;

/**
 * Requirements MD 14章 (Phase 0.5 Source Review confirmed: Legacy has no
 * Supplier PO mail feature to reuse - SYS_SEND_MAIL is internal-notification
 * only) / implementation instructions 4章: 9/17 Prototype uses fixed Demo
 * values only.
 *
 * The "to"/"cc" addresses use the {@code .invalid} TLD, reserved by RFC 2606
 * specifically for addresses guaranteed never to resolve or be deliverable -
 * this is not a real Supplier mail address fetched from anywhere, and this
 * class never connects to any mail transport (implementation instructions
 * 0章 external network prohibition; no Demo Send this Step either).
 */
@Component
public class DemoManufacturerCommunicationFactory {

    public ManufacturerCommunicationResponse build(PortalOrder order, String prototypePoNoForDisplay) {
        String to = "demo-supplier+" + order.getSupplierCode().toLowerCase() + "@example.invalid";
        String cc = "demo-purchasing@example.invalid";
        String poRef = prototypePoNoForDisplay != null ? prototypePoNoForDisplay : order.getDraftNo();
        String subject = "[Demo] Purchase Order " + poRef + " - " + order.getSupplierNameSnapshot();
        String body = "This is a Demo Mode message for the G-SYS Online Ordering Prototype.\n"
                + "No email has actually been sent.\n\n"
                + "PO Reference: " + poRef + "\n"
                + "Supplier: " + order.getSupplierNameSnapshot() + "\n"
                + "Brand: " + order.getBrandNameSnapshot() + "\n"
                + "Order Date: " + order.getOrderDate() + "\n"
                + "Requested Delivery: " + (order.getRequestedDelivery() != null ? order.getRequestedDelivery() : "-");
        String attachment = poRef + ".pdf";
        return new ManufacturerCommunicationResponse(to, cc, subject, body, attachment);
    }
}
