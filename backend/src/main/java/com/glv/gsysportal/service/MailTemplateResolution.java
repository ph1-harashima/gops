package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.MailTemplate;

/** Phase 7-C3 7章: outcome of {@link MailTemplateResolutionService#resolve}.
 * Exactly one of {@link #template()} is set for {@link #NOT_FOUND}/
 * {@link #AMBIGUOUS} - both leave it null, distinguished by {@code kind}. */
public record MailTemplateResolution(Kind kind, MailTemplate template) {

    public enum Kind { RESOLVED, NOT_FOUND, AMBIGUOUS }

    public static MailTemplateResolution resolved(MailTemplate template) {
        return new MailTemplateResolution(Kind.RESOLVED, template);
    }

    public static MailTemplateResolution notFound() {
        return new MailTemplateResolution(Kind.NOT_FOUND, null);
    }

    public static MailTemplateResolution ambiguous() {
        return new MailTemplateResolution(Kind.AMBIGUOUS, null);
    }
}
