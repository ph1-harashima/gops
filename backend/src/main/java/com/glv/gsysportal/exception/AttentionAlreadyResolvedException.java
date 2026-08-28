package com.glv.gsysportal.exception;

/** Acknowledge Attention (implementation instructions Step 5 2章): only
 * ACTIVE Attentions can be acknowledged. A second Acknowledge attempt on an
 * already-resolved Attention is rejected outright (409), the same
 * idempotency-via-rejection pattern used by Confirm Order / Demo Send /
 * Confirm Supplier Response - never silently no-ops, never double-writes
 * Audit. */
public class AttentionAlreadyResolvedException extends RuntimeException {
    public AttentionAlreadyResolvedException(Long id) {
        super("Attention is already resolved: " + id);
    }
}
