// Mirrors backend com.glv.gsysportal.dto.response.WarehouseStock* records
// (Phase 8-G, Warehouse Stock Visibility Foundation). warehouseCode is shown
// as-is on screen - no Source Confirmed Japanese name exists for any code
// beyond '01' (Phase 8-F RE Document 8章/17章), so the UI must never invent
// one (WarehouseStockListPage renders it as a plain Chip with the bare code).

export interface WarehouseStockSummary {
  warehouseCode: string
  sku: string
  itemName: string | null
  brandCode: string | null
  brandName: string | null
  stockQty: number | null
  updatedAt: string | null
}

export interface WarehouseStockDetail {
  sku: string
  itemName: string | null
  brandCode: string | null
  brandName: string | null
  warehouses: WarehouseStockSummary[]
}
