// Mirrors backend com.glv.gsysportal.dto.response.Arrival* records (Phase
// 8-G, Arrival Visibility Foundation). All 4 quantity fields
// (orderedQty/invoiceQty/arrivalQty/stockInQty) are plain Source Facts -
// there is no computed/derived field here (no "残数", no discrepancy flag,
// no color-coded status) - see ArrivalLineTable's own comment for why.

export interface ArrivalSummary {
  supplierCode: string | null
  supplierName: string | null
  poNumber: string
  invoiceNumber: string
  brandCode: string | null
  brandName: string | null
  blNumber: string | null
  vesselNumber: string | null
  orderedQty: number | null
  invoiceQty: number | null
  arrivalQty: number | null
  stockInQty: number | null
  etd: string | null
  eta: string | null
  etaWarehouse: string | null
  stockInDate: string | null
  warehouseReportStatus: string | null
  warehouseReportResult: string | null
}

export interface ArrivalLine {
  sku: string
  itemName: string | null
  orderedQty: number | null
  invoiceQty: number | null
  stockInQty: number | null
}

export interface ArrivalDetail {
  header: ArrivalSummary
  lines: ArrivalLine[]
}
