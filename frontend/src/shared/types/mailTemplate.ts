// Mirrors backend com.glv.gsysportal.dto.response.MailTemplateResponse /
// dto.request.MailTemplateRequest (Phase 7-C3).
export interface MailTemplate {
  id: number
  templateName: string
  templateType: string
  supplierCode: string | null
  brandCode: string | null
  language: string
  subjectTemplate: string
  bodyTemplate: string
  attachmentType: string | null
  active: boolean
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

export interface MailTemplateRequest {
  templateName: string
  templateType: string
  supplierCode: string | null
  brandCode: string | null
  language: string
  subjectTemplate: string
  bodyTemplate: string
  attachmentType: string | null
  active: boolean
}
