// Mirrors backend com.glv.gsysportal.dto.response.OrderCandidateResponse.
// Internal codes only (itemStatus, dataSource) - no Japanese text from the API
// (Requirements MD 30.12). Japanese labels are resolved via i18n on the client.
import type { ContactMethod, RestockSource, StockoutStatus } from './restockExpectation'

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
  // Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
  // gops-stage3-real-data-compatibility-review.md §5/§C6): real
  // discontinued-item signal, propagated straight from MS_ITEM.DISCON -
  // itemStatus alone rarely reflects it in real Production data.
  discon: boolean | null
  unitPrice: number | null
  currency: string | null
  dataSource: string
  // BR-09 (docs/gulliver-20260917-confirmed-business-rules.md): DOMESTIC /
  // OVERSEAS / null (unclassified) - lets the UI show "設定準備中"
  // specifically for a DOMESTIC Supplier whose recommendedQty is null,
  // rather than the generic "not available" fallback.
  regionClassification: 'DOMESTIC' | 'OVERSEAS' | null
  // Post-Freeze Business Refinement (re-audit doc §8/§11).
  restockSource: RestockSource
  restockDate: string | null
  // Post-Freeze Business Refinement 2
  // (docs/gops-manufacturer-stockout-information-management.md §15).
  stockoutStatus: StockoutStatus | null
  informationReceivedDate: string | null
  contactMethod: ContactMethod | null
  restockHasConflict: boolean
}

export interface OrderCandidateFilter {
  brandCode?: string
  supplierCode?: string
  keyword?: string
}
