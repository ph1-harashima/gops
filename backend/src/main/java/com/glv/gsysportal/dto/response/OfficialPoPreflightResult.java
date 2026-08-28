package com.glv.gsysportal.dto.response;

import java.util.List;

/**
 * Phase 7-C2A 12章: structured Preflight outcome - never a raw Legacy
 * Exception surfaced to the UI. {@code result} is the aggregate verdict
 * (worst severity among {@code issues}, ignoring INFO-only issues so a
 * clean Order isn't downgraded from PASS just for the always-present
 * "PO No. not assigned yet" informational note - see
 * OfficialPoPreflightService).
 */
public record OfficialPoPreflightResult(
        String result,
        List<OfficialPoPreflightIssue> issues
) {
    public static final String RESULT_PASS = "PASS";
    public static final String RESULT_WARNING = "WARNING";
    public static final String RESULT_BLOCKED = "BLOCKED";
}
