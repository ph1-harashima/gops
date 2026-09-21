# G-OPS Stage 5D — Production-Scale Performance & Real-Data Bug Root Cause Analysis

Scope: consolidate Stage 5C's 8 findings (P0×4, P1×4) into root causes via
full dependency-chain tracing (frontend → hook → API → Controller →
Service → Repository → SQL → tables) and read-only Snapshot query-plan
analysis (`EXPLAIN ANALYZE`, `SHOW INDEX`). **RCA ONLY — nothing was
fixed.** No Application/Test/Migration/Legacy file was changed this Stage
(`git status` confirms clean throughout).

## 1. Dependency Map (Common Paths)

| Screen | Hook | API | Service | Key Repository call |
|---|---|---|---|---|
| Dashboard | `useDashboard()` | `GET /api/dashboard` | `DashboardService.getDashboard()` | `OrderCandidateService.findOrderCandidates(null,null,null)` — **unpaginated, full catalog** |
| Brand List | `useDashboard()` | `GET /api/dashboard` | *(same as Dashboard — identical call)* | *(same)* |
| Candidate List | `useOrderCandidates()` **+ `useDashboard()`** | `GET /api/order-candidates` (paginated) **+ `GET /api/dashboard`** (background) | `OrderCandidateService.findOrderCandidatesPage()` **+ `DashboardService.getDashboard()`** | `findStockSalesList` (bounded) **+ the same full-catalog call as Dashboard, in the background** |
| SKU Detail | — | `GET /api/items/{sku}/ordering-context` | `SkuDetailService.getDetail()` | `findBySkus(Set.of(sku))` — single row, but the query's own `latest_po` derived table is **not** bounded by that selectivity (§4) |
| Stock/Sales | — | `GET /api/stock-sales` (paginated) | `StockSalesService` | `findStockSalesList` (bounded) — same base query as Candidate List, without the background Dashboard call |

Dashboard, Brand List, and Candidate List's background fetch all
converge on the exact same unbounded call: `OrderCandidateService
.findOrderCandidates(null, null, null)` → `LegacyStockReadRepository
.findOrderCandidates()` → `RecommendedQtyReadQuery.sql` with **no LIMIT,
no Brand/Supplier/keyword filter** — every one of `MS_ITEM`'s ~116,842
active rows, fully computed.

## 2. Dashboard RCA

`DashboardService.getDashboard()` (source, unchanged this Stage):

```java
@Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
public DashboardResponse getDashboard() {
    List<OrderCandidateResponse> candidates = orderCandidateService.findOrderCandidates(null, null, null);
    ...
    int candidateCount = (int) candidates.stream().filter(DashboardService::isCandidate).count();
    ...
}
```

- **Every** active item (~116,842) is fetched and fully computed —
  `current_stock`, the `latest_po` derived table, **and** the full
  `calc4` Recommended-Qty formula chain (`FormulaParser` included) — even
  though the only outputs needed are small aggregate counts.
- All aggregation (`candidateCount`, `outOfStockCount`,
  `longTermOutOfStockCount`, and the same three per-Brand in
  `buildBrandRows`) happens as **Java `Stream` filtering over the fully
  materialized in-memory list** — not a SQL `GROUP BY`/`COUNT`.
- **Confirmed N+1**: `toResponse()` (called once per row) invokes
  `RecommendedQtyCalculator.resolveRegion(row)` **twice** per row — once
  internally inside `calc4()`, once again directly
  (`OrderCandidateService.java` line 129) — and each call is
  `SupplierRegionClassificationResolutionService.resolve()`, a
  **separate Portal PostgreSQL query per call** (up to 2 queries per
  `resolve()`: brand-specific lookup, then a supplier-only fallback).
  This is up to **4 individual Portal round-trips per row** — up to
  ~467,000 round-trips for the full catalog in the worst case.
- `SkuRestockExpectationService.getBulk()` is, by contrast, genuinely
  batched (2 queries total regardless of row count) — confirmed not a
  contributor.

**Primary cost centers, in order of confirmed impact**: (1) the N+1
Portal region-resolution calls, (2) `calc4`'s per-row `FormulaParser`
invocation (cheap per-call, but ×116,842), (3) the `latest_po` derived
table's full materialization cost (§4) is paid once per query execution,
not per row, but is still non-trivial. Exact proportional attribution
between (1) and (2) was not separately profiled (would require
instrumenting the running application, out of this Stage's read-only
scope) — both are confirmed present and real.

## 3. Brand List RCA

`OrderCandidateBrandListPage.tsx` calls `useDashboard()` directly —
**identical** to Dashboard's own call, sharing 100% of §2's root cause.
Confirmed what Brand List actually needs, per Brand: `brandCode`,
`brandName`, `candidateCount`, `outOfStockCount`, `longTermOutOfStockCount`
(all three require the full-catalog computation as currently
implemented), plus `draftCount`/`awaitingSupplierCount`/`attentionCount`
(Portal-only, already fast and independent of Legacy). **Design-level
observation (not implemented)**: `outOfStockCount`/`longTermOutOfStockCount`
are pure arithmetic on `current_stock`/`open_po` — computable as a
`GROUP BY brand_cd` SQL aggregate without `calc4`/`FormulaParser` at all.
`candidateCount` (`recommendedQty > 0`) is harder — it genuinely depends
on the full formula-driven calculation, which is not a simple SQL
predicate. A dedicated Brand List aggregate query could eliminate the
Legacy-side N+1 and per-row formula cost for the first two counts
entirely; `candidateCount`'s definition would need a product decision
(keep it exact but slow, or accept an approximation) — not decided here.

## 4. Candidate Background Fetch RCA

Confirmed via source: `useDashboard()` in `CandidateListPage.tsx` has
**exactly one** consumer — `brandLabel(code)`, used only to resolve the
Filter Chip's displayed Brand name (falls back to the raw code if
`dashboard` hasn't loaded). No other value from the Dashboard response is
read on this page. **Design-level options (not implemented)**: a
dedicated, lightweight Brand-master lookup (name-only, no candidate
computation), or resolving just the one selected Brand's name via a
targeted lookup, would eliminate this page's dependency on the
catastrophic full-catalog Dashboard query entirely.

## 5. Candidate/Stock-Sales Query Performance RCA (`EXPLAIN ANALYZE`)

Run read-only against the Snapshot (`gops_snapshot_audit`), for both a
single-SKU lookup (matching SKU Detail's access pattern) and a
Brand-scoped, paginated lookup (matching Candidate List's Very-Large-Brand
case, `LIMIT 20`):

**Single-SKU** (`WHERE item_cd = '<sku>'`): total ~861ms.
**Brand-scoped, paginated** (`WHERE brand_cd = '<a 3,880-SKU real Brand>' LIMIT 20 OFFSET 0`): total ~4,937ms — and, critically, the `EXPLAIN` plan shows the `LIMIT 20` is applied **only after** all 3,880 matching rows are fully computed and materialized (`Table scan on stock_sales ... Materialize ... rows=3880`, *then* `Limit: 20`) — confirming Stage 5C's own observation that per-page cost does not shrink with a smaller page or later offset within the same Brand.

Two concrete, quantified cost centers, common to both queries:

1. **The `latest_po` derived table is fully materialized on every single execution, regardless of outer selectivity** — confirmed via `EXPLAIN ANALYZE`: `Window aggregate: row_number() OVER (PARTITION BY d.ITEM_CD ...) (actual rows=183480)`, preceded by a full `Table scan on d` (183,480 rows) and a `Sort`. This costs ~600-860ms **flat**, whether the outer query wants 1 row or 3,880.
2. **A per-outer-row `ms_comm` brand-name lookup that does not use a full composite-key seek**: `Index lookup on b using PRIMARY (CATE_ID='MS_BRAND') (actual rows=1504, loops=3880)` — i.e., for each of the 3,880 outer rows, MySQL scans up to 1,504 `ms_comm` rows and filters for the matching `CODE_ID`, rather than seeking the exact `(CATE_ID, CODE_ID)` pair directly. This costs roughly 1ms × outer-row-count.

**`current_stock`'s correlated subquery is, by contrast, confirmed cheap and well-indexed**: `Index lookup on phys using item_cd ... (actual time=0.08ms, loops=3880)` — ~315ms total across all 3,880 rows. This corrects an informal assumption from Stage 5C; the subquery itself is not the dominant cost.

## 6. Current Stock Query Redesign Options (Design Only)

Not evaluated in depth this Stage beyond confirming §5's finding that
`current_stock` is **not** the bottleneck — no redesign of it is
motivated by this Stage's evidence. The `PHISICAL_QTY` warehouse set
(`4,5,6,7,8,10,11,12,15`) is unchanged and not implicated in any finding.
The actual redesign candidates are the `latest_po` derived table (§8) and
the brand/supplier name lookups (§5 point 2), not `current_stock`.

## 7. SKU Detail RCA

`SkuDetailService.getDetail()` calls `findBySkus(Set.of(sku))` (one row),
`findPoHistoryBySku(sku)` (a separate, `ms_item`/`ms_stk`-free query,
confirmed fast in Stage 5A/5C), and `legacyPriceReadRepository
.findBySkus(Set.of(sku))` (a second, separate Legacy query) — sequentially,
in one Java method, **no N+1 loop, no `resolveRegion` double-call**
(unlike Dashboard). The isolated `EXPLAIN ANALYZE` single-SKU query
(§5) took ~861ms — far short of Stage 5C's observed 174.5 seconds.
**This discrepancy is noted honestly, not resolved with certainty**:
Stage 5C's own measurement was taken while multiple other heavy
browser-testing operations (a Very-Large-Brand Candidate List load, and
possibly a lingering background Dashboard fetch from an earlier tab) were
running concurrently against the same single MySQL container and limited
connection pools — the 174.5-second figure most likely reflects real
contention under that concurrent load, not the cost of this one query in
isolation. The `latest_po` full-materialization cost (§5) is real and
would scale this further under genuine concurrent multi-user access, but
was not, by itself, sufficient to reproduce 174.5 seconds when measured
in isolation this Stage.

## 8. FormulaParser RCA

`FormulaParser.java` is explicitly a **verbatim port of Legacy's own
class** ("PORTED VERBATIM FROM LEGACY G-SYS ... DO NOT change any
parsing/calculation logic in this file"). Its `FORMULA_11` extraction
regex is hardcoded to the literal cell reference `[AT]`
(`"\[AT\]\*(\d+)/2"`). Classifying the 2,593 real, populated `FORMULA_11`
rows that don't contain `[AT]` (anonymized pattern analysis, no formula
text tied to identifiable Brand/Supplier retained):

| Pattern family | Row count | % of non-`[AT]` failures |
|---|---|---|
| `[AJ]*1/2` | 888 | 34% |
| `[AJ]*2/2` | 841 | 32% |
| `[AJ]*3/2` | 659 | 25% |
| `[AJ]*4/2` | 77 | 3% |
| `[AJ]*0.5/2` (decimal numerator) | 51 | 2% |
| A distinct `[AS]*2/2` structural pattern | 55 | 2% |
| Contains a literal Excel `#REF!` corruption marker | 1 | <1% |

**~97% (2,516/2,593) of all failures are the exact same 1-to-3-condition
structure the parser already handles, just addressed via `[AJ]` instead
of `[AT]`** — the multiplier counts (1, 2, 3, 4 per formula) match the
existing 2-3-condition structure precisely. This is far more consistent
with a **different cell address for the same logical formula slot**
(e.g., a differently-shaped source spreadsheet inserting/removing columns
ahead of this one over time) than with a genuinely different business
rule. **Because this parser is a byte-for-byte Legacy port, whether real
Legacy G-SYS's own live system correctly handles the `[AJ]` variant (via
logic not captured in this one ported class) or has the same limitation
today cannot be determined from source alone — recorded as a Gulliver
Confirmation item**, not assumed either way.

## 9. 発注残 (Open PO) Bug RCA

Traced every screen that renders an "Open PO"-equivalent field:

| Screen | Service | Field source | Correct? |
|---|---|---|---|
| Candidate List / Dashboard | `OrderCandidateService.toResponse()` | `sum(row.openPo(), row.openArrival())` | **NO — confirmed bug** |
| Stock/Sales | `StockSalesService` | `row.openPo()`, `row.openArrival()` passed separately | Yes — correct |
| SKU Detail | `SkuDetailService.getDetail()` | `row.openPo()`, `row.openArrival()` passed separately | Yes — correct |

**The bug is isolated to exactly one call site** (`OrderCandidateService
.toResponse()`, shared by both the paginated Candidate List and the
unpaginated Dashboard path — so it also silently affects Dashboard's own
`isLongTermOutOfStock` proxy logic, which reads `c.openPo()`). No comment
or design-doc reference in the surrounding code explains why `openPo` and
`openArrival` were combined here — no evidence this was a deliberate
decision; most consistent with an unintentional mix-up during original
implementation. Canonical meaning, confirmed from the Legacy-side field
definitions (Stage 3B/4): **Open PO** = still-outstanding purchase-order
quantity not yet arrived; **Open Arrival** = quantity that has arrived
but is not yet reflected in on-hand stock; **Open Ship** = quantity
shipped by the supplier but not yet formally arrived; **Logical Qty** =
`PHISICAL_QTY + Open PO + Open Ship` (Open Arrival deliberately excluded,
per Stage 3B). These three (`Open PO`/`Open Arrival`/`Open Ship`) are
distinct, non-interchangeable figures everywhere else in the codebase —
this one field is the sole exception.

## 10. Connection Pool RCA

Traced the Portal (`prototype-readwrite-pool`, `maximum-pool-size: 5`)
transaction boundaries across every Legacy-adjacent code path:

- **`DashboardService.getDashboard()` carries a method-level
  `@Transactional(readOnly = true, transactionManager =
  "prototypeTransactionManager")`** — this holds one Portal connection
  reserved for the **entire method body**, including the synchronous call
  into `orderCandidateService.findOrderCandidates()`, which can run for
  minutes (§2). The Legacy work itself runs under a *separate* Legacy
  transaction manager (no shared connection with Portal), but the
  enclosing Portal transaction's connection sits checked-out and idle for
  the full duration regardless.
- **This is not a general pattern.** `OrderCandidateService`'s paginated
  path (`findOrderCandidatesPage`) has **no outer Portal
  `@Transactional`** — each Portal-touching helper
  (`SkuRestockExpectationService.getBulk`,
  `SupplierRegionClassificationResolutionService.resolve`) opens and
  commits its own short-lived transaction independently, per call. At
  page-scale (20-100 rows), this is wasteful (the N+1 from §2 still
  applies, scaled down) but does not hold a connection open for minutes.

**Root cause confirmed**: the Portal pool exhaustion observed in Stage
5C is a direct, mechanical consequence of `DashboardService`'s transaction
boundary wrapping unrelated, extremely slow Legacy work inside a held
Portal connection — not a pool-size problem. Simply raising
`maximum-pool-size` (explicitly out of scope per instruction) would mask,
not fix, this: it would let more slow Dashboard calls run concurrently
before exhausting a larger pool, without addressing the minutes-long
hold time itself.

## 11. Index Audit (Read-Only, `SHOW INDEX`)

| Table | Relevant index | Covers the query's actual access pattern? |
|---|---|---|
| `MS_ITEM` | `PRIMARY(ITEM_CD)`, secondary `brand_cd`, `item_cd`, `del_flg` | Yes |
| `ms_stk` | `PRIMARY(WH_CD, ITEM_CD)`, secondary `item_cd` | Yes — confirms §5's finding that `current_stock` is cheap |
| `ms_comm` | `PRIMARY(CATE_ID, CODE_ID)` (a correctly-structured composite key), plus secondary `code_id` | Index exists and is structured correctly; the observed query plan (§5) does not fully exploit it for the brand/supplier lookup — a query-shape issue, not a missing index |
| `ms_formula` | `PRIMARY(ID)` | Yes |
| `tr_po` | `PRIMARY(PO_NO)` | Yes, for its own row access; not the bottleneck |
| **`tr_po_dtl`** | **`PRIMARY(PO_NO, LINE_NO)`, secondary `PO_NO` — no index on `ITEM_CD` at all** | **No — this is the confirmed, concrete root cause of §5/§8's `latest_po` full-materialization cost** |
| `tr_arr` | `PRIMARY(SUPPLIER_CD, PO_NO, INV_NO)` | Yes, for Arrival's own unfiltered-browse access pattern (never joins `item_cd`) |

**`tr_po_dtl` lacking any index on `ITEM_CD` is the single most
concrete, mechanically-confirmed finding of this Stage.** It is the
reason the `latest_po` derived table (used by every screen built on
`RecommendedQtyReadQuery.sql` — Candidate List, Stock/Sales, SKU Detail,
Dashboard) cannot be filtered or bounded by MySQL's optimizer no matter
how selective the outer query is.

## 12. Root Cause Consolidation

| ID | Affected Findings | Severity | Technical Cause | Evidence | Proposed Remediation (not implemented) |
|---|---|---|---|---|---|
| **RC-A** | P0-1 (Dashboard), P0-2 (Brand List) | P0 | `DashboardService.getDashboard()` fetches and fully computes the entire unpaginated catalog (~116,842 items) via `findOrderCandidates(null,null,null)`, including a confirmed N+1 (`resolveRegion` called 2×/row, up to 4 Portal round-trips/row), then aggregates in Java, not SQL | §2/§3 source trace; Stage 5C timing (351-407s) | Replace with SQL-side aggregate counts (`GROUP BY brand_cd`) for the arithmetic-only KPIs; redesign or explicitly accept-and-bound `candidateCount`'s formula-dependent definition — a product decision |
| **RC-B** | P0-3 (Candidate List background freeze) | P0 | `CandidateListPage` calls `useDashboard()` solely to resolve one Filter Chip's Brand name, inheriting RC-A's full cost in the background on every page visit | §4 source trace (single consumer confirmed) | Replace with a lightweight Brand-name-only lookup, decoupled from RC-A entirely |
| **RC-C** | P0-4 (SKU Detail), P1-7 (Candidate/Stock-Sales scaling) | P0/P1 | `tr_po_dtl` has no index on `ITEM_CD`; the `latest_po` derived table's window function cannot be bounded by any outer filter and fully materializes (183,944 rows) on every execution; a secondary, less-certain `ms_comm` per-row lookup inefficiency compounds this at Brand scale | §5 `EXPLAIN ANALYZE`; §11 `SHOW INDEX` | An index on `tr_po_dtl(ITEM_CD)` (Production DB change — proposal only, not applied) would let the derived table be filtered; alternatively, a query redesign (e.g., resolving `latest_po` only for the already-filtered/paginated outer SKU set via a two-step query) could avoid needing an index change at all — G-OPS-side redesign is the safer, self-contained first option |
| **RC-D** | P1-5 (FormulaParser) | P1 | `FormulaParser`'s Legacy-ported regex hardcodes `[AT]` as the cell reference; ~97% of real failures are the identical formula structure using `[AJ]` instead | §8 pattern classification | Generalize the cell-reference regex to match any 1-2 letter reference — but this touches a class explicitly marked "DO NOT change... verbatim Legacy port," so requires Gulliver confirmation on whether Legacy's own system handles `[AJ]` correctly before generalizing G-OPS's copy |
| **RC-E** | P1-6 (発注残 bug) | P1 | `OrderCandidateService.toResponse()` alone computes the response's `openPo` field as `sum(openPo, openArrival)`; every other screen keeps them separate and correct | §9 cross-screen trace | Change this one call site to pass `row.openPo()` directly, matching Stock/Sales and SKU Detail's existing correct pattern |
| **RC-F** | P1-8 (Pool exhaustion) | P1 | `DashboardService.getDashboard()`'s method-level `@Transactional` on the Portal transaction manager holds a Portal connection for the full duration of RC-A's multi-minute Legacy computation | §10 transaction-boundary trace | Remove or narrow the Portal `@Transactional` scope so it only wraps the actual Portal-side queries (`portalOrderRepository.findAll()`, etc.), not the enclosing Legacy computation — a transaction-boundary fix, not a pool-size increase |

RC-A and RC-C are the two dominant, distinct architectural causes; RC-B,
RC-D, RC-E, RC-F are each narrower and more surgical. RC-A and RC-F are
related (both stem from `DashboardService.getDashboard()`'s design) but
are listed separately since they have independent fixes (query
aggregation vs. transaction scope).

## 13. Recommended Stage 5E Scope (Design Only — Not Decided Here)

Priority order, per instruction, collapsed where root causes are shared:

1. **RC-A + RC-F together** (same method, `DashboardService.getDashboard()`)
   — resolves Dashboard/Brand-first usability (P0-1, P0-2) and pool
   exhaustion (P1-8) in one coordinated change.
2. **RC-B** — resolves the Candidate List background freeze (P0-3),
   independent of RC-A/RC-F's own fix timeline.
3. **RC-C** — resolves SKU Detail (P0-4) and Candidate/Stock-Sales scaling
   (P1-7) together, via query redesign and/or the proposed (not applied)
   index.
4. **RC-D** — FormulaParser correctness; blocked on Gulliver confirmation
   before any change.
5. **RC-E** — 発注残 correctness; smallest, most self-contained fix, no
   dependency on any other RC.

## 14. Acceptance Targets (Proposed, Not an SLA)

| Screen | Current (Stage 5C) | Proposed target |
|---|---|---|
| Dashboard | 351-407s, frequent failure | <5s, always succeeds |
| Brand List | Same as Dashboard | <5s |
| Candidate List, Very Large Brand | ~17s/page | <3s/page |
| SKU Detail | 174.5s (contended), ~861ms (isolated) | <1s |
| Price Change | ~112ms | Unchanged (already meets any reasonable target) |
| Arrival | ~521ms (full unfiltered scale) | Unchanged |

These are starting proposals for Stage 5E discussion, not adopted
targets — explicitly not a Performance SLA per instruction.

## 15. Production DB Index Change

**Not implemented, not decided.** An index on `tr_po_dtl(ITEM_CD)` is
identified as a concrete, mechanically-confirmed candidate that would
resolve RC-C's root cause directly. Whether to pursue it (vs. a
G-OPS-only query redesign that avoids needing it) is a Stage 5E design
decision — this Stage recommends attempting the query-redesign path
first, since it requires no Production schema change at all, and falling
back to proposing the index only if redesign proves insufficient.
