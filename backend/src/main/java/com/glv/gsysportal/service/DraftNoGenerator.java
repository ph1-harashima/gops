package com.glv.gsysportal.service;

import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Prototype Draft No., independent of Legacy PO Number (implementation
 * instructions 3章). Format: DRAFT-YYYYMMDD-XXXX. Not the Prototype PO No. -
 * that is assigned at Confirm Order time (out of scope this Step).
 */
@Component
public class DraftNoGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_ATTEMPTS = 20;

    private final PortalOrderRepository portalOrderRepository;
    private final SecureRandom random = new SecureRandom();

    public DraftNoGenerator(PortalOrderRepository portalOrderRepository) {
        this.portalOrderRepository = portalOrderRepository;
    }

    public String generate(LocalDate onDate) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = "DRAFT-" + DATE_FORMAT.format(onDate) + "-" + String.format("%04d", random.nextInt(10000));
            if (!portalOrderRepository.existsByDraftNo(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique Draft No. after " + MAX_ATTEMPTS + " attempts");
    }
}
