package com.glv.gsysportal.dto.response;

/**
 * Phase 7-C2A 12章: one Preflight finding. {@code code} and {@code severity}
 * are internal codes (never Japanese text) - the Frontend resolves the
 * user-facing text via i18n keyed on {@code code}, same convention as every
 * other errorCode in this codebase (Requirements MD 30.12). {@code message}
 * is a secondary, always-English technical detail (e.g. for logs/ADMIN
 * troubleshooting) - it is NOT the user-facing string and the Frontend must
 * not render it directly as the primary label. {@code skuCode} is null for
 * Order-level issues (e.g. Supplier/Brand not found) and set for line-level
 * issues (e.g. an Item not found).
 */
public record OfficialPoPreflightIssue(
        String code,
        String severity,
        String message,
        String skuCode
) {
    public static final String SEVERITY_BLOCKED = "BLOCKED";
    public static final String SEVERITY_WARNING = "WARNING";
    public static final String SEVERITY_INFO = "INFO";
}
