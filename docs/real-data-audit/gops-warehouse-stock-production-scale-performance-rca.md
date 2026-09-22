# G-OPS Warehouse Stock Production-Scale Performance RCA

AUDIT ONLY. No code, SQL, index, Legacy, Production Snapshot, or Read
Model change was made. Reported live during manual browser verification
of the Stage 5K-frozen Read Model against the Production Snapshot
(`gsys-prod-snapshot-20260916-ci`, port 33200, `snapshot-validation`
profile, unchanged from Stage 5K-R/Freeze).

## 1. Reproduction Result

**Reproduced - confirmed, and worse than the user's own 20-second
observation.** A direct `GET /api/warehouse-stock` call (default,
unfiltered, page 0/size 20 - the exact request the Warehouse Stock
screen's own landing state issues), via a separate `curl` session so as
not to disturb the user's own browser session, **did not return within
60 seconds** (`curl --max-time 60`, `HTTP:000`, no response body at
all). Cross-checked directly against the Snapshot's own
`information_schema.processlist`: the query was still `executing`
**363 seconds** after it started (and a second, independent invocation
of the identical query - the user's own original browser request,
almost certainly - was still executing at **164 seconds**, later grown
to well past that). Neither had completed at the time of this report.

## 2. Endpoint

`GET /api/warehouse-stock` (`WarehouseStockController.list`, no query
parameters needed to reproduce - the default landing state alone
triggers it).

## 3. Actual Response Time

**Did not complete within the observation window** (>363s for the
oldest still-running instance at time of writing). Not "slow" - the
backend never returns for the default, unfiltered case against the real
Production-scale `ms_stk` table.

## 4. Bottleneck Layer

**Backend/Legacy SQL - confirmed, not assumed.** The API itself never
returns (§1), which by construction rules out any Frontend
rendering/state contribution (instruction §8's own distinction: "APIが
返っていない場合：Backend/SQL RCA"). `WarehouseStockListPage.tsx`'s own
data-fetching (`useWarehouseStockList`, a plain TanStack Query hook
calling `fetchList` once) shows no client-side anti-pattern - a single,
unremarkable `GET` request, nothing retried or duplicated client-side.

## 5. SQL Execution Time

Not completed (§1) - `EXPLAIN` (not `EXPLAIN ANALYZE`, which would have
required waiting for/adding a third long-running execution) was used
instead to characterize the plan safely:

```
EXPLAIN SELECT s.wh_cd, s.item_cd, i.description, i.brand_cd, b.code_name,
       s.stk_qty, s.update_datetime
FROM ms_stk s
JOIN ms_item i ON i.item_cd = s.item_cd
LEFT JOIN ms_comm b ON b.cate_id = 'MS_BRAND' AND b.code_id = i.brand_cd
WHERE s.wh_cd <> 'XX' AND (i.del_flg IS NULL OR i.del_flg = 0)
  AND (s.del_flg IS NULL OR s.del_flg = 0)
ORDER BY i.brand_cd, s.item_cd, s.wh_cd
LIMIT 20 OFFSET 0;
```

| table | type | key | rows (est.) | Extra |
|---|---|---|---|---|
| `i` (ms_item) | ref_or_null | `del_flg` | 58,163 | **`Using temporary; Using filesort`** |
| `s` (ms_stk) | ref | `item_cd` | ~11 (per outer row) | Using index condition; Using where |
| `b` (ms_comm) | ref | `PRIMARY` | 1,912 | Using where |

**`Using temporary; Using filesort` is the root finding.** MySQL cannot
apply `LIMIT 20 OFFSET 0` until it has built and sorted the *entire*
joined, filtered result set (est. 58,163 items x ~11 physical-warehouse
rows each, before the 50% join-filter estimate ~ hundreds of thousands
of rows) - no index exists that satisfies `ORDER BY i.brand_cd,
s.item_cd, s.wh_cd` across this join, so the sort cannot be pushed down
or avoided. The `COUNT(*)` companion query, by contrast, has no `ORDER
BY` and shows `Using where; Using index` on `ms_item` (no filesort) -
**the list query's sort, not the count, is the bottleneck**, confirmed
by the plan alone (not re-verified via `EXPLAIN ANALYZE` this session,
per §1's caveat).

`SHOW INDEX` confirms no composite index exists that could satisfy this
`ORDER BY`: `ms_item` has a standalone `brand_cd` index and a standalone
`del_flg` index, but nothing covering `(del_flg, brand_cd, item_cd)`
together; `ms_stk` has `PRIMARY(WH_CD, ITEM_CD)` and a `(WH_CD, ITEM_CD,
DEL_FLG)` secondary index, neither leading with `ITEM_CD` in a way that
would help this query's `i`-driven access path.

## 6. Production Row Scale

- `ms_item` non-deleted: ~58,163 matched by the `del_flg` index alone in
  this plan (a looser count than the ~47,280 Stage 5I/5K figure, which
  additionally requires Brand-addressable/calc4-relevant filtering not
  applied here - Warehouse Stock has no such narrowing, by design, since
  it is a physical-stock browse, not a candidate list).
- `ms_stk`: 1,518,842 total rows (Stage 2's own figure, re-confirmed
  present and unchanged this session via `SHOW INDEX`'s own cardinality
  estimates, ~1.3-1.4M).
- Estimated rows MySQL must generate and sort before `LIMIT 20` can trim
  them: on the order of **several hundred thousand** (58,163 x ~11,
  discounted by the plan's own 50% join-filter estimate) - this is the
  direct cause of the observed multi-minute (and still not complete)
  hang.

**Every one of §6's specific instructed checks is confirmed present**:
full-shaped `ms_stk` join without an early bound, `ms_item x ms_stk`
processing far beyond what a 20-row page needs, and (per the `EXPLAIN`
plan) a filesort MySQL cannot avoid - all before any row reaches the
`LIMIT`. Pagination is NOT materialized in Java (`WarehouseStockService.list`
passes `limit`/`offset` straight into the SQL, confirmed by direct
source read) - the anti-pattern is entirely inside the SQL's own
sort-before-limit shape, not a Java-side full-fetch.

## 7. Root Cause

**A missing/insufficient index for `WarehouseStockListQuery.sql`'s
`ORDER BY i.brand_cd, s.item_cd, s.wh_cd`, combined with the query's
join shape (ms_item driving into ms_stk's ~11-row-per-item physical-
warehouse fan-out) forcing MySQL into `Using temporary; Using filesort`
over an estimated several-hundred-thousand-row intermediate set before
`LIMIT 20 OFFSET 0` can apply.** This is the exact same *category* of
root cause Stage 5D/5E's RC-C found for `tr_po_dtl` (a JOIN/ORDER BY
shape that cannot be bounded before a full materialization) - but this
specific query/table pair was never audited or remediated by that Stage,
because Warehouse Stock was explicitly out of RC-A/B/C/E/F's scope
(Dashboard/Candidate-List/SKU-Detail-focused).

## 8. Why Stage 5G Missed It

Checked directly (`docs/real-data-audit/gops-stage5g-application-wide-performance-audit.md`
line 72): Stage 5G's own table records Warehouse Stock List as **"not
separately re-measured this Stage (paginated + item-set-filtered since
Phase 8-G; code-audited, no anti-pattern found)"** - explicitly a
**code-only audit**, never executed against real data at any scale.
Stage 5H's own later mention of Warehouse Stock (line 207) is an E2E
**correctness** test count ("Arrival/Warehouse Stock (11/11) - all
green"), run against the small Demo dataset, not a performance
measurement either. **Stage 5G's "OK" verdict was never backed by an
actual execution timing at any scale, Demo or Production** - a genuine
gap in that Stage's own audit method (pagination + a filter parameter
existing in the code does not, by itself, prove the query beneath it is
boundable), not a data-changed-since-then regression. Recorded here
plainly, as instructed, rather than glossed over.

## 9. Same-Pattern Screens at Risk (Listed Only - Not Fixed)

Grepped every `legacy/*.sql` file for the same shape (`ORDER BY` +
`LIMIT`/`OFFSET` against a query that does not first bound the row set
to one page before joining):

| File | Screen | Join shape | Risk vs. Warehouse Stock |
|---|---|---|---|
| `WarehouseStockListQuery.sql` | Warehouse Stock List | `ms_item` -> `ms_stk` (1:~11 fan-out) -> `ms_comm` | **Confirmed severe (this RCA)** |
| `RecommendedQtySkuIdsQuery.sql` | Candidate List / Stock-Sales List, **only** when `supplierCode`/`minStock`/`maxStock`/`minSales`/`maxSales` is set (the "full" variant - RC-I's lean variant, used for the common Brand-only/unfiltered case, only joins `ms_stk` 1:1 via the `'XX'` aggregate row, no fan-out) | `ms_item` -> `ms_stk` (1:1, `'XX'` row) -> `tr_po_dtl`/`tr_po` derived table | Same unindexed `ORDER BY i.brand_cd, i.item_cd` shape, but base cardinality stays at `ms_item` scale (~58K), not multiplied by warehouse count - **not measured this session**, plausibly much less severe, not confirmed safe either |
| `PriceCandidateListQuery.sql` (wraps `PriceReadQuery.sql`) | Price Change candidate search | `ms_item` -> `ms_comm` only (no `ms_stk`) | Same `ORDER BY brand_cd, item_cd` shape, `ms_item`-only cardinality (~58K) - **not measured this session** |

**Not fixed, not further characterized this session** (audit scope, per
instruction §9/§1). Flagged as same-pattern risk for a future,
dedicated Stage to measure and, if warranted, remediate - the two
smaller-cardinality cases are plausibly fine in practice (Stage 5H's own
RC-I precedent already reduced the common Candidate List path's
cardinality for exactly this reason) but **were not verified**, so they
are not being asserted safe either.

## 10. Recommended Minimal Remediation (Not Applied)

Two independent options exist, matching Stage 5E RC-C's own precedent
for the structurally identical `tr_po_dtl` problem - neither applied
this session:

1. **G-OPS-side query redesign** (no Production schema change): a
   two-step pagination, resolving the page's `(item_cd, wh_cd)` pairs
   first via a bounded/indexable query, then fetching full detail only
   for that small set - the same pattern RC-C already established for
   Candidate List/Stock-Sales.
2. **Production DB index** (would require Gulliver approval, out of
   this session's authority entirely): a composite index covering
   `ms_item(del_flg, brand_cd, item_cd)` would let this exact `ORDER BY`
   be satisfied without a filesort.

Per Stage 5E's own precedent, redesign-first (no schema change) is the
lower-risk starting recommendation - not decided or applied here.

## 11. Expected Performance After Remediation

Not modeled quantitatively this session (would require implementing and
measuring one of §10's options, out of this audit's scope) - qualitatively,
matching RC-C's own measured before/after ratio for the structurally
identical `tr_po_dtl` fix (a query that could not complete in a
reasonable time became sub-second once bounded before the join), a
comparable improvement is plausible but **not verified or promised**.

## 12. Operational Note (Observed, Not a Fix)

At the time of this report, **two Legacy connection-pool slots (of 5
total) remain occupied by the still-executing, still-not-returned
queries** (§1) - confirmed via `information_schema.processlist`, not
killed (per instruction, the environment was left exactly as found).
If repeated manual retries accumulate further concurrent Warehouse Stock
requests, the Legacy pool (`maximum-pool-size: 5`) could become
exhausted, which would then also block unrelated screens (Dashboard,
Candidate List, etc.) that share the same pool - worth being aware of
during continued manual verification, though not itself evidence of a
second, independent defect.

## 13. Final Judgment

**C. LEGACY SQL BOTTLENECK**

Confirmed unambiguously: the API never returns (ruling out A, Frontend);
no Java-side full-materialization-then-paginate exists (ruling out a B
component); the `EXPLAIN` plan shows exactly one bottleneck mechanism
(`Using temporary; Using filesort` on the `ORDER BY`, against an
unbounded several-hundred-thousand-row intermediate set) with no
secondary contributing layer identified - not D. Fully reproduced, not
E.

## 14. Stop

Per instruction: audit complete, no fix applied, no code/SQL/index/
Legacy/Snapshot/Read-Model change made. Awaiting ChatGPT Tech Lead
review before any remediation.
