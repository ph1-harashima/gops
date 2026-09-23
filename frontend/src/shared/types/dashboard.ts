// Mirrors backend com.glv.gsysportal.dto.response.DashboardResponse /
// DashboardBrandRow. Action/Operation Cockpit only - deliberately no Sales
// Trend / margin / turnover rate field exists anywhere in this shape.
export interface DashboardBrandRow {
  brandCode: string
  brandName: string
  candidateCount: number
  outOfStockCount: number
  longTermOutOfStockCount: number
  draftCount: number
  awaitingSupplierCount: number
  attentionCount: number
}

export interface Dashboard {
  // Stage 5K (docs/real-data-audit/gops-stage5k-dashboard-read-model-implementation.md):
  // candidateCount/outOfStockCount/longTermOutOfStockCount (overall and
  // per-Brand) now come from a background Read Model Refresh, not a
  // live per-request calc4 - calculatedAt/initialized surface that.
  /** ISO instant the active Read Model version was published, or null
   * when `initialized` is false. */
  calculatedAt: string | null
  /** False only in the narrow window before the very first Refresh has
   * ever succeeded - every count below is 0 (not a real value) then. */
  initialized: boolean
  candidateCount: number
  outOfStockCount: number
  longTermOutOfStockCount: number
  draftCount: number
  /** Phase 7-C1 14章: ADMIN approval queue KPI. */
  pendingApprovalCount: number
  awaitingSupplierCount: number
  attentionCount: number
  /** Phase 7-C7A 19章: Open Follow-up Case count (OPEN + INQUIRY_PREPARED). */
  openFollowUpCaseCount: number
  /** Phase 8-J 11章/13章: Price Change Sets currently in DRAFT status. */
  priceChangeDraftCount: number
  /** G-OPS Operational Workflow Realignment Phase D: Approved orders whose
   * current Official PO Integration Request's Signature axis is PENDING
   * (a formal PDF exists, not yet signed). */
  signaturePendingCount: number
  /** Signed and still APPROVED (not yet actually sent) - mutually
   * exclusive with awaitingSupplierCount (which covers orders already
   * sent). */
  readyToSendCount: number
  brands: DashboardBrandRow[]
}
