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
  /** Gap Analysis C-2/C-4: ACTIVE | SUPERSEDED | CANCELLED - the Document
   * lifecycle axis, separate from `status` above. */
  lifecycleStatus: string | null
  lifecycleReason: string | null
  /** Gap Analysis C-2/C-3: true when this ACTIVE Document was actually
   * issued and an Order correction has happened since - drives the "Official
   * POの再発行が必要です" banner and enables the Reissue button. */
  reissueRequired: boolean
  /** Signed Official PO Workflow: the Signature axis - NOT_REQUIRED | PENDING
   * | SIGNED, independent of status/lifecycleStatus above. */
  signatureStatus: string | null
  /** Whether a signed PDF is currently available for download - mirrors
   * pdfGenerated's idiom (never exposes the storage key itself). */
  signedPdfAvailable: boolean
  /** Manufacturer Send readiness: SIGNATURE_SIGNED on an ACTIVE Document.
   * Admin approval alone never implies this is true. */
  readyToSend: boolean
  signedAt: string | null
  signedBy: string | null
  signedByDisplayName: string | null
}

/** Gap Analysis C-2 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
 * one row per Official PO Integration Request ever created for an Order. */
export interface OfficialPoRevisionHistoryEntry {
  revisionNo: number
  createdAt: string | null
  createdBy: string | null
  createdByDisplayName: string | null
  officialPoNo: string | null
  integrationStatus: string
  excelGenerated: boolean
  pdfGenerated: boolean
  lifecycleStatus: string
  lifecycleReason: string | null
  lifecycleChangedBy: string | null
  lifecycleChangedAt: string | null
  emailSendStatus: string | null
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
