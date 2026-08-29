// Phase 7-G (Dashboard欠品/長期欠品Drill-down明確化): a single client-side
// helper mirroring backend DashboardService.isOutOfStock/
// isLongTermOutOfStock EXACTLY (currentStock==0 / +openPo==0) - the SAME
// [PROTOTYPE DECISION] provisional Predicate, not a new definition. Used by
// every screen that needs to show this judgement (Candidate List, SKU
// Detail) so Dashboard count / List Filter / per-row Badge / Detail Badge
// can never drift out of sync with each other, unlike before (Dashboard
// counted this, but no screen ever showed which SKUs it counted).
//
// Deliberately independent from itemStatus (通常/廃番/発注停止 - Legacy
// Item Master's own field, MS_ITEM由来) - confirmed via Source
// (OrderCandidateResponse/SkuDetailResponse carry both fields separately,
// itemStatus is never read by isOutOfStock/isLongTermOutOfStock and vice
// versa). This module must never be merged into ItemStatusChip/itemStatus
// handling - they are two independent axes on the same SKU.

export type StockJudgement = 'NORMAL' | 'OUT_OF_STOCK' | 'LONG_TERM_OUT_OF_STOCK'

/** Mirrors DashboardService.isOutOfStock/isLongTermOutOfStock. 長期欠品 is
 * always a SUBSET of 欠品 (isLongTermOutOfStock requires isOutOfStock to
 * already be true) - never a disjoint or overlapping-but-distinct category
 * - so this returns the single most specific classification, never both.
 *
 * CAUTION on the `openPo` argument: DashboardService reads it from
 * OrderCandidateResponse, where OrderCandidateService.toResponse already
 * sums row.openPo()+row.openArrival() (Legacy 発注残 + 入荷予定数) into one
 * field. OrderCandidate.openPo (Candidate List) already carries that summed
 * value, so it can be passed straight through. SkuDetail keeps openPo/
 * openArrival as two separate raw fields (SkuDetailService does not sum
 * them) - callers passing SkuDetail data MUST pass
 * `(openPo ?? 0) + (openArrival ?? 0)`, not raw openPo alone, or this
 * screen's judgement can silently diverge from the Dashboard/Candidate
 * List's (see SkuDetailPage.tsx for the live example). */
export function computeStockJudgement(currentStock: number | null | undefined, openPo: number | null | undefined): StockJudgement {
  const isOutOfStock = currentStock === 0
  if (!isOutOfStock) return 'NORMAL'
  const isLongTermOutOfStock = openPo == null || openPo === 0
  return isLongTermOutOfStock ? 'LONG_TERM_OUT_OF_STOCK' : 'OUT_OF_STOCK'
}
