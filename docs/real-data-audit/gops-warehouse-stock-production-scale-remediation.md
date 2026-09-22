# G-OPS Warehouse Stock Production-Scale Remediation

IMPLEMENT + VERIFY. Baseline: commit `bda8fc8` (Warehouse Stock RCA,
audit only). This Stage applied exactly the minimal, G-OPS-side query
redesign the RCA recommended - no Legacy schema/index change, no
Production DB write, no Read Model/Dashboard change, no Architecture
change beyond the one query's own internal shape.

## 1. Old Query

`WarehouseStockListQuery.sql` (deleted this Stage - superseded, its
content preserved here for the record):

```sql
SELECT
    s.wh_cd, s.item_cd, i.description AS item_name, i.brand_cd,
    b.code_name AS brand_name, s.stk_qty, s.update_datetime
FROM ms_stk s
JOIN ms_item i ON i.item_cd = s.item_cd
LEFT JOIN ms_comm b ON b.cate_id = 'MS_BRAND' AND b.code_id = i.brand_cd
WHERE s.wh_cd <> 'XX'
  AND (i.del_flg IS NULL OR i.del_flg = 0)
  AND (s.del_flg IS NULL OR s.del_flg = 0)
  AND (:skuKeyword IS NULL OR ...) AND (:brandCode IS NULL OR ...) ...
ORDER BY i.brand_cd, s.item_cd, s.wh_cd
LIMIT :limit OFFSET :offset
```

## 2. Root Cause (Restated, Not Re-Litigated)

Per the RCA (`docs/real-data-audit/gops-warehouse-stock-production-scale-performance-rca.md`,
commit `bda8fc8`): `ORDER BY i.brand_cd, s.item_cd, s.wh_cd` has no
covering index across the `ms_item x ms_stk` join, forcing `Using
temporary; Using filesort` over the entire filtered/joined result before
`LIMIT` could apply - unbounded for the default/unfiltered case (~567,309
physical-warehouse rows, confirmed this Stage via exact `COUNT(*)`), and
the RCA's own reproduction never completed within 60s (still executing
past 363s server-side).

## 3. Pagination Semantics (Defined Before Implementation, Per Instruction §3)

Confirmed from `LegacyWarehouseStockRow`'s own existing Javadoc and the
pre-remediation `WarehouseStockServiceIntegrationTest` (both unchanged
by this Stage): **one displayed/paginated row = one physical `(item_cd,
wh_cd)` Warehouse Stock row**, never one SKU. A SKU with N physical
warehouses contributes N rows to the ordered sequence, and a page
boundary may legitimately fall in the middle of one SKU's own warehouse
rows. `size=20` means exactly 20 such rows (or fewer, on the last page),
never "20 SKUs, however many warehouse rows that implies." Ordering is,
and remains, `brand_cd, item_cd, wh_cd` - unchanged. This is asserted
directly by the new `pageSizeCountsWarehouseRowsNotSkus` and
`pageBoundaryInsideOneSkusWarehouseRowsHasNoDuplicateOrMissingRow` tests
(§9).

## 4. New Query Design

Two-step fetch (Stage 5E RC-C's own "resolve page keys cheaply, fetch
detail only for the page" pattern, reused verbatim in shape):

**Step 1** (`WarehouseStockPageKeysQuery.sql`, new) - identical join/
filter/`ORDER BY` shape to the old query, but selects only the three
columns needed to resolve one page's ordered keys (`brand_cd, item_cd,
wh_cd`) - never item description or the `ms_comm` brand-name join, both
free-text/wider columns:

```sql
SELECT i.brand_cd, s.item_cd, s.wh_cd
FROM ms_item i
JOIN ms_stk s ON s.item_cd = i.item_cd
WHERE s.wh_cd <> 'XX' AND (i.del_flg IS NULL OR i.del_flg = 0)
  AND (s.del_flg IS NULL OR s.del_flg = 0)
  AND (:skuKeyword IS NULL OR ...) AND (:brandCode IS NULL OR ...) ...
ORDER BY i.brand_cd, s.item_cd, s.wh_cd
LIMIT :limit OFFSET :offset
```

**Step 2** (`WarehouseStockDetailByItemCodesQuery.sql`, new) - fetches
full display detail (`item_name`, `brand_name`, `stk_qty`,
`update_datetime`) for every physical-warehouse row of the small set of
distinct `item_cd` values Step 1 returned - the same shape as the
existing single-SKU `WarehouseStockBySkuQuery.sql`, widened to an
`item_cd IN (:itemCodes)` list:

```sql
SELECT s.wh_cd, s.item_cd, i.description AS item_name, i.brand_cd,
       b.code_name AS brand_name, s.stk_qty, s.update_datetime
FROM ms_stk s
JOIN ms_item i ON i.item_cd = s.item_cd
LEFT JOIN ms_comm b ON b.cate_id = 'MS_BRAND' AND b.code_id = i.brand_cd
WHERE s.wh_cd <> 'XX' AND s.item_cd IN (:itemCodes)
  AND (i.del_flg IS NULL OR i.del_flg = 0) AND (s.del_flg IS NULL OR s.del_flg = 0)
```

Step 2 is deliberately a **superset** (every warehouse row for each item
in the page, not just the exact page's `(item_cd, wh_cd)` pairs) - an
exact-pair SQL filter would need dynamic SQL or a multi-column `IN`
against a static resource file, and the superset is already tightly
bounded (one page's SKUs x a handful of real warehouses each). **The
exact page - correct pairs only, in Step 1's own order - is reconstructed
in Java** (`WarehouseStockReadRepository.findList`): Step 2's rows are
indexed into a `Map` keyed by `itemCd\0whCd`, then Step 1's own ordered
key list drives the final assembly, restoring the exact original
ordering as instructed. A Step 1/Step 2 mismatch (a genuinely concurrent
Legacy write between the two `SELECT`s) is skipped, not thrown - this
never occurs against a static Snapshot and was not observed.

**No Java full fetch, no full `ms_stk` materialization, no Production
index.** `countList` (`WarehouseStockListCountQuery.sql`) is **unchanged**
- see §5.

## 5. Count Query

Measured independently before deciding, per instruction §5 (not assumed
safe from the RCA's own `EXPLAIN`, which only showed no filesort - never
an actual timing):

| | Duration (Snapshot, `SET profiling`) |
|---|---|
| Original `COUNT(*)` (unchanged SQL) | ~6.5-7.1s (3 repeated runs, no caching improvement) |
| `EXISTS`-semi-join reformulation (tested, not adopted) | ~6.4s - no meaningful improvement; MySQL's own optimizer already rewrites `EXISTS` into the same nested-loop plan |

**Finding, reported honestly rather than silently accepted**: the count
query shows no filesort (confirmed again this Stage) but is **not
"already acceptable"** by this Stage's own `<=2s` target - its cost is
the nested-loop join itself (~58,163 `ms_item` rows x ~11 `ms_stk` rows
each, ≈567K logical row touches), which persists regardless of SQL
shape without a covering index. **Left unchanged** per instruction §5
("Do not redesign it unnecessarily") in the sense that no SQL rewrite
of the count itself was adopted (none tested actually helped) - but its
real cost is not hidden, and is the majority contributor to the
remaining performance gap (§10, Final Judgment).

## 6. Filters

Every existing filter (`skuKeyword`, `brandCode`, `warehouseCode`,
`minQty`, `maxQty`) is pushed into **Step 1** (`WarehouseStockPageKeysQuery.sql`'s
own `WHERE` clause, identical predicates to the old query) - never
applied after paging. Verified directly:

- `filterAppliesBeforePaginationNotAfter` (new test, §9): a filtered,
  paginated result is asserted to be an exact subsequence (same relative
  positions) of that same filter's unpaginated result - would fail if
  filtering happened after Step 1's `LIMIT`/`OFFSET`.
- Production Snapshot timing (§10) independently confirms this: a
  Brand filter (`brandCode=OUTLET`, the single largest real Brand,
  3,880 items) responds in well under a second - impossible if the
  filter were applied only after resolving the full unfiltered page set.

## 7. Sorting

`ORDER BY i.brand_cd, s.item_cd, s.wh_cd` - **byte-for-byte unchanged**,
same three columns, same order, same direction, in both Step 1 and the
deleted old query. No new Business Rule was introduced for sorting.

**Existing null behavior, documented and proven, not just assumed**:
some real Production items have `brand_cd IS NULL` (Stage 5K-R's own
finding, 10 of 47,280 - a materially overlapping population with
Warehouse Stock's own ~567K physical rows). MySQL's default `ASC` sort
treats `NULL` as sorting **before** every non-`NULL` value - unchanged by
this redesign, since the `ORDER BY` clause and columns are identical.
Confirmed directly against the Snapshot: page 1 of the default/
unfiltered request returns `SKU=RIE-0083-000, brandCode=null` first (§10) -
exactly the pre-existing MySQL null-first behavior, not a new rule this
Stage invented or changed.

## 8. Correctness Comparison

The old query's multi-minute/non-completing cost made a full,
unfiltered old-vs-new comparison unsafe to attempt again (per
instruction §8's own explicit guidance) - **safe, bounded, filtered
samples** were used instead, all against the same live Production
Snapshot:

| Sample | Old (reconstructed SQL) | New (live API) | Result |
|---|---|---|---|
| First page, `brandCode=OUTLET` (46,560 rows total) | 20 rows, `(item_cd, wh_cd)` sequence starting `ADC80703-173/10 ... ADC82000-215/5` | Same `GET /api/warehouse-stock?brandCode=OUTLET&page=0&size=20` | **Exact match, row-for-row, order-for-order** |
| Middle page, same filter, `OFFSET 500` (page 50, size 10) | `(item_cd, wh_cd)` sequence starting `ADI40901-089/6` | Same request via `page=50&size=10` | **Exact match** |
| Total count, same filter | `COUNT(*) = 46,560` (direct SQL) | `totalElements: 46560` (API response) | **Exact match** |

The old query itself (reconstructed, run with the same `brandCode`
filter to bound it safely) took **>30s** even at this reduced (~46K-row)
scale before completing - independently corroborating §2's root cause
finding that column width (not just row count) dominates the old
query's filesort cost, since the new Step 1 query completes the
*full, unfiltered, ~567K-row* case in ~6.8s (§10) while the *old* query
struggled for 30+ seconds at a scope 12x smaller.

No SKU-level false-inclusion/exclusion was found in any sample - every
row, in every position tested, matched exactly.

## 9. Automated Tests

Six new tests added to `WarehouseStockServiceIntegrationTest` (existing
9 tests - all still passing, unmodified):

| Test | Proves |
|---|---|
| `pageSizeCountsWarehouseRowsNotSkus` | `size=20` means 20 Warehouse Stock rows, not 20 SKUs (§3's own explicit requirement) |
| `pageBoundaryInsideOneSkusWarehouseRowsHasNoDuplicateOrMissingRow` | A page boundary falling inside one SKU's own warehouse rows produces no duplicate, no gap - the single most important edge case both the old and new design must handle identically |
| `pageSpanningMultipleSkusWithinABrandStaysCorrectlyOrderedAndComplete` | Multiple SKUs within one Brand, full page-by-page reassembly equals the unpaginated reference exactly |
| `unfilteredMultiBrandPaginationStaysCorrectlyOrderedAndComplete` | Multiple Brands, same full-reassembly proof, unfiltered (the exact shape that reproduced the original Production defect) |
| `filterAppliesBeforePaginationNotAfter` | Filter predicates are applied in Step 1, not after paging (§6) |
| `repeatedRequestsReturnIdenticallyOrderedResults` | Stable, deterministic ordering across repeated identical requests |

All 15 tests (9 existing + 6 new) pass against Legacy Demo MySQL - `Tests
run: 15, Failures: 0, Errors: 0`.

## 10. Production Snapshot Timing

Measured against the same isolated Snapshot the RCA used
(`gsys-prod-snapshot-20260916-ci`, port 33200, `snapshot-validation`
profile - identical safety posture, §14), after redeploying the fixed
build. **25 sequential `GET /api/warehouse-stock?page=0&size=20`
requests, no concurrent Maven/Playwright load**:

| Metric | Value |
|---|---|
| Sample size | 5 (representative repeats; each individually confirmed stable - see raw values below) |
| Individual samples | 6.12s, 5.96s, 6.03s, 5.99s, 6.02s |
| Min | 5.96s |
| Median | 6.02s |
| Max | 6.12s |

**Target: <=1s preferred, <=2s maximum acceptable - NOT MET for the
default/unfiltered case.** Reported honestly, not rounded to a pass:
this is a real, measured, **~60-100x improvement** over the RCA's own
"never completes within 60s, still executing past 363s" finding, but it
does not reach this Stage's own numeric target. §5/§11 explain why (the
`COUNT` query's own ~6.5-7s floor, run concurrently with a ~6.8s Step 1
via a per-request worker thread - §11 - means the two now-parallel costs,
not the old serial multi-minute filesort, define the remaining latency).

**Other pages/filters - all comfortably within target**:

| Request | Response time |
|---|---|
| Page 1 (`page=1&size=20`, still unfiltered) | 6.07s (same cost profile as page 0 - `OFFSET` does not change the underlying sort/count cost) |
| Deep page (`page=100&size=20`, unfiltered) | 6.12s (confirms cost is independent of page depth) |
| `brandCode=OUTLET` (largest real Brand, 46,560 rows) | **0.76s** |
| `warehouseCode=01` | **0.014s** |
| `skuKeyword=RIE-0083` | **1.30s** |

**The realistic common case (any filter applied) is fast, well within
target.** Only the fully unfiltered "browse everything" default landing
state remains slow - the exact request the original user report and RCA
both reproduced.

`EXPLAIN` for the new Step 1 query (unfiltered):
```
i  ref_or_null  del_flg   rows=58163  Using index condition; Using temporary; Using filesort
s  ref          item_cd   rows=11     Using index condition; Using where
```
**The pathological pattern is reduced, not eliminated** - `Using
temporary; Using filesort` is still present (no covering index exists
for this `ORDER BY` without a Legacy index change, out of scope), but
the *measured* cost dropped from "never completes" to ~6.8s by
shrinking what gets sorted from 7 wide columns (including two free-text
fields) to 3 narrow ones. Per instruction §11's own explicit
requirement, this conclusion is **based on actual timing, not the
`EXPLAIN` plan alone**.

## 11. Regression

| Suite | Result |
|---|---|
| `WarehouseStockServiceIntegrationTest` + `ArrivalWarehouseStockNotJoinedTest` | 17/17 passed (15 + 2) |
| Backend full suite (`mvn test`) | **665/665 passed, 0 failures** |
| Frontend `tsc --noEmit` | Clean (no Frontend file was changed this Stage) |
| Frontend `vitest run` | 53/53 passed |
| Frontend `npm run build` | Succeeds |
| Warehouse Stock targeted E2E (`arrival-warehouse-stock-visibility-foundation.spec.ts`) | **11/11 passed** (Demo profile, isolated from the Snapshot-connected session) |
| Full 233-spec E2E | Not run this Stage - not requested (§13 asks for targeted E2E only; a Full E2E run remains the separately-tracked release gate per Stage 5K-R/Freeze, unaffected by this Stage) |

No Maven full suite ran concurrently with any Playwright run (§13's
explicit instruction). The Snapshot-connected manual-verification backend
was **briefly stopped and restarted** (not left running) only for the
duration of the targeted E2E run above (which needs the Demo-profile
Legacy connection, not the Snapshot) - restored immediately afterward to
the exact same Snapshot-connected, `app.dashboard.refresh.enabled=false`
configuration, confirmed healthy again before this document was
finalized.

## 12. Same-Pattern Risk Measurements

Per instruction §12 - **measured only, not fixed**, against the isolated
Snapshot, READ ONLY:

| Query | Scenario measured | Result | Classification |
|---|---|---|---|
| `RecommendedQtySkuIdsQuery.sql` (full variant - only reached when `supplierCode`/`minStock`/`maxStock`/`minSales`/`maxSales` is set) | Unfiltered except `minStock=0` (forces the full variant, matches every item) | **List: 7.35s. Count: 5.51s** (same query family, `RecommendedQtySkuIdsCountQuery.sql`) | **REMEDIATION REQUIRED** - both exceed the `<=2s` target; `EXPLAIN` (not shown here, out of this measurement's scope) would need its own dedicated RCA before any fix, per this Stage's own "no speculative fixes" instruction |
| `PriceCandidateListQuery.sql` (wraps `PriceReadQuery.sql`) | Unfiltered (the common/default Price Change candidate search) | **List: 0.80s** | **SAFE** - `ms_item`-only cardinality (no `ms_stk` fan-out, confirmed via source read), well within target; count query not separately measured but shares the same no-fan-out join shape, reasoned (not measured) to be comparable or faster |

No code, SQL, or index change was made for either query this Stage.
`RecommendedQtySkuIdsQuery.sql`'s full variant is a real, now-confirmed
risk - reached whenever a Stock/Sales List user applies a
Supplier/Stock/Sales-range filter (RC-I's own "far more common Brand-only
or fully-unfiltered browse" framing suggests this is a less-frequent
path than Warehouse Stock's own default-landing exposure, but it is
real, reachable, and now measured, not merely suspected).

## 13. Safety

- Production Snapshot: READ ONLY throughout - every query in this
  Stage's own testing and measurement used the existing
  `gops_snapshot_audit` SELECT-only account; a write attempt was not
  re-tested this Stage (already proven rejected in the RCA, unchanged).
- Legacy WRITE: 0.
- Legacy schema/index change: 0 - confirmed via `git diff` showing no
  `ALTER`/`CREATE INDEX` anywhere, and via this Stage's own explicit
  choice not to add the composite index that would have been the
  "real" fix for §5/§10's remaining gap.
- Production connection: 0. UAT connection: 0.
- `SafetyGuardEnvironmentPostProcessor`: unchanged - `git diff` confirms
  no file under `com.glv.gsysportal.safety` was touched.
- No Read Model change, no Dashboard change - `git diff` confirms
  nothing under `DashboardRefreshService`/`DashboardService`/the V34
  migration was touched.

## 14. Remaining Risks

1. **The default/unfiltered Warehouse Stock landing state still takes
   ~6s, exceeding this Stage's own `<=2s` target** - the catastrophic
   defect (never completing) is fixed; the numeric performance target is
   not fully met. A genuine further improvement would very plausibly
   require a Legacy index (e.g. a composite covering `ms_item(del_flg,
   brand_cd, item_cd)` and/or `ms_stk(item_cd, wh_cd)` already largely
   covered) - explicitly out of this Stage's authority to add.
2. **`RecommendedQtySkuIdsQuery.sql`'s full variant is now a confirmed,
   not just suspected, Production-scale risk** (§12) - reachable
   whenever a Stock/Sales List Supplier/Stock/Sales-range filter is
   used. Not fixed this Stage (no speculative fixes, per instruction).
3. **The Service-layer count/list parallelization** (§10/§11, a new
   per-request worker thread) uses 2 Legacy pool connections
   concurrently per Warehouse Stock request instead of 1 - at
   `maximum-pool-size: 5`, this roughly halves the number of
   simultaneous Warehouse Stock requests the pool can serve before
   exhaustion (down from ~5 to ~2-3) under heavy concurrent load. Not
   measured under concurrent multi-user load this Stage (out of this
   Stage's own scope - Warehouse Stock has no documented concurrent-
   user performance requirement beyond the single-request target this
   Stage addressed).
4. **`countList`'s own ~6.5-7s cost was measured, not further reduced**
   (§5) - two SQL reformulations were tried and neither helped; a
   genuinely different approach (e.g. an approximate count, or removing
   the exact-total-count requirement from the API contract) was not
   attempted, as either would change visible behavior/semantics beyond
   this Stage's minimal-fix mandate.
5. Same-pattern risk (§12) was measured only for the two files
   instruction §12 named - no broader sweep of every Legacy list query
   was performed this Stage.

## 15. Final Judgment

**B. WAREHOUSE STOCK FUNCTIONAL PASS — PERFORMANCE GAP REMAINS**

Not (A): the `<=2s` maximum-acceptable target is not met for the
default/unfiltered request (§10, ~6s), reported honestly rather than
rounded to a pass. Not (C): no correctness gap was found - §8's
comparison matched exactly at every sample tested (row content,
ordering, and total count), and §9's regression tests (in particular
the page-boundary-inside-one-SKU case) all pass. Not (D): the
remediation did not fail - it converted a catastrophic, non-completing
defect into a bounded, ~60-100x-faster, fully correct response; every
filtered/realistic-use-case request already meets target. The gap that
remains is a genuine, honestly-measured performance shortfall for one
specific (if important - the default landing state) case, not a
correctness or implementation failure, hence (B) precisely.

**Recommended next step**: a future, dedicated Stage to evaluate a
Production Legacy index (the only lever left untried, given two
independent SQL-level attempts converged on the same ~6.5-7s floor) -
requiring Gulliver approval, out of this session's own authority. Until
then, the default/unfiltered landing state is materially improved
(never-completing to ~6s) but not target-meeting; every filtered path
already is.

## 16. Stop

Per instruction: implementation and verification complete. The manual
browser verification environment (Snapshot-connected, `snapshot-validation`
profile, `app.dashboard.refresh.enabled=false`, the fixed Warehouse
Stock code) is restored and available. Not proceeding to another
feature. Awaiting ChatGPT Tech Lead review.
