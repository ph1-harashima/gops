// Mirrors backend com.glv.gsysportal.dto.response.DashboardResponse /
// DashboardBrandRow. Action/Operation Cockpit only - deliberately no Sales
// Trend / margin / turnover rate field exists anywhere in this shape.
export interface DashboardBrandRow {
  brandCode: string
  brandName: string
  candidateCount: number
  outOfStockCount: number
  draftCount: number
  awaitingSupplierCount: number
  attentionCount: number
}

export interface Dashboard {
  candidateCount: number
  outOfStockCount: number
  longTermOutOfStockCount: number
  draftCount: number
  awaitingSupplierCount: number
  attentionCount: number
  brands: DashboardBrandRow[]
}
