# G-OPS Stage 5G: Application-Wide Performance Architecture Audit

AUDIT / DESIGN ONLY. No code, SQL, migration, Production DB index, cache, or
Business Rule change was made in this Stage. All measurements below were
taken with the backend connected to the same read-only Production Snapshot
(`gsys-prod-snapshot-20260916-ci`, SELECT-only `gops_snapshot_audit`
account) Stage 5B–5F already used. No Production/UAT connection was made
and no Snapshot write occurred.

## 1. Executive Summary

Stage 5D/5E fixed a real, dominant root cause (RC-A: Dashboard's unbounded
full-catalog computation + N+1 region resolution) at exactly one call site
- `DashboardService`. This Stage's cross-application sweep found that
**the identical anti-pattern still exists, unaudited, at a second call
site**: `SupplierMasterService` (backing the "Supplier/Manufacturer List"
and "Supplier Detail" screens under Master Maintenance - the "メーカー一覧"
flagged as slow in Stage 5F's Human Walkthrough) calls the same
`OrderCandidateService.findOrderCandidates(null, null, null)` full-catalog
method Dashboard used to call before RC-A. Measured against the Snapshot:
**Supplier List = 54.4s, Supplier Detail (a single Supplier) = 51.1s** -
both worse than Dashboard ever was after RC-A, and the Detail screen pays
almost the identical cost as the List for viewing exactly one record.

Beyond this, the sweep found this call site *also* still holds a Portal
DB transaction open for its entire ~50s+ duration (the same RC-F
transaction-boundary anti-pattern already fixed for Dashboard), and *also*
never received RC-C's item-set-filter-pushdown fix, so it still fully
materializes `tr_po_dtl`'s window function on every call. In other words,
one service reintroduces three already-fixed anti-pattern families at
once, because it was never in scope for Stage 5D/5E's Dashboard-only
audit. No other HTTP-reachable call site of the unpaginated
`findOrderCandidates` exists.

A second, smaller finding: the unfiltered ("all Brands") Candidate List /
Stock-Sales List entry points measure ~3.3-3.6s, noticeably above the
~1.5-1.8s Stage 5E measured for a single Very Large Brand - a live, not
yet flagged, filter-pushdown gap in the same family as RC-C.

Everything else audited (Price Change, Arrival, Order History, SKU
Detail, Warehouse Stock, and every small Portal-side Master config list -
Supplier Contacts/Manufacturer Channels/Supplier Region Classifications/
Official PO Short Codes/Mail Templates) measured well within budget
(12ms-283ms) and showed no anti-pattern from the checklist in this audit's
scope. Stage 5E's own fixes (RC-B/RC-C/RC-E for the Legacy repository
layer) were re-verified intact and were NOT found duplicated or reverted
anywhere else in the codebase.

**No Production DB change is required for any finding in this report** -
every issue found is a service/query-structure issue fixable the same way
RC-A/RC-C were (bulk fetch, SQL-side aggregate, filter pushdown, request-
scoped transaction), not an indexing problem.

## 2. Screen / API Performance Inventory

Measured against the Snapshot, logged-in as `sys_admin`/`purchase01`
(local Demo/Validation credentials only), SELECT-only throughout.

| Screen / API | Response Time | Data Source | Data Volume | Pagination | Heavy Calc | N+1 | Fetch-all | Risk | Root Cause Family |
|---|---|---|---|---|---|---|---|---|---|
| Dashboard (`GET /api/dashboard`) | ~13.8-15.5s | Both | 116,842 SKU (Category B path) + all Portal orders | N/A (aggregate) | YES (calc4 × 116,842) | No (fixed, RC-A) | Portal orders: YES (`findAll()`) | P1 | PF-A, PF-B |
| Brand List (`OrderCandidateBrandListPage`, reuses `GET /api/dashboard`) | ~13.8-15.5s | Both | same as Dashboard | N/A | YES (same call) | No | same | P1 | PF-A, PF-B |
| Candidate List, Brand-filtered (Stage 5E baseline) | ~1.5-1.8s | Legacy | 1 page of a Very Large Brand | YES | Per-page only | No (fixed) | No (fixed, RC-C) | OK | - |
| Candidate List, **unfiltered ("all Brands")** | ~3.3-3.6s | Legacy | 1 page, but filter-eligible computation spans full catalog | YES | Per-page, but wider pre-filter scan | No | Partial (see §6) | P2 | PF-H (new, minor) |
| Stock/Sales List, unfiltered | ~3.4s | Legacy | same shape as Candidate unfiltered | YES | same | No | Partial | P2 | PF-H (new, minor) |
| SKU Detail (`GET /api/items/{sku}/ordering-context`) | ~0.18-1.6s (run-to-run) | Legacy | 1 SKU | N/A | No | No | No (fixed, RC-C) | OK | - |
| Price Change List | ~36ms | Portal | small (Portal-side drafts) | YES | No | No | No | OK | - |
| Arrival List | ~158ms | Legacy | 1 page | YES | No | No | No | OK | - |
| Order History List | ~126ms | Portal | 1 page | YES | No | No | No | OK | - |
| **Supplier/Manufacturer List** (`GET /api/admin/suppliers`) | **54.4s** | Both | 602 Suppliers, but pays full 116,842-SKU catalog cost | NO | YES (calc4 × 116,842, reused unmodified) | No (fixed) | YES (`findOrderCandidates(null,null,null)`, `portalOrderRepository.findAll()`) | **P0** | PF-A, PF-B, PF-E, PF-F, PF-H |
| **Supplier Detail** (`GET /api/admin/suppliers/{code}`) | **51.1s** | Both | 1 Supplier, but pays the same full-catalog cost as the List | N/A | Same as List | No | Same as List | **P0** | PF-A, PF-B, PF-F, PF-H |
| Warehouse Stock List | not separately re-measured this Stage (paginated + item-set-filtered since Phase 8-G; code-audited, no anti-pattern found) | Legacy | 1 page | YES | No | No | No | OK | - |
| Supplier Contacts (Master Maintenance) | ~21ms | Portal | small (Portal-side config) | NO | No | No | YES (`findAll`) | P2 (watch) | PF-B, PF-E |
| Manufacturer Channels (Master Maintenance) | ~16ms | Portal | small | NO | No | No | YES | P2 (watch) | PF-B, PF-E |
| Supplier Region Classifications (Master Maintenance) | ~13ms | Portal | small | NO | No | No | YES | P2 (watch) | PF-B, PF-E |
| Official PO Short Codes (Master Maintenance) | ~12ms | Portal | small | NO | No | No | YES | P2 (watch) | PF-B, PF-E |
| Mail Templates (Master Maintenance) | ~27ms | Portal | small (93KB payload) | NO | No | No | YES | P2 (watch) | PF-B, PF-E |
| FollowUp Case List (per Order) | not separately re-measured (code-audited) | Portal | tiny (per-Order, few cases) | N/A | No | Minor (per-distinct-username display-name lookup) | No | P2 (low severity) | PF-D (residual, bounded) |

Legacy/Portal DB query counts (from code reading, not instrumented
profiling - out of scope to add SQL logging in an audit-only Stage):
Dashboard/Brand List = 3 Legacy queries (stock aggregate, candidate
inputs, brand names) + 2-3 Portal queries (region lookup, orders
`findAll`); Supplier List/Detail = 2 Legacy queries (1 trivial supplier-
name query + 1 catastrophic full-catalog query) + 7-8 Portal queries (all
individually fast - the cost is entirely in the one Legacy query's
materialization plus its 116,842-row Java-side `calc4` evaluation, not in
query count).

## 3. Root Cause Families

Defined from what was actually found in this codebase, not a generic
checklist:

- **PF-A - Synchronous Full-Catalog Calculation**: a request thread runs
  `calc4`/formula evaluation over the entire 116,842-SKU Legacy catalog
  synchronously within the HTTP request. Present at: Dashboard/Brand List
  (Category B `candidateCount`, already reduced from 351-407s to ~15.5s by
  RC-A, but still fundamentally this pattern) and Supplier List/Detail
  (NOT reduced - still hits the pre-RC-A-equivalent full path).
- **PF-B - Unbounded Master/Portal Read**: `repository.findAll()` /
  `findAllBy...()` with no pagination and no row-count bound. Present at:
  `PortalOrderRepository.findAll()` (Dashboard, Supplier Master), and the
  5 Master Maintenance config lists (Contacts/Channels/Regions/Short
  Codes/Mail Templates). Currently cheap only because Portal-side table
  sizes are still small - a data-volume risk, not a today-latency risk,
  except where it compounds with PF-A (Supplier Master).
- **PF-C - Fetch-All Then Java Filter (Legacy repositories)**: the exact
  pattern Stage 5D found in `LegacyStockReadRepository`/
  `LegacyPriceReadRepository` and Stage 5E fixed. This audit re-scanned
  every Legacy repository (`ArrivalReadRepository`,
  `FulfillmentReadRepository`, `LegacyPoConcurrencyReadRepository`,
  `OfficialPoPreflightReadRepository`, `WarehouseStockReadRepository`) for
  a repeat - **none found**. Stage 5E's fix was not duplicated or
  reverted elsewhere.
- **PF-D - N+1 Lookup**: the resolveRegion N+1 Stage 5D found and Stage 5E
  fixed via bulk `RegionClassificationLookup` is confirmed still bulk
  (and confirmed reused, unmodified, even inside the still-slow
  `findOrderCandidates` path - it is not the cause of Supplier Master's
  slowness). One minor, bounded residual N+1 idiom exists
  (`FollowUpCaseService.list()`/`OrderHistoryService`'s per-distinct-
  username `portalUserRepository.findByUsername()` calls) - scope is
  always a handful of usernames per Order, not catalog-scale - P2.
- **PF-E - Missing Pagination**: Supplier/Manufacturer List (602 rows
  today, unpaginated, admin-only but real) and the 5 Master Maintenance
  config lists (currently small). Distinguished from PF-B by user-facing
  symptom: PF-B is a backend cost, PF-E is "this list has no page/size
  contract at all," which also blocks ever adding one without a Frontend
  change later.
- **PF-F - Cross-Datasource Transaction Hold**: Stage 5E's RC-F removed
  Dashboard's method-level `@Transactional` holding a Portal connection
  across the whole Legacy computation. This audit found the *same*
  pattern still present and unfixed at `SupplierMasterService
  .listSuppliers()`/`.getSupplier()` - both are
  `@Transactional(transactionManager = "prototypeTransactionManager")`
  at the method level, and both call into `findOrderCandidates`'s ~50s+
  Legacy-side work from inside that transaction. This is an operational
  risk beyond Supplier Master's own slowness: a Portal connection (pool
  max=5, unchanged per Stage 5E's own constraint) is reserved for the
  full ~50s duration on every concurrent Supplier List/Detail request,
  which can starve unrelated Portal-dependent requests exactly like the
  pre-RC-F Dashboard incident Stage 5C/5D documented.
- **PF-G - Frontend Unnecessary Dependency**: Stage 5E's RC-B removed
  `CandidateListPage`'s incidental `useDashboard()` call. This audit
  re-scanned every Frontend page for `useDashboard()` and any other
  unusually broad list-fetch hook - only `DashboardPage` itself and
  `OrderCandidateBrandListPage` (Brand List, which legitimately needs
  Dashboard's own brand breakdown - not a hidden/incidental dependency)
  remain. No new instance of this family found.
- **PF-H - Expensive SQL Materialization Without Filter Pushdown**: RC-C's
  fix (push the page's `item_cd` set into `latest_po`'s own `WHERE`
  before its `ROW_NUMBER()` window function runs) is proven in place for
  the paginated, filtered Candidate/Stock-Sales/SKU-Detail paths. Two
  gaps found: (1) `findOrderCandidates(null,null,null)` never goes
  through the two-step/item-set design at all (`hasItemCodes=false`), so
  it still fully materializes `tr_po_dtl`'s window function (183,480 rows)
  on every call - part of why Supplier Master is as slow as it is; (2) the
  unfiltered ("all Brands") Candidate/Stock-Sales List entry points
  (~3.3-3.6s vs ~1.5-1.8s for a filtered Very Large Brand) suggest the
  Step 1 SKU-ID query's own filter/aggregate work is not free even before
  `LIMIT`/`OFFSET` shrinks the result when no Brand/Supplier narrows the
  `WHERE` clause - a smaller, live version of the same family.

## 4. Manufacturer List (Supplier Master) Deep Dive

Business Rule confirmed unchanged in this audit: Supplier = Manufacturer;
this finding does not touch that mapping.

**Root cause, precisely located**: `SupplierMasterService.listSuppliers()`
and `.getSupplier()` both call a private helper `brandsBySupplier()`
(`SupplierMasterService.java:136-151`), which calls
`orderCandidateService.findOrderCandidates(null, null, null)` -
**the exact same unpaginated, full-catalog method Dashboard called before
RC-A**. Confirmed via a full-codebase grep: this is the *only* remaining
HTTP-reachable call site of that method anywhere in the application; the
`OrderCandidateController` itself only exposes the paginated
`findOrderCandidatesPage` and the lightweight `/api/brands` endpoint.

Why this was missed by Stage 5D/5E: `findOrderCandidatesPage`'s own
Javadoc (`OrderCandidateService.java:77-85`) explicitly documents
*"`findOrderCandidates` itself is deliberately left unchanged, since
`DashboardService` and `SupplierMasterService` both call it expecting the
complete, unpaginated result"* - this comment predates RC-A and was never
updated after RC-A actually removed `DashboardService`'s call. The
sentence rationalizing the method's continued existence is now half
stale, which is almost certainly why an unpaginated full-catalog method
still being called from *somewhere* did not read as a leftover bug during
Stage 5E's own review - the comment said it was expected.

**Answering the specific questions this Stage's instruction raised**:

- Is it a simple 602-row fetch? **No.** `findAllSuppliers()` (the actual
  602-row Legacy Supplier Master read) is fast and is not the bottleneck.
  The cost is entirely inside `brandsBySupplier()`'s
  `findOrderCandidates(null,null,null)` call, which computes
  Recommended-Qty-calculator (`calc4`) output for all 116,842 active SKUs
  just to derive a Supplier-to-Brand association map - work whose output
  size (a few hundred supplier→brand pairs) is utterly disproportionate
  to what was computed to get it.
- Are Contact/Region/Channel/PO-Short-Code/Brand fetched per-Supplier
  (N+1)? **No** - each of those 4 Portal lookups plus `brandsBySupplier`
  is fetched exactly ONCE for the whole list (not once per Supplier) and
  then filtered in memory per Supplier. This part of the design is
  correct and was not the cause.
- Does it round-trip between Portal and Legacy repeatedly? Not
  repeatedly in the N+1 sense, but it does hold one Portal transaction
  open for the entire duration of a ~50s+ Legacy computation (PF-F,
  §3) - a second, independent problem from the same call.
- Is full catalog fetch actually needed? **No** - the only thing
  `brandsBySupplier()` needs from the full-catalog computation is
  `(supplierCode, brandCode, brandName)` tuples. It does not need
  `current_stock`, `recommendedQty`, `openPo`, or any other `calc4`
  output at all - those fields are computed and then discarded
  unread by this caller. A SQL-side `SELECT DISTINCT supplier_cd, brand_cd,
  brand_name` (or reusing the existing bulk `findAllBrandNames()`-style
  query RC-A already introduced for Dashboard, paired with a lean
  supplier-brand association query) would answer this without touching
  `calc4`/`FormulaParser` at all.
- Is unneeded Detail information fetched at List time? The opposite
  problem exists: Detail (`getSupplier()`) re-pays the *entire* List's
  full-catalog cost for one Supplier, because it calls the same
  `brandsBySupplier()` helper with no Supplier-scoping. This is the
  single most disproportionate finding in this audit - a 51.1s cost to
  view one Supplier's settings page.

## 5. Dashboard / Brand List RCA (Forward-Looking, Design Only)

Stage 5E already fixed the N+1 and split Category A/B; the ~13.8-15.5s
remaining is Category B's per-request `calc4` evaluation over 116,842
SKUs, which cannot be reduced further by query restructuring alone
without changing when it runs. `candidateCount`'s business meaning is not
proposed to change in any option below.

Design options (not implemented, no Product Decision made here):

1. **Precomputed Dashboard/Brand Aggregate (Portal DB Read Model)**: a
   scheduled job (or an admin-triggered "Refresh" action) runs the exact
   same Category A/B computation Stage 5E already built, once, and writes
   the resulting per-Brand and Dashboard-wide numbers into a small Portal
   table. `GET /api/dashboard` then becomes a single fast Portal `SELECT`
   - the same computation, the same definition, just relocated off the
   request path.
2. **Last Calculated Timestamp**: the read model carries a
   `calculatedAt` value, shown in the UI, so staleness is visible rather
   than silently assumed-fresh - addresses the honesty concern a cached
   number otherwise raises.
3. **Manual Refresh**: an explicit "recalculate now" action for a user who
   needs the current-second value badly enough to wait ~15s for it,
   without forcing everyone to pay that cost on every Dashboard visit.
4. **Scheduled Refresh tied to Legacy batch/import timing**: if Legacy's
   own nightly batch is the only thing that actually changes
   `current_stock`/`open_po`/formula inputs, a refresh scheduled shortly
   after that batch completes would keep the read model "as fresh as the
   source data can legitimately be" without any polling waste - this
   needs Techlead/Gulliver confirmation of the actual batch schedule
   before it can be sized, which this Stage does not have.
5. **Event-driven refresh** is a weaker fit here than for Portal-only data
   (Legacy is read-only and external to this application, so there is no
   write event to hook) - listed for completeness but not recommended
   over (4).

None of these require a Product Decision about candidateCount's
*meaning* - only about acceptable staleness, which is a genuinely new
question this Stage surfaces rather than answers.

## 6. Other Latent Slow Screens

- **Unfiltered Candidate List / Stock-Sales List** (~3.3-3.6s): see PF-H.
  Not a regression of Stage 5E's own fix (which was measured with a Brand
  filter), but a real, currently-live gap the Stage 5E acceptance targets
  did not cover. Affects the "すべての発注候補を表示" (view-all) entry
  point and any Stock/Sales visit with no filter applied.
- **Supplier Master List/Detail**: covered in full in §4 - the most severe
  finding in this Stage.
- **Master Maintenance's 5 small config lists** (Contacts/Channels/
  Regions/Short Codes/Mail Templates): not slow today (12-27ms), but
  unpaginated `findAll()` reads with no ceiling - flagged as a watch item
  (PF-B/PF-E), not a current-risk item.
- Everything else audited (SKU Detail, Price Change, Arrival, Order
  History, Warehouse Stock) showed no anti-pattern from this Stage's
  checklist and measured well inside any reasonable budget.

## 7. Performance Budget (Proposed, Not a Formal SLA)

| Category | Target | Rationale |
|---|---|---|
| Dashboard / Navigation-level List | ~1s | First-thing-you-see; currently only Dashboard/Brand List miss this, and only because of PF-A - see §5's design options |
| Search / Paginated List (filtered) | ~1-2s | Matches Stage 5E's own Candidate Very Large Brand target (<3s) and current measured ~1.5-1.8s |
| Search / Paginated List (unfiltered/"view all") | ~2-3s | Acknowledges wider pre-filter scope (§6) without excusing PF-H indefinitely |
| Detail | ~2s | SKU Detail already meets this; Supplier Detail (51.1s) is the one clear violation |
| Heavy Explicit Operation (e.g. a deliberate bulk action, an explicit "recalculate") | several seconds acceptable | User has explicitly asked for the expensive thing, unlike Dashboard's default load |

## 8. Improvement Architecture (Per Root Cause Family)

Explicitly not "cache everything" - matched to each family's actual data
shape:

- **PF-A** (Supplier Master's instance): replace `brandsBySupplier()`'s
  full-catalog `findOrderCandidates(null,null,null)` call with a lean,
  SQL-side `(supplier_cd, brand_cd, brand_name)` distinct-pairs query -
  no `calc4`, no formula evaluation, no `ms_comm` full join needed. This
  is a **Request-time Query Optimization**, not a cache - the exact same
  shape as RC-A's Category A split for Dashboard.
- **PF-A** (Dashboard's remaining Category B cost): **Precomputed
  Aggregate / Portal DB Read Model** with **Scheduled Refresh** and a
  **Last Calculated Timestamp** - see §5. Not a per-request cache with a
  TTL; a deliberately-computed, timestamped snapshot, because silently
  serving a stale number without saying so would be worse than the
  current honest-but-slow behavior.
- **PF-B**: **Server-side Pagination** for Supplier Master List (also
  closes part of PF-E) and, as table sizes grow, for the 5 Master
  Maintenance config lists - no urgency today, revisit if/when any of
  those tables reach the low thousands.
- **PF-E**: same as PF-B - add a page/size contract to Supplier Master
  List first (the only one with a real row count today), design the
  other 5 the same way so a future growth doesn't need a redesign, not
  just a parameter bolt-on.
- **PF-F**: **Request-scoped, narrower transactions** - remove
  `SupplierMasterService`'s method-level `@Transactional`, matching
  exactly how RC-F fixed `DashboardService.getDashboard()`. Each Portal
  repository call already gets its own short transaction from Spring
  Data JPA without an enclosing one.
- **PF-H** (both instances): **Bulk/Lightweight Query, filter pushdown**
  - give `findOrderCandidates`'s few remaining legitimate full-catalog
    callers (after PF-A's fix, likely none) a real two-step/item-set
    design like RC-C's, or replace them with the lean SQL query above;
    for the unfiltered Candidate/Stock-Sales List gap, investigate
    whether the Step 1 SKU-ID query's `current_stock`/`latest_po` work
    can be deferred until a filter that actually needs it
    (min/maxStock/Sales) is present, rather than always computed.
- **PF-C, PF-D, PF-G**: no new work proposed - Stage 5E's existing fixes
  are confirmed intact and sufficient; continue the same bulk-fetch/
  filter-pushdown discipline for any new code.

## 9. Recommended Remediation Order

1. **PF-F fix for SupplierMasterService** (remove the method-level
   `@Transactional`) - same one-line-scope change pattern as RC-F,
   lowest risk, addresses the pool-exhaustion exposure independently of
   the slowness fix.
2. **PF-A/PF-H fix for `brandsBySupplier()`** (replace the full-catalog
   call with a lean supplier-brand-pair query) - the dominant fix; alone
   should take Supplier List/Detail from 50s+ to sub-second, matching
   Dashboard's own Category A pattern.
3. **PF-H fix for unfiltered Candidate/Stock-Sales List** - smaller,
   independent, no dependency on (1)/(2).
4. **PF-E pagination for Supplier Master List** - worth doing alongside
   (2) since the row count (602, admin-only) makes it lower urgency than
   (1)-(3), but touches the same screen.
5. **Dashboard/Brand List Precomputed Aggregate design** (§5) - largest
   design surface, needs Techlead/Product input on staleness tolerance
   and Legacy batch timing before an implementation Stage can be scoped
   - proposed as its own, later Stage rather than bundled with (1)-(4).
6. **PF-B/PF-E pagination for the 5 small Master config lists** - lowest
   urgency (no measured slowness today); revisit on table growth, not on
   a fixed timeline.

## 10. Production DB Change Required

**NO.** Every finding in this report is a service/query-structure issue
(unbounded full-catalog call, held transaction, missing pagination,
missing filter pushdown) fixable entirely within the application layer,
the same way RC-A/RC-C/RC-F were - no Production DB index or schema
change is implicated by any finding here.

## 11. P0 / P1 / P2 Findings Summary

- **P0**: Supplier/Manufacturer List (54.4s) and Supplier Detail (51.1s)
  - PF-A + PF-B + PF-E + PF-F + PF-H compounding at one service.
- **P1**: Dashboard/Brand List's remaining ~13.8-15.5s (already improved
  23-26x by RC-A, but still short of the ~1s Navigation-level budget in
  §7) - PF-A (Category B).
- **P2**: unfiltered Candidate/Stock-Sales List (~3.3-3.6s, PF-H); the 5
  small Master Maintenance config lists' unbounded reads (PF-B/PF-E,
  watch-only); `FollowUpCaseService`/`OrderHistoryService`'s bounded,
  low-severity per-username N+1 (PF-D residual).
- **OK / no finding**: SKU Detail, Price Change, Arrival, Order History,
  Warehouse Stock, and Stage 5E's own RC-B/RC-C/RC-E fixes (all
  re-verified intact, none reverted or duplicated elsewhere).

## 12. Stage 5H Implementation Scope Proposal (Not Started This Stage)

Proposed scope, pending Techlead review - matches §9's order:

- RC-G (proposed numbering, continuing from Stage 5D/5E's RC-A..RC-F):
  remove `SupplierMasterService`'s method-level `@Transactional`
  (PF-F fix, item 1).
- RC-H: replace `brandsBySupplier()`'s full-catalog call with a lean
  SQL-side supplier-brand-pair query (PF-A/PF-H fix, item 2) - no
  `calc4`/`FormulaParser` involvement, no Business Rule change.
- RC-I: filter-pushdown fix for unfiltered Candidate/Stock-Sales List
  (PF-H, item 3).
- RC-J: server-side pagination for Supplier/Manufacturer List (PF-E,
  item 4).
- Explicitly OUT of Stage 5H's proposed scope: the Dashboard/Brand List
  Precomputed Aggregate (item 5, needs a separate design-approval pass
  with Techlead/Product on staleness tolerance) and the 5 small Master
  config lists' pagination (item 6, no measured urgency).

## 13. Final Judgment

**READY FOR SYSTEMATIC PERFORMANCE REMEDIATION**

Every P0/P1 finding in this report has a precisely located root cause, a
proposed fix that reuses an already-proven pattern from Stage 5E (no new
architectural risk), and no Production DB change requirement. The one
open design question (Dashboard/Brand List's acceptable staleness
tolerance for a Precomputed Aggregate) is explicitly scoped out of Stage
5H rather than blocking it.
