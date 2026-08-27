// Mirrors backend com.glv.gsysportal.dto.response.OrderCandidateResponse.
// Internal codes only (itemStatus, dataSource) - no Japanese text from the API
// (Requirements MD 30.12). Japanese labels are resolved via i18n on the client.
export interface OrderCandidate {
  sku: string
  itemName: string
  brandCode: string
  brandName: string | null
  supplierCode: string | null
  supplierName: string | null
  currentStock: number | null
  safetyStock: number | null
  openPo: number | null
  monthlySales: number | null
  leadTime: string | null
  recommendedQty: number | null
  itemStatus: string | null
  unitPrice: number | null
  currency: string | null
  dataSource: string
}

export interface OrderCandidateFilter {
  brandCode?: string
  supplierCode?: string
  keyword?: string
}
