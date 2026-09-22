# G-OPS Stage 5K Freeze: Dashboard Read Model — Accepted Final Status

FREEZE RECORD. This document consolidates Stage 5K / Stage 5K-V / Stage
5K-R into a single, unambiguous accepted-state reference. It supersedes
nothing - Stage 5J, the Production Batch Schedule Addendum, Stage 5K,
Stage 5K-V, and Stage 5K-R remain the authoritative design/implementation/
verification records; this document is the Tech-Lead-approved summary
of where that sequence landed. Baseline: commit `40ebeba` (Stage 5K-R).

## 1. Final Accepted Production-Scale Results

Measured against the isolated Production Snapshot dated **2026-09-16**
(`gsys-prod-snapshot-20260916-ci`, `goo_20260916.dmp`, unmodified since
its original restore) - Stage 5K-R §7-§15.

| Metric | Value |
|---|---|
| Physical `MS_ITEM` row count | 116,842 |
| Non-deleted population (`DEL_FLG IS NULL OR DEL_FLG=0`) | **47,280** |
| Of which, `brand_cd IS NULL` | 10 |
| `calc4`-evaluated (Brand-addressable) population | **47,270** |
| `candidateCount` | **16,863** (exact match, Stage 5I's historical reference) |
| `outOfStockCount` | **37,576** (exact match, Stage 5K-V's independent raw-SQL reference) |
| `longTermOutOfStockCount` | **34,511** (exact match, Stage 5K-V's independent raw-SQL reference) |
| Brand Aggregate rows | 571 |
| Brand Aggregate rows with `brand_code IS NULL` | **0** |
| Refresh result | **SUCCESS** |
| Refresh duration | 16.14s |
| Dashboard `GET /api/dashboard` | median 90ms, max 212ms (25 samples, no concurrent load) |
| Brand List (same endpoint) | median 86ms, max 122ms (25 samples, no concurrent load) |
| Second concurrent Refresh attempt | skipped in 42ms (single-flight) |
| Request-time `calc4` invocations | 0 |
| Request-time heavy Legacy calculation | 0 |
| Legacy WRITE | 0 |
| Production DB modification | 0 |
| `FormulaParser` change | 0 |
| Business Rule change | 0 |

## 2. Important Population Clarification

Stated precisely, to prevent misreading either figure in isolation:

- **47,280** = the full non-deleted Legacy population, and the figure
  relevant to **Overall stock aggregation** (`outOfStockCount`/
  `longTermOutOfStockCount`). All 47,280 items, including the 10 with a
  null `brand_cd`, contribute to these two Overall KPIs.
- **47,270** = the population after excluding the 10 null-`brand_cd`
  items **specifically from Brand-level publication and from
  `candidateCount`/`calc4` evaluation** - not a reduction of the
  Overall stock population.

**The 10 null-Brand items are never discarded from Overall stock KPIs.**
Per Stage 5K-R §3/§12's fix and verification, they contribute to Overall
`outOfStockCount`/`longTermOutOfStockCount` exactly as pre-Stage-5K
`DashboardService` behavior did (a plain sum over every stock-aggregate
row, regardless of Brand) - confirmed by direct arithmetic: `SUM(Brand
rows' out_of_stock_count) = 37,566`, while `Overall out_of_stock_count =
37,576`, a difference of exactly 10, i.e. the null-Brand items' own
contribution, present and accounted for. The same 10-item difference
holds for `longTermOutOfStockCount` (34,501 vs 34,511).

**`candidateCount`/`calc4` evaluation is the one place the 10 items are
genuinely excluded** - matching the pre-Stage-5K live
`computeCandidateCountByBrand`'s own `if (row.brandCd() == null) {
continue; }` behavior exactly, carried forward unchanged by Stage 5K-R.
This is not a new exclusion Stage 5K/5K-R introduced.

**Do not describe 47,270 as if 10 Legacy items were globally excluded
from the Read Model** - they are fully present in Overall stock
aggregation; only their Brand-level attribution and their `calc4`
candidacy are (by design, matching prior behavior) not evaluated.

## 3. Read Model Status

| Item | Status |
|---|---|
| Architecture (Portal DB Read Model, scheduled background Refresh, request-time read) | **FROZEN** |
| Business semantics (`candidateCount`/`outOfStockCount`/`longTermOutOfStockCount` definitions) | **FROZEN** |
| `calc4` semantics (`RecommendedQtyCalculator`/`FormulaParser`) | **FROZEN** - unmodified throughout Stage 5J-5K-R |
| RC-D (`FormulaParser` `[AT]`/`[AJ]`) | **CLOSED** (Addendum §11) |
| Null-Brand remediation (Stage 5K-V's Production-scale finding) | **CLOSED** (Stage 5K-R) |
| Production-scale correctness | **VERIFIED** (Stage 5K-R §10-§12, exact-match evidence against three independent sources) |
| Production-scale performance | **VERIFIED** (Stage 5K-R §13-§14, <=1s target met with wide margin) |
| Atomic publication | **VERIFIED** (Stage 5K §5 design + unit test; Stage 5K-R §15 real concurrent-load confirmation) |
| Last-known-good | **VERIFIED** (Stage 5K-V real-failure confirmation; Stage 5K-R confirms the complementary "pointer advances on success" half) |
| Single-flight | **VERIFIED** (Stage 5K unit test; Stage 5K-R §15 real concurrent-load confirmation, 42ms skip) |

## 4. E2E Status

Recorded precisely, so the latest incomplete run is not misread as an
application regression:

- **Stage 5K-V's full run**: 232/233. The one failure
  (`order-candidates-brand-entry.spec.ts` "Candidate List never calls GET
  /api/dashboard") was subsequently source/test-audited (Stage 5K-R §9)
  and confirmed **obsolete** - `OrderCandidateBrandListPage` intentionally
  reuses `useDashboard()` by design (Stage 5J §13, confirmed in that
  component's own header comment and by `git log` showing the page and
  its test were introduced in the same commit). **That test assertion has
  since been corrected** (its listener window now starts after the Brand
  List's own expected load) **without any change to application
  behavior** - re-verified passing (21/21, alongside every other
  Dashboard-relevant test) twice this Stage.
- **Latest Full E2E attempt** (Stage 5K-R §17): **did not complete
  cleanly**, due to host memory pressure (the harness itself killed the
  run, independent of any application or test defect). Tally: **116
  passed, 22 failures attributable to browser-crash/harness resource
  exhaustion** (`STATUS_ACCESS_VIOLATION` worker exits, not assertion
  failures against real Production/Demo data), **95 not executed**
  (run terminated before reaching them).
- **Dashboard/Brand List targeted tests**: **21/21 PASS, confirmed twice**
  independently, isolated from the memory-pressure-affected full run.

**Conclusion: no known Stage 5K functional defect remains.** The Full
E2E suite's incomplete run is an infrastructure/host-resource
verification gap, not a code defect - **Stage 5K implementation is not
being reopened for this reason**. A clean, uninterrupted Full E2E run
(233/233, under sufficient host memory headroom) remains an outstanding
**release verification item** before Production deployment, tracked here
explicitly rather than folded into "done."

## 5. Freeze

Stage 5K's Dashboard Read Model - architecture, business semantics,
`calc4` semantics, null-Brand handling, atomic publication, single-flight,
and last-known-good - is **FROZEN** as of commit `40ebeba`. No further
functional changes are in scope under the Stage 5J/5K/5K-V/5K-R line of
work. The one remaining, explicitly tracked item before Production
deployment is the release-verification gap in §4 (a full, uninterrupted
233/233 E2E run) - not a design, architecture, or correctness question.

Per instruction: no new functionality was added this Stage, no
deployment was performed, and no further Stage begins automatically -
awaiting explicit direction for the next step.
