package com.glv.gsysportal.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Prototype PO No. (implementation instructions 6章): assigned ONLY at
 * Confirm Order time, never at Preview time. Backed by a Postgres SEQUENCE
 * ({@code prototype_po_no_seq}, V6 migration) rather than DraftNoGenerator's
 * random-plus-retry approach, because {@code nextval()} is atomic and
 * gap-tolerant under concurrent Confirm Order calls - two transactions can
 * never observe the same value, so no collision-retry loop is needed.
 *
 * Format: {@code PO-DEMO-<yyyyMMdd>-<seq, >=4 digits>}. The "-DEMO-" segment
 * keeps this unmistakably distinct from Legacy PO Number and is never
 * intended to resemble Legacy's own format.
 *
 * Runs via the JPA {@link EntityManager} for the Prototype persistence unit
 * (the only EntityManagerFactory bean in this application - Legacy is
 * JdbcTemplate-only, Technical Design 4.2) so the sequence allocation
 * participates in the same Prototype WRITE transaction as the rest of
 * Confirm Order.
 */
@Component
public class PrototypePoNoGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @PersistenceContext
    private EntityManager entityManager;

    public String generate(LocalDate onDate) {
        Number seq = (Number) entityManager.createNativeQuery("SELECT nextval('prototype_po_no_seq')").getSingleResult();
        return "PO-DEMO-" + DATE_FORMAT.format(onDate) + "-" + String.format("%04d", seq.longValue());
    }
}
