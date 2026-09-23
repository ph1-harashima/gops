package com.glv.gsysportal.service.integration;

/**
 * G-OPS Operational Workflow Realignment Phase C (docs/ux-audit/
 * gops-operational-workflow-realignment-implementation.md §7-2): storage
 * abstraction for the signed PDF - deliberately a STORE, not a directory
 * POLL. The prior Official PO reality audit confirmed the human signature
 * process is entirely offline today (nothing in Legacy source or G-OPS
 * code addresses it), and this Phase's own instructions explicitly forbid
 * inventing a Production directory path that has never been confirmed.
 * An upload-based flow sidesteps that entirely: the ADMIN who already has
 * the signed PDF in hand (having just signed it, by whatever offline
 * process) uploads it directly - there is no path/polling convention to
 * guess at all.
 *
 * <p>Mirrors {@link OfficialPoImportFolderAdapter}'s own one-Adapter-per-
 * concern shape, but this one has no Production implementation this Phase
 * - the real Production storage location for signed artifacts (S3? a
 * network share? something else?) is as unconfirmed as the Import
 * Folder's own path once was, and this Phase does not guess it. Only
 * {@link LocalFilesystemSignedOfficialPoAdapter} (local/demo/test) exists;
 * a Production implementation is deliberately deferred to a future,
 * Gulliver-confirmed Phase rather than invented here.
 */
public interface SignedOfficialPoAdapter {

    /** Stores the signed PDF bytes for one Order/Revision and returns an
     * opaque file key (same shape as {@code OfficialPoPdfStorageService}'s
     * own {@code store}/{@code load} key) - never a hard-coded path,
     * always keyed by (orderId, revisionNo) so multiple Revisions' signed
     * artifacts never collide. */
    String storeSignedPdf(Long orderId, int revisionNo, byte[] signedPdfBytes);

    byte[] loadSignedPdf(String fileKey);
}
