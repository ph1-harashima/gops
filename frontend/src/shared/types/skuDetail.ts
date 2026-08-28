// Mirrors backend com.glv.gsysportal.dto.response.SkuDetailResponse /
// SkuPoHistoryLine. No Sales Trend / 30-60-90 day / previous-month field
// exists anywhere in this shape (implementation instructions Step 5 4章).
export interface SkuPoHistoryLine {
  poNo: string
  orderDate: string | null
  status: string | null
  qty: number | null
  unitPrice: number | null
  currency: string | null
  supplierCode: string | null
  supplierName: string | null
}

export interface SkuDetail {
  sku: string
  itemName: string
  brandCode: string | null
  brandName: string | null
  supplierCode: string | null
  supplierName: string | null
  itemStatus: string | null
  currentStock: number | null
  safetyStock: number | null
  openPo: number | null
  openArrival: number | null
  monthlySales: number | null
  leadTime: string | null
  recommendedQty: number | null
  unitPrice: number | null
  currency: string | null
  dataSource: string
  poHistory: SkuPoHistoryLine[]
}
