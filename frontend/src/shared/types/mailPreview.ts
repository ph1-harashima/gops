// Mirrors backend com.glv.gsysportal.dto.response.MailPreviewResponse /
// MailPreviewIssue (Phase 7-C3). No Send API exists this Phase - Preview
// only.
export interface MailPreviewIssue {
  code: string
  severity: string
  message: string
}

export interface MailPreviewAttachmentSummary {
  type: string | null
  fileName: string | null
  generated: boolean
}

export interface MailPreview {
  from: string | null
  to: string[]
  cc: string[]
  subject: string | null
  body: string | null
  attachment: MailPreviewAttachmentSummary
  issues: MailPreviewIssue[]
}
