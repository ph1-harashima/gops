package com.glv.gsysportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Gap Analysis §11 (docs/gulliver-20260917-phase1-gap-analysis.md 11章):
 * Default CC Foundation - a Portal-wide singleton (id always 1, DB CHECK
 * constraint enforced). {@code defaultCc} only ever PREFILLS the send-time
 * CC Override field in the Frontend - it is never read by
 * {@code EmailSendService} itself and never automatically appended to a
 * sent Email. No "always CC this address" Business Rule exists anywhere in
 * this class or its callers (explicit customer instruction).
 */
@Entity
@Table(name = "portal_mail_settings")
@Getter
@Setter
@NoArgsConstructor
public class PortalMailSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    /** Comma-separated - display/prefill only, same convention as
     * OrderEmail.toAddresses/ccAddresses (never re-parsed for business
     * logic beyond splitting for display). */
    @Column(name = "default_cc")
    private String defaultCc;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
