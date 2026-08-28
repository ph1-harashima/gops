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
}

export interface AuditEventItem {
  eventType: string
  portalOrderDetailId: number | null
  fieldName: string | null
  oldValue: string | null
  newValue: string | null
  performedBy: string
  performedAt: string
}
