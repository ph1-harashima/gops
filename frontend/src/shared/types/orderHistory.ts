// Mirrors backend com.glv.gsysportal.dto.response.OrderHistorySummaryResponse
// / OrderHistoryDetailResponse / OrderHistoryDetailLineView / AuditEventView.
import type { AttentionSummary } from './attention'

export interface OrderHistorySummary {
  id: number
  draftNo: string
  prototypePoNo: string | null
  orderDate: string | null
  supplierCode: string | null
  supplierName: string | null
  brandCode: string | null
  brandName: string | null
  skuCount: number
  totalOrderedQty: number
  totalAmount: number
  status: string
  activeAttentionTypes: string[]
  updatedAt: string
}

/** Recommended -> Ordered -> Confirmed 3-stage line. */
export interface OrderHistoryDetailLine {
  sku: string
  itemName: string
  recommendedQty: number
  orderedQty: number
  confirmedQty: number | null
  requestedDelivery: string | null
  confirmedDelivery: string | null
  attentions: AttentionSummary[]
}

export interface OrderHistoryDetail {
  id: number
  draftNo: string
  prototypePoNo: string | null
  supplierCode: string | null
  supplierName: string | null
  brandCode: string | null
  brandName: string | null
  orderDate: string | null
  requestedDelivery: string | null
  currency: string | null
  remark: string | null
  status: string
  totalQty: number
  totalAmount: number
  details: OrderHistoryDetailLine[]
  orderAttentions: AttentionSummary[]
  // Phase 7-H (EDI発注Workflow Foundation): null until the first Send.
  communicationChannel: 'EMAIL' | 'EDI' | null
  // Phase 9-D: Manufacturer Channel Master's resolved value - EMAIL/EDI/null
  // (unresolved, no Master row yet). "What SHOULD happen", independent of
  // communicationChannel above ("what actually happened" on a past Send).
  resolvedManufacturerChannel: 'EMAIL' | 'EDI' | null
  ediStatus: 'WAITING_INPUT' | 'COMPLETED' | null
  ediCompletedBy: string | null
  ediCompletedAt: string | null
}

export interface AuditEventItem {
  eventType: string
  portalOrderDetailId: number | null
  fieldName: string | null
  oldValue: string | null
  newValue: string | null
  performedBy: string
  /** Read-time lookup of performedBy's current display name; null if no
   * matching portal_user account exists (e.g. removed account) - fall back
   * to performedBy (the Login ID) for display in that case. */
  performedByDisplayName: string | null
  performedAt: string
}
