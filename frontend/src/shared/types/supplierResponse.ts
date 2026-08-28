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
  isConfirmed: boolean
  attentions: AttentionSummary[]
  warningCodes: string[]
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
}

export interface SaveSupplierResponseRequest {
  responseDate?: string | null
  responseNote?: string | null
  details?: {
    detailId: number
    confirmedQty: number | null
    confirmedDelivery: string | null
    responseNote?: string | null
  }[]
}
