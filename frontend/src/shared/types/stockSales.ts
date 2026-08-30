// Mirrors backend com.glv.gsysportal.dto.response.StockSalesSummaryResponse
// (Phase 8-H, Stock/Sales Visibility Foundation).
//
// currentMonthSalesQty mirrors MS_STK.SOLD_QTY - the Tempostar order CSV-
// derived CURRENT MONTH CUMULATIVE shipment quantity (Phase 8-C). It is
// NEVER a daily figure, a rolling-window figure, or a Trend/History value -
// Legacy holds only this single current-month number. Never label this
// field "最近の販売数"/"過去30日販売数"/"Sales Trend"/"Daily Sales" anywhere
// in the UI (Phase 8-H 5章's explicit prohibition).
//
// currentStock mirrors the SAME 'XX' aggregate ms_stk row Order Candidate
// List/SKU Detail already read - it is NOT proven Source-identical to the
// per-warehouse MS_STK.STK_QTY rows Phase 8-G's Warehouse Stock reads
// (different granularity). Never merge or compare against Warehouse Stock
// values (13章) - Navigation to /warehouse-stock is fine, a Business Join
// is not.
export interface StockSalesSummary {
  sku: string
  itemName: string | null
  brandCode: string | null
  brandName: string | null
  supplierCode: string | null
  supplierName: string | null
  currentStock: number | null
  currentMonthSalesQty: number | null
  openPoQty: number | null
  openArrivalQty: number | null
  recommendedQty: number | null
  leadTime: string | null
  itemStatus: string | null
  updatedAt: string | null
}
