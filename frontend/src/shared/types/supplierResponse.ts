// Mirrors backend com.glv.gsysportal.dto.response.SupplierResponseView /
// SupplierResponseDetailView / SupplierResponseSummary. confirmedQty is
// `number | null` (never coerced) - null means not yet answered, 0 means an
// explicit zero answer (implementation instructions 11章).
import type { AttentionSummary } from './attention'

export interface SupplierResponseDetail {
  detailId: number
  sku: string
  itemName: string
  orderedQty: number
  confirmedQty: number | null
  requestedDelivery: string | null
  confirmedDelivery: string | null
  responseNote: string | null
  // Phase 7-C5 7章: explicitly selected only, never inferred from confirmedQty.
  // null = not yet selected, distinct from the explicit "UNKNOWN" choice.
  supplyStatus: string | null
  isConfirmed: boolean
  attentions: AttentionSummary[]
  warningCodes: string[]
}

// Phase 7-C5 8章: computed fresh on every read, never persisted.
export interface ResponseDifference {
  type: 'QUANTITY_CHANGED' | 'DELIVERY_CHANGED' | 'UNANSWERED'
  skuCode: string
  orderedValue: string | null
  confirmedValue: string | null
  severity: 'INFO' | 'WARNING'
}

export interface SupplierResponseSummary {
  totalCount: number
  answeredCount: number
  unansweredCount: number
  quantityChangedCount: number
  deliveryChangedCount: number
  zeroQtyCount: number
}

export interface SupplierResponse {
  orderId: number
  draftNo: string
  prototypePoNo: string | null
  supplierCode: string | null
  supplierName: string | null
  brandCode: string | null
  brandName: string | null
  orderDate: string | null
  status: string
  totalOrderedQty: number
  totalAmount: number
  responseDate: string | null
  responseNote: string | null
  responseStatus: string
  details: SupplierResponseDetail[]
  orderAttentions: AttentionSummary[]
  summary: SupplierResponseSummary
  // Phase 7-C5 21章: Revision-aware fields.
  responseId: number
  revisionNo: number
  isCurrent: boolean
  differences: ResponseDifference[]
  agreedBy: string | null
  agreedAt: string | null
  reopenedBy: string | null
  reopenedAt: string | null
  reopenReason: string | null
}

export interface SaveSupplierResponseRequest {
  responseDate?: string | null
  responseNote?: string | null
  details?: {
    detailId: number
    confirmedQty: number | null
    confirmedDelivery: string | null
    responseNote?: string | null
    supplyStatus?: string | null
  }[]
}

// Phase 7-C5 19章/20章: Order Detail's browsable Revision History.
export interface OrderRevisionLine {
  skuCode: string
  itemName: string
  recommendedQty: number
  orderedQty: number
  requestedDelivery: string | null
  unitPrice: number | null
}

export interface OrderRevisionSummary {
  revisionId: number
  revisionNo: number
  revisionType: 'INITIAL' | 'CORRECTION'
  reason: string | null
  createdBy: string
  createdAt: string
  lines: OrderRevisionLine[]
}

// Phase 7-C5 20章/21章: Order Detail's / Supplier Response's browsable
// Response History.
export interface SupplierResponseHistoryEntry {
  responseId: number
  revisionNo: number
  responseDate: string | null
  responseStatus: string
  isCurrent: boolean
  agreedBy: string | null
  agreedAt: string | null
  reopenedBy: string | null
  reopenedAt: string | null
  reopenReason: string | null
}

export interface CreateRevisionRequest {
  reason: string
  applyConfirmedValues: boolean
}
