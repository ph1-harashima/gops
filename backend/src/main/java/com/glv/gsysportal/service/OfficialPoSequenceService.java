package com.glv.gsysportal.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * BR-08 (docs/gulliver-20260917-confirmed-business-rules.md): the 3-digit
 * Official PO sequence, counted per Supplier x Brand combination. Uses a
 * single atomic {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}
 * statement against {@code official_po_sequence} (V29) - Postgres takes a
 * row lock on the targeted (supplierCode, brandCode) key for the duration
 * of the statement, so two concurrent callers for the SAME pair are
 * strictly serialized (never both read the same "current" value and both
 * add 1 - the exact "MAX+1 without exclusion" race BR-08 explicitly
 * forbids), while different pairs never block each other. Same
 * EntityManager-native-query idiom as {@link PrototypePoNoGenerator}'s own
 * single global sequence, generalized to a per-key counter table since
 * Postgres SEQUENCE objects cannot be created dynamically per Supplier x
 * Brand pair.
 */
@Component
public class OfficialPoSequenceService {

    @PersistenceContext
    private EntityManager entityManager;

    public int nextSequence(String supplierCode, String brandCode) {
        Number seq = (Number) entityManager.createNativeQuery(
                        "INSERT INTO official_po_sequence (supplier_code, brand_code, next_seq, updated_at) "
                                + "VALUES (:supplierCode, :brandCode, 1, now()) "
                                + "ON CONFLICT (supplier_code, brand_code) "
                                + "DO UPDATE SET next_seq = official_po_sequence.next_seq + 1, updated_at = now() "
                                + "RETURNING next_seq")
                .setParameter("supplierCode", supplierCode)
                .setParameter("brandCode", brandCode)
                .getSingleResult();
        return seq.intValue();
    }
}
