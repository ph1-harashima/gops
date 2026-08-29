// Mirrors backend com.glv.gsysportal.dto.response.FollowUpCaseResponse /
// dto.request.Create/Update/CloseFollowUpCaseRequest (Phase 7-C7A). Never
// auto-generated - always created by an explicit "問い合わせ対象にする"
// Business Action.
export interface FollowUpCase {
  id: number
  portalOrderId: number
  orderRevisionId: number | null
  officialPoNo: string | null
  skuCode: string | null
  status: 'OPEN' | 'INQUIRY_PREPARED' | 'CLOSED'
  reason: string
  note: string | null
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  closedBy: string | null
  closedAt: string | null
}

export interface CreateFollowUpCaseRequest {
  skuCode?: string | null
  reason: string
  note?: string | null
}

export interface UpdateFollowUpCaseRequest {
  note: string | null
}

export interface CloseFollowUpCaseRequest {
  note?: string | null
}

export interface CreateReorderDraftRequest {
  skus: string[]
  reorderReason?: string | null
}
