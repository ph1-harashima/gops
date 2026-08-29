package com.glv.gsysportal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Phase 7-C6 3章: computes a stable SHA-256 digest of a Canonical Snapshot's
 * JSON serialization. <b>This is a Concurrency Detection digest, NOT a
 * Security boundary</b> (7-C6 3章's explicit distinction) - it exists purely
 * to answer "does the Snapshot I captured earlier still match G-SYS now?"
 * cheaply, without diffing every field on every Compare call. The actual
 * field-level Diff (when the fingerprint DOES differ) is
 * {@link LegacyPoDiffEngine}'s job, not this class's.
 */
final class LegacyPoFingerprintCalculator {

    private LegacyPoFingerprintCalculator() {
    }

    /** {@code canonicalJson} must already be a deterministic serialization of
     * a {@link LegacyPoSnapshot} (same object graph -> byte-identical JSON,
     * e.g. via a single shared {@code ObjectMapper} instance) - this method
     * itself does no canonicalization of its own. */
    static String compute(String canonicalJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm (JLS/JCA guarantee) - unreachable in practice.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
