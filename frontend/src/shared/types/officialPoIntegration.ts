// Mirrors backend com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse
// / OfficialPoPreflightResult / OfficialPoPreflightIssue (Phase 7-C2A).

export interface OfficialPoPreflightIssue {
  code: string
  severity: string
  /** Internal, always-English technical detail - never rendered as the
   * primary label (the Frontend resolves user-facing text via i18n keyed on
   * `code`, same convention as errorCode). */
  message: string
  skuCode: string | null
}

export interface OfficialPoPreflightResult {
  result: string
  issues: OfficialPoPreflightIssue[]
}

export interface OfficialPoIntegration {
  orderId: number
  revisionNo: number
  status: string
  officialPoNo: string | null
  requestedBy: string | null
  /** i18n localization audit: prefer this for display, fall back to
   * requestedBy when null (matches AuditEventView.performedByDisplayName). */
  requestedByDisplayName: string | null
  requestedAt: string | null
  preflight: OfficialPoPreflightResult | null
  generatedAt: string | null
  submittedAt: string | null
  confirmedAt: string | null
  failedAt: string | null
  errorCode: string | null
  errorMessage: string | null
  /** Phase 7-C6 12章/13章: "NEW" | "UPDATE" | null. */
  integrationIntent: string | null
  /** Phase 9-A: Excel-contract fields with no other home in the domain. */
  deliveryWeek: string | null
  deliveryDate: string | null
  shipVia: string | null
  shipTerm: string | null
  paymentTerm: string | null
  /** Phase 9-A: whether a generated Excel is currently available to download
   * (derived from generatedFileKey != null - the key itself is never sent
   * to the Frontend). */
  excelGenerated: boolean
  /** Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
   * whether a "G-OPS Standard Format" PDF is currently available to
   * download - independent of excelGenerated/status (PDF never drives the
   * Integration Status axis). */
  pdfGenerated: boolean
}

/** Mirrors backend OfficialPoImportConfirmationResponse (Phase 9-C). */
export interface OfficialPoImportConfirmationDiff {
  skuCode: string
  expectedQty: number | null
  actualQty: number | null
}

export interface OfficialPoImportConfirmationResult {
  matched: boolean
  reason: string | null
  details: OfficialPoImportConfirmationDiff[]
  integration: OfficialPoIntegration
}
