# G-OPS Stage 5E: Production-Scale Targeted Remediation

Implements Stage 5D's (`gops-stage5d-production-scale-rca.md`) Root Cause
Consolidation (RC-A through RC-F) against the confirmed root causes only —
RC-D (FormulaParser `[AT]`/`[AJ]` pattern) is explicitly **held**, not
addressed, per this Stage's own instruction. No Production/UAT connection
was made; all Performance Revalidation ran against the same read-only
Snapshot (`gsys-prod-snapshot-20260916-ci`) Stage 5B/5C/5D already used, via
the unchanged `gops_snapshot_audit` SELECT-only account and the existing
`SafetyGuardEnvironmentPostProcessor` gate. No Production DB index was
added. No Snapshot write occurred. No Legacy G-SYS source was touched.

## 1. Scope: Implemented vs. Held

| Root Cause | Stage 5D Finding | Status |
|---|---|---|
| RC-A | Dashboard: unbounded full-catalog (116,842 SKU) computation + resolveRegion N+1 | **Implemented** |
| RC-B | CandidateListPage's unneeded `useDashboard()` dependency (Brand-name resolution only) | **Implemented** |
| RC-C | `RecommendedQtyReadQuery`/`latest_po` full-materialization before pagination | **Implemented** |
| RC-D | FormulaParser `[AT]`-only regex vs. real `[AJ]` pattern | **Held** (explicit instruction; needs Gulliver confirmation) |
| RC-E | 発注残 = Open PO + Open Arrival conflation at one call site | **Implemented** |
| RC-F | `DashboardService.getDashboard()` method-level `@Transactional` holding a Portal connection across the whole Legacy computation | **Implemented** |

## 2. RC-A: Dashboard Redesign

Stage 5D classified Dashboard's KPIs into Category A (pure SQL aggregate:
`outOfStockCount`, `longTermOutOfStockCount`) and Category B (formula-
dependent: `candidateCount`, requires `calc4`/FormulaParser evaluation per
SKU). This Stage implements that split rather than generating a full
`OrderCandidate` DTO catalog (116,842 rows, full `ms_comm` joins, full
formula evaluation) just to read a handful of aggregate numbers off it:

- **Category A** — `DashboardStockAggregateQuery.sql` computes
  `outOfStockCount`/`longTermOutOfStockCount` per Brand as a single SQL
  `GROUP BY`, no Java-side aggregation, no `ms_comm`/`ms_formula` join.
- **Category B** — `DashboardCandidateInputsQuery.sql` fetches only the
  columns `calc4` actually reads (no `ms_comm` join at all — brand/supplier
  name are never needed for this computation), then `candidateCount` is
  computed exactly as before: `calc4(row, region) → recommendedQty`,
  counted where `recommendedQty > 0`, grouped by Brand. **The definition of
  `candidateCount` was not changed.**
- The `resolveRegion` N+1 (up to 4 Portal round-trips per SKU, confirmed by
  Stage 5D as the dominant cost) is eliminated by
  `SupplierRegionClassificationResolutionService.loadAll()`: one Portal
  query loads the full active classification set into an in-memory
  `RegionClassificationLookup` (a `supplierCode|brandCode` map plus a
  `supplierCode`-only fallback map), reused for every row with zero
  additional Portal round-trips. `bulkRegionLookupAgreesWithSingleRowResolve`
  proves the bulk path agrees with the original per-row `resolve()` call.
- Brand display names are loaded once via `findAllBrandNames()` (one bulk
  `ms_comm` query) instead of per-row.

**candidateCount Product Semantics (this Stage's §3 constraint):** the
existing meaning — "count of SKUs where the current formula-evaluated
Recommended Qty is greater than zero" — was preserved exactly. No Product
Decision was required to hold this Stage's target: the speedup came
entirely from (a) not re-deriving Category A data through the formula path
and (b) eliminating the N+1, not from redefining what is counted.

## 3. RC-F: Dashboard Transaction Boundary

`DashboardService.getDashboard()`'s method-level `@Transactional` was
removed. Spring Data JPA repositories are already `@Transactional(readOnly
= true)` per call when no broader transaction is active, so each Portal
query now opens and commits its own short-lived connection instead of one
connection being reserved for the method's entire body — including the
multi-second-to-minute Legacy-side computation. The Portal pool's
`maximum-pool-size: 5` was **not** changed (explicitly prohibited by this
Stage's instruction; the fix is transaction-scope design, not pool size).
`getDashboardHasNoTransactionalAnnotation` guards this structurally via
reflection, since the pool-exhaustion symptom itself only reproduces under
real concurrent load against a slow Legacy source (see §8, Pool
Revalidation, for that live evidence).

## 4. RC-B: Candidate List Dashboard Dependency Removal

`CandidateListPage.tsx` no longer calls `useDashboard()`. Its only use was
resolving a Brand code to a display name for the Filter Chip. A new
dedicated, lightweight endpoint (`GET /api/brands`, backed by
`OrderCandidateService.findBrandNames()` → `LegacyStockReadRepository
.findAllBrandNames()`, the same bulk query RC-A's Dashboard redesign
introduced) replaces it via a new `useBrandNames()` React Query hook. A new
Playwright E2E test (`Candidate List never calls GET /api/dashboard`)
intercepts all network requests during a full Candidate List visit
(navigate → select Brand → confirm Filter Chip text) and asserts zero
requests to `/api/dashboard`.

## 5. RC-C: Query Redesign (No Production DB Index)

Stage 5D's `EXPLAIN ANALYZE` found `tr_po_dtl` has no index on `ITEM_CD`,
forcing the `latest_po` derived table's `ROW_NUMBER() OVER (PARTITION BY
item_cd ...)` window function to fully materialize (all ~183k rows of
`tr_po_dtl`) on every execution, and that a window function acts as an
optimizer fence blocking predicate pushdown — so filtering only the outer
query's `WHERE` clause does not help. This Stage's instruction explicitly
prohibited adding a `tr_po_dtl(ITEM_CD)` index, so the fix is query
structure only:

- **Two-step pagination** for Candidate List / Stock-Sales List:
  1. `RecommendedQtySkuIdsQuery.sql` (+ its `...CountQuery.sql`) resolves
     only the page's target `item_cd` set — filters (Brand/Supplier/
     keyword/min-max Stock/Sales), pagination (`LIMIT`/`OFFSET`), and
     sort — but selects no `ms_comm`/`ms_formula` columns.
  2. The full-detail `RecommendedQtyReadQuery.sql` is then called bound to
     `item_cd IN (:itemCodes)` for just that already-paginated small set.
  3. Critically, the `:itemCodes` filter is applied **inside the
     `latest_po` derived table's own `WHERE` clause**, not only the outer
     query — this pushes the filter to before the `ROW_NUMBER()` window
     function computes, so the window function materializes only the
     handful of rows belonging to the requested SKUs instead of all of
     `tr_po_dtl`, even with no index present.
- `LegacyStockReadRepository.findBySkus()` (used by SKU Detail) applies the
  same `item_cd IN (:itemCodes)` push-down instead of its previous
  behavior of fetching the unfiltered full query and filtering in Java.
- `StockSalesListQuery.sql`/`StockSalesListCountQuery.sql` are deleted —
  superseded by the two-step design.
- The safe `:hasItemCodes`/`:itemCodes` binding pattern (a boolean flag
  paired with an always-non-null placeholder collection) is used
  throughout, since `NamedParameterJdbcTemplate` cannot expand `IN
  (:param)` from a bare `null`.

**Sibling bug found during Performance Revalidation (§7):** the identical
"fetch everything via `search(null,null,null)`, filter in Java" anti-
pattern existed in `LegacyPriceReadRepository.findBySkus()` (used by SKU
Detail's Price Change data), not yet audited when the `LegacyStockRead
Repository` fix was made. It was the dominant remaining cost once the
stock-side fix alone left SKU Detail at ~37.4s. Fixed with the identical
`:hasItemCodes`/`:itemCodes` SQL-filter pattern in `PriceReadQuery.sql` and
`LegacyPriceReadRepository.java` — `search()`/`searchPage()`/
`countSearch()` behavior is byte-for-byte unchanged (their `:hasItemCodes`
always binds `false`), only `findBySkus()` now filters in SQL.

**Current Stock / Recommended Qty formulas: unchanged.** `WH_CD IN ('4',
'5','6','7','8','10','11','12','15')` and `LOGICAL_QTY = PHISICAL_QTY +
Open PO + Open Ship` (ARRIVAL excluded) were not touched — see §6.

## 6. RC-E: 発注残 (Open PO) / 入荷残 (Open Arrival) Separation

`OrderCandidateService.toResponse()` previously computed `openPo = row
.openPo() + row.openArrival()`. This conflated two distinct Legacy
concepts into one field. Fixed to `openPo = row.openPo()` directly; Open
Arrival remains its own separate value (`row.openArrival()`), already
exposed elsewhere (Stock/Sales `openArrivalQty`). This does not touch the
Recommended Qty calculation, which already only ever summed Open PO + Open
Ship (never Open Arrival) — confirmed unchanged in §7.

## 7. Controlled Snapshot Performance Revalidation

All measurements below are against the same Production-like Snapshot
(`gsys-prod-snapshot-20260916-ci`) Stage 5C/5D used, via the SELECT-only
`gops_snapshot_audit` account, read-only. No Production-specific business
values appear below — timings and aggregate/anonymized counts only.

| Area | Stage 5C/5D Before | After Stage 5E | Target (§9, not an SLA) |
|---|---|---|---|
| Dashboard | 351–407s (P0, background freeze) | ~15.5s | <5s — **not fully met** (see below) |
| Brand List | shared Dashboard's cause | ~15.5s (same call) | <5s — **not fully met** |
| Candidate — Small Brand | (fast, not a P0) | flat, <1s | — |
| Candidate — Very Large Brand, page 1 | up to ~17.2s | ~1.5–1.8s | <3s/page — **met** |
| Candidate — Very Large Brand, page 2 | up to ~17.2s | ~1.5–1.8s, distinct rows from page 1 (0 overlap) | <3s/page — **met** |
| Candidate — keyword search | (scaled with Brand size) | flat, consistent with above | — |
| SKU Detail | 174.5s+ (P0, timeout-adjacent) | 37.4s (stock-side fix only) → **1.6s** (after sibling `LegacyPriceReadRepository` fix) | <1s — **not fully met** (~1.6x over) |
| Stock/Sales | (scaled with Brand size, P1) | ~1.58s | no serious regression — **met** |
| Price Change | (not previously a flagged P0/P1) | ~205ms | no serious regression — **met** |
| Arrival | (not previously a flagged P0/P1) | ~283ms | no serious regression — **met** |

**Dashboard/Brand List (~15.5s) does not fully meet the <5s aspirational
target.** This is a real, substantial improvement (~23–26x over the 351–
407s baseline) achieved entirely through RC-A's Category A/B split and N+1
elimination — **not** by changing `candidateCount`'s definition, which was
explicitly prohibited. The remaining ~15.5s is the cost of Category B's
per-SKU formula evaluation (116,842 SKUs through `calc4`) still running
synchronously in the request path; going further (e.g., caching, async
pre-computation, materialized aggregate) would be a larger design change
than this Stage's instruction scoped, and is not attempted here.

**SKU Detail (~1.6s) does not fully meet the <1s target** but is a ~109x
improvement over the original 174.5s finding and a ~23x improvement over
the 37.4s intermediate (stock-only-fixed) state. The remaining time is the
two now-SQL-filtered but still separate Legacy queries (stock/PO-history
and price) plus Portal-side data for one SKU.

Per this Stage's explicit instruction (§16 Correctness Revalidation): a
performance improvement alone is not treated as PASS without the
correctness checks in §8 below also holding.

## 8. Correctness Revalidation (Against the Snapshot)

All re-confirmed directly against the Snapshot after the RC-C/RC-E changes,
using the same reference SKUs Stage 5C already established as ground
truth:

- **Current Stock, multi-warehouse**: unchanged formula
  (`WH_CD IN ('4','5','6','7','8','10','11','12','15')`); reference SKU's
  current stock value matches Stage 5C exactly.
- **Recommended Qty, ARR exclusion**: reference SKU's `recommendedQty`
  unchanged from Stage 5C after the 発注残 fix (only the separately-
  reported Open PO field changed, per RC-E — the calculation itself does
  not read Open Arrival, confirmed by inspection and by this value staying
  constant).
- **DISCON**: reference DISCON SKU still reports `discon = true`.
- **Multi-Supplier**: reference multi-supplier Brand still shows the same
  count of distinct suppliers and the same total row count as Stage 5C.
- **発注残 (RC-E)**: reference SKU's reported Open PO now reflects Open PO
  alone (previously included Open Arrival, which is now reported
  separately) — `recommendedQty` for that SKU is unchanged.
- Backed by unit/integration tests: `responseOpenPoExcludesOpenArrival()`,
  `openPoAndOpenArrivalRemainSeparateFields()`,
  `findBySkusReturnsExactlyTheRequestedSkusForAMultiSkuRequest()`,
  `findBySkusWithEmptyCollectionReturnsEmpty()`,
  `dashboardBrandRowCountsMatchDefinitionsForKnownFixtureBrand()`,
  `bulkRegionLookupAgreesWithSingleRowResolve()`.

## 9. RC-D: Explicitly Held

FormulaParser was not modified. Its confirmed `[AT]`/`[AJ]` pattern
coexistence (Stage 5D Finding) remains open, pending Gulliver
confirmation of which pattern(s) are authoritative in Production. This
Stage's performance changes do not suppress or hide FormulaParser errors:
the Snapshot backend log continued to emit the same class of
`FormulaParser` `ERROR`-level lines (e.g. "Expected 2-3 multipliers in
FORMULA_11, found 0") at the same points during Dashboard's
`candidateCount` computation and Candidate List rendering as before — this
was directly confirmed in the backend log during the §10 Pool Revalidation
run (a FormulaParser error line immediately preceded two successful
concurrent `/api/dashboard` completions), proving the errors still surface
normally rather than being swallowed by the redesigned query/computation
path.

## 10. Pool Revalidation

With the Portal pool's `maximum-pool-size: 5` unchanged, a concurrent
batch of 2× `/api/dashboard` + 1× Candidate List (paginated) + 1× SKU
Detail request was fired simultaneously against the Snapshot-pointed
backend. All four completed successfully (`200`). The backend log directly
recorded both concurrent Dashboard calls completing at `durationMs=14022`
and `durationMs=14000` respectively — near-identical timing, indicating
they ran concurrently without serializing behind each other or behind the
pool. A targeted grep of the full Snapshot-session backend log for
`"Connection is not available"` / `"not available, request timed out"`
(the exact error strings Stage 5C/5D's pool-exhaustion findings produced)
returned **zero matches**. RC-F's transaction-boundary fix is confirmed to
resolve the pool-exhaustion symptom under this test's concurrency level,
without changing the pool size.

## 11. Regression

Backend full test suite, frontend Vitest, TypeScript compilation, and
production build all pass. Targeted E2E (Dashboard, Brand List, Candidate,
SKU Detail, Stock/Sales, Price Change, Arrival) passed, including the new
RC-B network-interception test. Full E2E suite (233/233) passed once,
after Targeted E2E was green, per this Stage's instruction to run Full E2E
only a single time.

## 12. Summary

RC-A, RC-B, RC-C, RC-E, and RC-F are implemented and verified against the
Snapshot; RC-D remains explicitly held. Candidate List (all Brand sizes)
and 発注残 fully meet their targets/correctness requirements. Dashboard/
Brand List and SKU Detail are both massively improved (23–109x) but do not
fully reach their aspirational (<5s / <1s, explicitly non-SLA) targets —
reported honestly here rather than declared PASS on the strength of the
improvement alone, per this Stage's own explicit instruction that
performance improvement alone does not equal PASS.
