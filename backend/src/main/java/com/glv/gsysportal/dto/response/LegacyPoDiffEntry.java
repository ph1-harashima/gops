package com.glv.gsysportal.dto.response;

/**
 * Phase 7-C6 7章: one Structured Diff entry between a Baseline
 * {@code LegacyPoSnapshot} and the current one. {@code field} is a plain
 * technical field name (e.g. {@code "status"}, {@code "orderedQty"},
 * {@code "line"}) - the Frontend resolves the user-facing label via i18n,
 * same convention as every other internal-code-over-the-wire field in this
 * codebase. {@code skuCode} is null for header-level entries.
 */
public record LegacyPoDiffEntry(
        String field,
        String skuCode,
        String baselineValue,
        String currentValue,
        String diffType) {

    public static final String TYPE_HEADER_CHANGED = "HEADER_CHANGED";
    public static final String TYPE_LINE_CHANGED = "LINE_CHANGED";
    public static final String TYPE_LINE_ADDED = "LINE_ADDED";
    public static final String TYPE_LINE_REMOVED = "LINE_REMOVED";
}
