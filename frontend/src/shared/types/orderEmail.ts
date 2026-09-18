// Mirrors backend com.glv.gsysportal.dto.response.OrderEmailResponse (Phase 9-E).

export interface OrderEmail {
  orderId: number
  revisionNo: number
  status: 'SENT' | 'FAILED' | null
  to: string[]
  cc: string[]
  subject: string | null
  sentAt: string | null
  sentBy: string | null
  /** i18n localization audit: prefer this for display, fall back to sentBy
   * when null (matches AuditEventView.performedByDisplayName). */
  sentByDisplayName: string | null
  errorCode: string | null
  errorMessage: string | null
  retryCount: number
  /** Gap Analysis C-5 (docs/gulliver-20260917-phase1-gap-analysis.md 10章):
   * the Master-resolved addresses, always present regardless of whether an
   * Override was used - `to`/`cc` above stay "what was actually sent". */
  masterTo: string[]
  masterCc: string[]
  recipientOverrideUsed: boolean
}
