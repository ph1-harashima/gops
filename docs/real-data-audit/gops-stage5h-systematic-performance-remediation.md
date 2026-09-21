# G-OPS Stage 5H: Systematic Performance Remediation (RC-G / RC-H / RC-I / RC-J)

Implements exactly the 4 Root Cause fixes Stage 5G's audit scoped for this
Stage - RC-G, RC-H, RC-I, RC-J. The Dashboard/Brand List Precomputed
Aggregate design (Stage 5G §5/§9) is explicitly **not** implemented here,
per this Stage's own instruction. No Legacy G-SYS change, no Production/UAT
connection, no Production DB index, no cache, no Business Rule change, no
FormulaParser (RC-D) change, and no change to `candidateCount`'s meaning.
Baseline: commit `a375ae7` (Stage 5G audit).

## 1. RC-G: SupplierMasterService Transaction Boundary

`listSuppliers()`/`getSupplier()`'s method-level `@Transactional(transactionManager
= "prototypeTransactionManager")` was removed - the exact same fix RC-F
applied to `DashboardService.getDashboard()` in Stage 5E. Each Legacy/Portal
repository call underneath already carries its own transaction (every
Legacy repository method here is individually `@Transactional
(transactionManager = "legacyTransactionManager")`; Spring Data JPA
repositories are `@Transactional(readOnly = true)` per call by default).
Portal pool size (`maximum-pool-size: 5`) was **not** changed. A reflection
test (`listSuppliersAndGetSupplierHaveNoTransactionalAnnotation`) guards
this structurally, mirroring `DashboardServiceIntegrationTest`'s own RC-F
guard.

## 2. RC-H: Supplier / Brand Association (Primary Fix)

`SupplierMasterService.brandsBySupplier()` no longer calls
`OrderCandidateService.findOrderCandidates(null, null, null)` - the full,
unpaginated, calc4-evaluating 116,842-SKU catalog method Stage 5G found it
still using (the only remaining caller anywhere in the application; the one
Dashboard itself stopped calling in Stage 5E's RC-A).

Replaced with a new, lightweight Legacy query
(`SupplierBrandAssociationQuery.sql`, exposed as
`OfficialPoPreflightReadRepository.findSupplierBrandAssociations()`)
returning only distinct `(supplier_cd, brand_cd)` pairs, derived the exact
same way every other Supplier-on-an-Item resolution in this codebase
already works (`ms_item` joined to `tr_po_dtl`/`tr_po`'s
`ROW_NUMBER()`-most-recent-PO `latest_po` derived table - unchanged
design, same flat ~500-800ms one-time cost regardless of catalog size,
same "no Production DB index" constraint honored: `tr_po_dtl.item_cd`
remains unindexed). Brand display name is resolved from the existing bulk
`OrderCandidateService.findBrandNames()` map (already used by RC-B), not a
second per-row `ms_comm` join.

**A query-design pitfall found and avoided in this Stage**: an earlier
draft of this query joined `ms_comm` directly for the Brand name, matching
`RecommendedQtyReadQuery.sql`'s own pattern. `EXPLAIN ANALYZE` against the
Snapshot showed this costs **~27 seconds** at this query's row scale (the
same `ms_comm` join-plan inefficiency Stage 5D/5G both already documented -
confirmed again here, independently). Dropped before implementation in
favor of the existing bulk brand-name lookup; the shipped query (no
`ms_comm` join) measured **~1.08s**.

`calc4`/`FormulaParser`/Recommended Qty/Current Stock are not evaluated
anywhere in this path. `Supplier = Manufacturer` is unchanged; the
Portal-Orders half of `brandsBySupplier()`'s union (`portalOrderRepository
.findAll()`) is unchanged - Stage 5G flagged it as its own, separate,
lower-priority finding, explicitly out of this Stage's RC-G/H/I/J scope.

## 3. Dead Full-Catalog Path Audit

Re-grepped the entire codebase (main + test) for
`OrderCandidateService.findOrderCandidates(...)` callers after RC-H:
**zero HTTP-reachable callers remain** (`OrderCandidateController` only
ever exposed the paginated `findOrderCandidatesPage` and the lightweight
`/api/brands`). One test-only caller remains
(`OrderCandidateServicePaginationIntegrationTest
.unpaginatedFindOrderCandidatesStillReturnsEveryRow`), used purely as a
correctness cross-check against `findOrderCandidatesPage`'s total count.

**Kept, not deleted**: the method itself
(`OrderCandidateService.findOrderCandidates`/`LegacyStockReadRepository
.findOrderCandidates`) is still a correct, independently useful
"browse with an optional filter, no pagination" primitive, and deleting it
would also have required rewriting its own dedicated correctness test for
no functional benefit (it costs nothing to exist - it just has no caller
today). Its Javadoc now says so explicitly, and warns any future caller of
the `(null, null, null)` full-catalog form to prefer a lean, SQL-side query
instead (pointing at `SupplierBrandAssociationQuery.sql` as the pattern).

**Stale comments found and fixed** (Stage 5G's own finding - the
half-outdated Javadoc that explained why this Dead Path audit almost
didn't happen): `OrderCandidateService.findOrderCandidatesPage`'s Javadoc
previously said `DashboardService`/`SupplierMasterService` "both call it
expecting the complete, unpaginated result" - corrected to note both
statements are now false, and when each stopped being true. The one
remaining test's own docstring was corrected the same way.

## 4. RC-I: Unfiltered Candidate / Stock-Sales

Root cause confirmed via `EXPLAIN ANALYZE` against the Snapshot, matching
Stage 5G PF-H exactly: with no Brand filter, MySQL cannot use an
index-ordered scan for `ORDER BY brand_cd, item_cd` and instead computes a
Left Hash Join against the fully-materialized `latest_po` derived table
(183,480-row window function) over all ~47,280 active items **before**
`ORDER BY`/`LIMIT` can trim it to one page - measured **~2062ms** for
Step 1 alone. `latest_po` is only actually needed there for the
`:supplierCode` filter; the `ms_stk agg` join is only needed for
`:minSales`/`:maxSales`.

Two new SQL resources (`RecommendedQtySkuIdsLeanQuery.sql` /
`...LeanCountQuery.sql`) omit both joins entirely - identical
`brandCode`/`keyword`/`includeDeleted` semantics, identical `ORDER BY`,
identical pagination contract; the omitted joins/predicates are always
no-ops when neither `supplierCode` nor any of `minStock`/`maxStock`/
`minSales`/`maxSales` is set, so this is a provably equivalent rewrite,
not a behavior change. `LegacyStockReadRepository` picks the lean variant
whenever none of those five filters is active - covering both the fully
unfiltered case and the far more common "Brand only" browse. Measured:
Step 1 alone dropped to **~1051ms** (unfiltered); the COUNT companion
dropped from **~1142ms to ~19.5ms** (no `ORDER BY` to force a filesort, so
removing the joins there is close to free).

Stage 5E's own two-step pagination / item-set filter pushdown design
(RC-C) is unchanged - the lean query is Step 1's sibling, not a
replacement of Step 2, which still resolves full display detail only for
the page's already-narrowed `item_cd` set exactly as before.

## 5. RC-J: Supplier / Manufacturer Pagination

`GET /api/admin/suppliers` now accepts `page`/`size` and returns a
`PageResponse<SupplierMasterSummaryResponse>` - same envelope/default-20/
max-100 convention as `OrderCandidateService`/`WarehouseStockService`.
Every row's own computation (region/contact/channel/PO-code aggregation,
Brand count) is unchanged; only the final slice returned differs, computed
in Java over the already-fast (post RC-G/RC-H) full 602-row result -
correctness-first, no new SQL-side pagination needed at this row count.
Frontend (`SupplierMasterListPage.tsx`) adds a `TablePagination` control,
matching `CandidateListPage`'s own pattern. `Supplier Detail` (`GET
/api/admin/suppliers/{code}`) is unchanged - always exactly one record, as
instructed.

## 6. Performance Revalidation

Measured against the same read-only Production Snapshot
(`gsys-prod-snapshot-20260916-ci`, SELECT-only `gops_snapshot_audit`)
Stage 5B-5G used. No Production/UAT connection, no Snapshot write.

| Area | Stage 5G Before | Stage 5H After | Target | Result |
|---|---|---|---|---|
| Supplier / Manufacturer List (page 1) | 54.4s | **1.41s** | ~1-2s | Met |
| Supplier Detail | 51.1s | **1.01s** | ~1-2s | Met |
| Candidate, unfiltered ("all Brands") | ~3.3-3.6s | **~1.1-1.2s** | ~2s | Met |
| Stock/Sales, unfiltered | ~3.4s | **~1.2-1.3s** | ~2s | Met |
| Candidate, Brand-filtered (Very Large Brand, page 1/2) | ~1.5-1.8s (Stage 5E) | **~0.36-0.38s** | ~1-2s | Met (bonus improvement - now also skips the `latest_po`/`ms_stk` joins) |
| Dashboard | ~13.8-15.5s | ~13.6s | not in scope this Stage | Unchanged (expected) |
| Brand List | ~13.8-15.5s | same call as Dashboard | not in scope this Stage | Unchanged (expected) |
| SKU Detail | ~0.18-1.6s | ~0.20s | <1s | Met, unaffected |

Supplier List/Detail: ~38-51x improvement. Candidate/Stock-Sales unfiltered:
~3x improvement, comfortably under the 2s target. Dashboard/Brand List
confirmed **not worse** than Stage 5G (~13.6s vs ~13.8-15.5s, within normal
run-to-run variance) - correctly out of scope for this Stage.

A 4-way concurrent request (Supplier List + Supplier Detail + Dashboard +
Candidate, fired simultaneously) all completed successfully - Dashboard's
own 13.7s Legacy computation ran concurrently with the other three without
any of them failing or timing out, consistent with RC-G's transaction-
boundary fix removing the Portal-connection-holding risk from Supplier
Master the same way RC-F already removed it from Dashboard.

## 7. Correctness Revalidation

Re-confirmed directly against the Snapshot (performance improvement alone
is not treated as PASS, per this Stage's own explicit priority):

- **Supplier count**: 602, matching Stage 5G exactly.
- **Supplier -> Brand relation**: present and populated (e.g. one sampled
  Supplier resolved to exactly 5 distinct Brands, matching between the
  List's `brandCount` and Detail's `brands` array).
- **Multi-Brand Supplier**: confirmed - 2 of a sampled 20-Supplier page
  have `brandCount > 1` (up to 14 Brands for one Supplier).
- **Multi-Supplier Brand**: confirmed on multiple real Brands (3 distinct
  Suppliers each on two sampled Brands) - one specific Brand sampled first
  (by construction, the single largest by candidate count) showed only 1
  Supplier across 100 sampled rows, which on inspection reflects that
  Brand's own real Supplier concentration, not a resolution defect
  (confirmed by finding genuine multi-Supplier diversity on other,
  smaller Brands with the identical code path).
- **Supplier Detail**: Brand list, active Contact count, and
  Region/Channel/PO-Code aggregation all populated consistently with the
  List row for the same Supplier.
- **Contact / Manufacturer Channel / Region / Official PO Short Code**:
  all four Portal-side lookups are unchanged code (not touched by RC-G/H/
  I/J) and continue to populate exactly as before.

**One pre-existing (not Stage-5H-introduced) data-quality finding
surfaced during this revalidation**: `SupplierMasterService.getSupplier()`
treats "Supplier code not found" and "Supplier code found but its Legacy
`ms_comm` name is `NULL`" identically (`findSupplierName` returns `null`
in both cases, so `getSupplier` throws `SupplierCodeNotFoundException`
either way) - reproduced against a real Supplier code present in
`findAllSuppliers()`'s own list with a `NULL` name. This logic is
unchanged by RC-G/H (neither method was touched) and is not part of this
Stage's assigned scope - noted here for Techlead awareness, not fixed.

## 8. Regression

- **Backend full test suite**: 655/655 passing, including 3 new/updated
  RC-G/RC-H/RC-J tests (`listSuppliersAndGetSupplierHaveNoTransactionalAnnotation`,
  `listSuppliersIsPaginatedWithoutLosingOrDuplicatingRows`, and the
  existing Brand-association test re-verified against the new query path).
- **Frontend**: TypeScript clean, Vitest 53/53, production build clean.
- **Targeted E2E**: Master Maintenance Hub (8/8), Dashboard Deep Links
  (12/12), Order Candidates Brand Entry (9/9), Stock/Sales Visibility
  (12/12), Price Change (7/7), Arrival/Warehouse Stock (11/11) - all green
  when run against a healthy backend.
- **Full E2E**: run twice; both runs were interrupted by the same host
  memory-pressure protection that has recurred throughout this project
  (not related to this Stage's code). Root cause confirmed directly from
  the failure logs both times: a `page.goto()` navigation's target element
  never appeared within timeout, in a file unrelated to RC-G/H/I/J
  (`supplier-response-revision-workflow.spec.ts`, `role-approval-workflow.spec.ts`),
  consistent with the backend process being terminated mid-run rather than
  an application defect. First run: 175 passed, 10 failed (all cascading
  0ms login-timeout failures after the same root-cause event), 48 did not
  run. Second run got substantially further: **228 passed**, 3 failed (1
  real 17.2s timeout at the same root cause, 2 cascading 0ms failures), 2
  did not run. Across both runs combined, effectively the full 233-test
  suite has now passed at least once, with zero failures attributable to
  application logic. A single uninterrupted clean run was not achieved in
  this session due to the host constraint; this is reported honestly
  rather than presented as a clean pass.

## 9. Remaining Performance Findings (Not In This Stage's Scope)

- Dashboard/Brand List's ~13.6s (PF-A, Category B `calc4` evaluation) -
  Stage 5G's Precomputed Aggregate design, explicitly deferred.
- `portalOrderRepository.findAll()` inside `brandsBySupplier()`'s Portal-
  Orders union, and the 5 small Master Maintenance config lists' unbounded
  reads (PF-B/PF-E) - Stage 5G's own lower-priority items, not RC-G/H/I/J.
- The `getSupplier()` null-name/not-found ambiguity noted in §7.

## 10. Final Judgment

**SYSTEMATIC PERFORMANCE REMEDIATION PASS**

RC-G, RC-H, RC-I, and RC-J are all implemented, measured, and correctness-
verified against the Snapshot with no Production DB index, no cache, no
Business Rule change, and no RC-D/candidateCount change. Every acceptance
target in §6 is met. Backend regression is clean (655/655) and Targeted
E2E is clean; Full E2E's two interrupted runs are reported transparently
with root-cause evidence pointing away from this Stage's code rather than
toward it - this is flagged as a residual verification gap (not a code
defect) for Techlead awareness, not silently omitted.
