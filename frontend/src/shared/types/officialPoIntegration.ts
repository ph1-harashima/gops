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
  requestedAt: string | null
  preflight: OfficialPoPreflightResult | null
  generatedAt: string | null
  submittedAt: string | null
  confirmedAt: string | null
  failedAt: string | null
  errorCode: string | null
  errorMessage: string | null
}
