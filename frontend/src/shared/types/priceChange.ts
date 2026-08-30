// Mirrors backend com.glv.gsysportal.dto.response/request.Price* records
// (Phase 8-B, Price Change Foundation). errorCode-style fields (status,
// concurrencyStatus) are internal English codes, resolved via i18n on the
// client (Requirements MD 30.12) - same convention as orderDraft.ts.

export const PRICE_CHANGE_STATUS_DRAFT = 'DRAFT'
export const PRICE_CHANGE_STATUS_SUBMITTED = 'SUBMITTED'
export const PRICE_CHANGE_STATUS_APPLIED = 'APPLIED'
export const PRICE_CHANGE_STATUS_FAILED = 'FAILED'
export const PRICE_CHANGE_STATUS_CANCELLED = 'CANCELLED'

export const CONCURRENCY_UNCHANGED = 'UNCHANGED'
export const CONCURRENCY_CHANGED = 'CHANGED'
export const CONCURRENCY_NOT_AVAILABLE = 'NOT_AVAILABLE'

export interface PriceChangeSetSummary {
  id: number
  status: string
  note: string | null
  createdByDisplayName: string | null
  createdAt: string
  detailCount: number
  updatedAt: string
}

export interface PriceChangeSetLine {
  detailId: number
  itemCd: string
  itemName: string | null
  brandCode: string | null
  itemGrpCd: string | null
  baselinePrcSellWTax: number | null
  currentPrcSellWTax: number | null
  costWTax: number | null
  marginAmount: number | null
  marginRate: number | null
  proposedPrcSellWTax: number | null
  proposedMarginAmount: number | null
  proposedMarginRate: number | null
  priceDifference: number | null
  percentageChange: number | null
  concurrencyStatus: string
}

export interface PriceChangeAuditEvent {
  eventType: string
  fieldName: string | null
  oldValue: string | null
  newValue: string | null
  performedByDisplayName: string | null
  performedAt: string
  note: string | null
}

export interface PriceChangeSetDetail {
  id: number
  status: string
  note: string | null
  createdByDisplayName: string | null
  createdAt: string
  updatedByDisplayName: string | null
  updatedAt: string
  details: PriceChangeSetLine[]
  auditTrail: PriceChangeAuditEvent[]
}

export interface PriceChangeCandidate {
  itemCd: string
  itemName: string | null
  brandCode: string | null
  brandName: string | null
  itemGrpCd: string | null
  itemStatus: string | null
  prcSellWTax: number | null
  costThisMonthAvg: number | null
}
