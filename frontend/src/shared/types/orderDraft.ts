// Mirrors backend com.glv.gsysportal.dto.response.OrderDraftResponse /
// OrderDraftDetailResponse. warningCodes are internal codes, resolved via
// i18n on the client (Requirements MD 30.12).
export interface OrderDraftDetail {
  id: number
  sku: string
  itemName: string
  recommendedQty: number
  orderQty: number
  unitPrice: number | null
  amount: number
  currentStock: number | null
  safetyStock: number | null
  openPo: number | null
  monthlySales: number | null
  leadTime: string | null
  itemStatus: string | null
  dataSource: string
  warningCodes: string[]
}

export interface OrderDraft {
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
  status: string
  remark: string | null
  totalQty: number
  totalAmount: number
  dataSource: string
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  details: OrderDraftDetail[]
  warningCodes: string[]
}

export interface CreateDraftRequest {
  skus: string[]
  orderDate?: string | null
  requestedDelivery?: string | null
  remark?: string | null
}

export interface UpdateDraftRequest {
  orderDate?: string | null
  requestedDelivery?: string | null
  remark?: string | null
  details?: { detailId: number; orderQty: number }[]
}

/** Mirrors GlobalExceptionHandler's {"errorCode": "..."} contract. */
export interface ApiErrorBody {
  errorCode: string
  supplierCodes?: string[]
  skus?: string[]
}
