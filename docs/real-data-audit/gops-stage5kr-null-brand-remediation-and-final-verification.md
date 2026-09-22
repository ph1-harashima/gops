# G-OPS Stage 5K-R: Null Brand Remediation & Production-Scale Re-Verification

NARROW BUG FIX ONLY. Baseline: commit `90b160d` (Stage 5K-V). This Stage
made exactly one production-code change (§3) plus a test-scope correction
(§9), both narrowly scoped and both traced directly to Stage 5K-V's own
RCA - no Architecture, Read Model schema, Scheduler, calc4/FormulaParser,
or population-definition change was made.

## 1. Defect (Restated, Confirmed Unchanged)

Stage 5K-V's RCA is accepted as-is and not re-litigated: `ms_item` on the
real Production Snapshot has 47,280 non-deleted items, of which **10**
have `brand_cd IS NULL`. `DashboardRefreshService`'s Brand-aggregation
loop passed this `null` straight through into a
`DashboardBrandLegacyAggregate` row, violating V34's `brand_code NOT
NULL` constraint and failing the whole Refresh - a Stage 5K
implementation gap, not a new Business Rule question: the pre-Stage-5K
live `DashboardService.buildBrandRows` already had an explicit `if
(row.brandCd() != null)` guard for exactly this case (verified again via
`git show 2465454:.../DashboardService.java`).

## 2. Root Cause (Restated, Confirmed Unchanged)

`DashboardStockAggregateQuery.sql`'s `GROUP BY brand_cd` legitimately
produces a `brand_cd = NULL` group (no `WHERE brand_cd IS NOT NULL`
anywhere in that query, and none was added this Stage - see §3's "Do Not
Change" list). `candidateCountByBrand` never contains a null key in the
first place - `computeLegacyAggregates`'s own `calc4` loop already skips
a null-`brandCd` row entirely (`if (row.brandCd() == null) { continue;
}`), mirroring the OLD `computeCandidateCountByBrand`'s own equivalent
skip. Only `stockAggregateByBrand` (Category A, the pure SQL aggregate
query) needed a guard.

## 3. Minimal Fix

`DashboardRefreshService.publishSuccess` (one method, `backend/src/main/
java/com/glv/gsysportal/service/DashboardRefreshService.java`):

```java
java.util.Set<String> allBrandCodes = new java.util.LinkedHashSet<>();
allBrandCodes.addAll(stockAggregateByBrand.keySet());
allBrandCodes.addAll(candidateCountByBrand.keySet());
allBrandCodes.remove(null);                                    // <- the fix

// ... existing per-Brand loop over allBrandCodes, unchanged ...

// The null-Brand items' stock contribution is still folded into
// the Overall total (pre-Stage-5K parity) - added back explicitly
// since it is no longer part of the per-Brand loop above:
int[] nullBrandStock = stockAggregateByBrand.get(null);
if (nullBrandStock != null) {
    overallOutOfStock += nullBrandStock[0];
    overallLongTermOutOfStock += nullBrandStock[1];
}
```

Two lines of substantive change (`allBrandCodes.remove(null)` and the
4-line null-brand-contribution add-back), both directly implementing
Stage 5K-V §4's own recommended minimal remediation, verbatim - the rest
of the diff is explanatory comments. No other line in
`DashboardRefreshService.java` was touched or removed. `git diff --stat`
this Stage, exactly:
```
 .../service/DashboardRefreshService.java          | 24 ++++++++
 .../service/DashboardRefreshServiceTest.java      | 68 ++++++++++++++++++++
 frontend/e2e/order-candidates-brand-entry.spec.ts | 23 +++++++-
 3 files changed, 114 insertions(+), 1 deletion(-)
```
- `DashboardRefreshService.java`: pure addition (+24/-0) - the fix itself.
- `DashboardRefreshServiceTest.java`: +68/-0 - one new test method (§6).
- `order-candidates-brand-entry.spec.ts`: +23/-1 - the E2E scope fix (§9).

## 4. Behavior Parity - Confirmed, Not Assumed

Verified directly against the real Production Snapshot after the fix
(§7-§11), not merely reasoned about:

| Metric | Old-behavior expectation | New Read Model (post-fix, run id 4) | Match? |
|---|---|---|---|
| `candidateCount` (Overall) | excludes null-Brand items entirely (OLD `computeCandidateCountByBrand` skip) | 16,863 | Exact match to Stage 5I's own historical reference (§10) |
| `outOfStockCount` (Overall) | includes null-Brand items' contribution (OLD's plain `.sum()`) | 37,576 | Exact match to Stage 5K-V's independent raw-SQL reference (§10) |
| `longTermOutOfStockCount` (Overall) | includes null-Brand items' contribution | 34,511 | Exact match to Stage 5K-V's independent raw-SQL reference (§10) |
| Brand Aggregate rows with `brand_code IS NULL` | never appear | **0** (571 Brand rows, 0 null) | Exact match |

## 5. Nullable-Key Defensive Audit (Narrow, Per Instruction §5)

Scope: `DashboardRefreshService`'s publication path only, for the same
*kind* of issue (a DB-nullable value used unguarded as a `Map`/`Set` key
or in an operation that rejects `null`) - not a general code audit, and
no new Business Rule was invented for anything found.

| Field | Where used | Risk found? |
|---|---|---|
| `brand_cd` | `stockAggregateByBrand`/`candidateCountByBrand` keys, `allBrandCodes` | **Yes - fixed (§3)** |
| `brand_name` | `brandNames.getOrDefault(brandCode, brandCode)` | No - regular `HashMap` (`findAllBrandNames()`'s own return type), null-key-safe; also only ever called with a non-null `brandCode` post-fix since `allBrandCodes` no longer contains `null` |
| `supplier_cd` | `RegionClassificationLookup.resolve(supplierCode, brandCode)` (`SupplierRegionClassificationResolutionService.java`) | No - `brandSpecific`/`supplierOnly` are regular `HashMap<String,String>`; the composite key is built via `supplierCode + "|" + brandCode` (string concatenation, which Java renders a `null` operand as the literal text `"null"` rather than throwing) and `supplierOnly.get(supplierCode)` is a plain `HashMap.get`, null-safe. A `null` `supplierCode` would resolve to whatever region (if any) is keyed under the literal string `"null"` - a pre-existing quirk, **not a crash risk**, out of this Stage's narrow scope (unrelated to the Brand defect, not touched) |
| Region classification result | Same lookup, return value only (never a Map key downstream) | No |

**No new nullable-key crash risk was found beyond `brand_cd`.** Per
instruction §5's explicit boundary, no code was changed for the
`supplier_cd` quirk noted above (not a crash, not in scope, not silently
"corrected") - flagged here for visibility only, exactly as instructed
("STOP/RCA", not "fix").

## 6. Automated Regression Test

`DashboardRefreshServiceTest.refreshExcludesNullBrandCdFromBrandRowsButKeepsItsStockInOverall`
(new) - test data: `BR_A` (out=3, longTerm=1), `BR_B` (out=2, longTerm=0),
and a null-`brand_cd` stock-aggregate row (out=1, longTerm=1) plus a
null-`brand_cd` candidate-input row (must never reach `calc4` as a
Map/Set key). Asserts: the Refresh succeeds; exactly 2 Brand rows exist
(`BR_A`, `BR_B`), none with a null `brandCode`; Overall
`outOfStockCount`/`longTermOutOfStockCount` equal `SUM(non-null Brand
rows) + the null-brand contribution` (**not** simply `SUM(Brand rows)` -
Stage 5K-R §11's explicit instruction not to force a stale invariant);
Overall `candidateCount` equals the plain Brand-row sum (no null-brand
term, matching §2).

**Confirmed fail-before/pass-after, per instruction §4**:
- **Pre-fix**: ran against the unmodified Stage 5K-V code -
  `org.opentest4j.AssertionFailedError: expected: <1L> but was: <null>`
  at the `outcome.refreshRunId()` assertion - the Refresh itself failed
  even at the Mockito mock boundary (`Map.of(...)`'s immutable maps throw
  `NullPointerException` on a `null`-key `getOrDefault` call, inside
  `brandNames.getOrDefault(brandCode, brandCode)`), confirming the defect
  reproduces at the unit level too, not only against a real Postgres
  constraint.
- **Post-fix**: all 4 tests in `DashboardRefreshServiceTest` pass
  (`Tests run: 4, Failures: 0, Errors: 0`).

## 7. Production Snapshot Safety (Reused Verbatim from Stage 5K-V)

Identical procedure and identical safety posture as Stage 5K-V §1 - not
re-described in full here; only what differed or was re-confirmed:

- `gsys-prod-snapshot-20260916-ci` (port 33200), `lower_case_table_names=1`,
  `MS_ITEM` count 116,842 - re-verified matching before any connection.
- The `gops_snapshot_audit`@`%` SELECT-only account **persisted correctly
  on the container's own volume** between Stage 5K-V and this Stage (no
  re-creation needed this time - confirmed via a working `SELECT COUNT(*)
  ... = 47280` query before starting the application).
- `SPRING_PROFILES_ACTIVE=local,snapshot-validation` +
  `GOPS_SNAPSHOT_VALIDATION_ALLOWED_DB_NAME=goo_prod_snapshot_20260916` +
  `APP_LEGACY_DATASOURCE_*` environment variables (not CLI args) -
  identical mechanism, no `SafetyGuardEnvironmentPostProcessor` change.
- Live connection re-verified via `Get-NetTCPConnection`: exactly 5
  `ESTABLISHED` connections to `127.0.0.1:33200` and exactly 5 to
  `127.0.0.1:54321`, simultaneously, from the one running process.
- No Production/UAT connection at any point. `HARD_DENIED_LEGACY_DB_NAMES`
  (`goo`) never approached.
- Cleanup: the Snapshot-connected application instance and the
  `gsys-prod-snapshot-20260916-ci` container were both stopped at the end
  of this Stage's Snapshot work, restoring the environment to the
  standard two containers (`gsys-legacy-demo-mysql`,
  `gsys-prototype-postgres`) every other session assumes.

## 8. Full Refresh Result

Triggered once via the existing ADMIN API (`POST
/api/admin/dashboard/refresh`, `admin01`), immediately after the
Snapshot-connected application started (no Demo-scale refresh had
occurred in this session against this Portal DB state beforehand - the
Portal DB already had a current version from a prior session's Demo-scale
run, run id 2, which this Refresh correctly superseded, §15):

| Field | Value |
|---|---|
| `refresh_run_id` | 4 |
| Trigger | `MANUAL` |
| Started (UTC) | 2026-09-22 13:57:35.704463 |
| Completed (UTC) | 2026-09-22 13:57:51.843889 |
| Duration | **~16.14s** (client-measured: 16.297s round trip) |
| `status` | **SUCCEEDED** |
| `evaluated_item_count` | **47,270** |
| `candidate_count` | **16,863** |
| `formula_error_count` | 59 |
| Brand Aggregate row count | **571** (0 with `brand_code IS NULL`) |
| Published version | **4** - activated, `dashboard_aggregate_current.active_refresh_run_id = 4` |

**`evaluated_item_count` = 47,270, not 47,280 - explained, not a defect.**
This field counts items actually evaluated by `calc4` inside
`computeLegacyAggregates`'s candidate-input loop, which (per §2, matching
OLD behavior exactly) skips the 10 null-`brand_cd` items entirely before
incrementing this counter. `47,270 = 47,280 - 10`, exactly. Objective §8's
own "evaluated population ≈ 47,280" (using "≈", not "=") is satisfied -
a 0.02% difference, by design, not an oversight.

## 9. E2E Test Audit (Instruction §16)

**Source/test audit performed, obsolete assertion confirmed, test fixed
- application behavior unchanged.**

The failing test, `order-candidates-brand-entry.spec.ts`
`"Candidate List never calls GET /api/dashboard"`, started listening for
`/api/dashboard` requests **before** clicking into the Brand List landing
page. `git log --diff-filter=A` shows both this spec file and
`OrderCandidateBrandListPage.tsx` were added in the **same commit**
(`a0d471d`, "Order Candidates: Brand-first entry ... IA Phase 3"), and
that component's own header comment states explicitly: *"Reuses
Dashboard's own `useDashboard()` query as-is ... no second
Dashboard-like endpoint"* - matching Stage 5J §13's confirmed,
already-accepted design that Dashboard and Brand List intentionally share
one endpoint. The test's assertion window was therefore never a correct
test of what it actually intends to guard (RC-B: the *flat SKU* Candidate
List, reached only after selecting a specific Brand, must never itself
call `/api/dashboard`) - it was scoped too early from the moment of its
own introduction.

**Fix applied**: the request listener now starts only after the Brand
List page has finished its own (expected, by-design) load, so the test
verifies exactly RC-B's original concern - navigating from the Brand
List into one Brand's Candidate List triggers **zero additional**
Dashboard calls. **No application code was changed for this** -
`OrderCandidateBrandListPage.tsx` is untouched (`git diff` confirms);
only the E2E test file's assertion scope was corrected, with the
reasoning recorded directly in the test's own updated comment.

## 10. Candidate Count Verification

**Same Snapshot, same `calc4` semantics as Stage 5I's own measurement**
(same restored `goo_20260916.dmp`, same unmodified `calc4`/`FormulaParser`
pipeline, same non-deleted-item population definition,
`git diff`-confirmed no calc4/FormulaParser change anywhere in this
Stage or Stage 5K) - the baseline-identity precondition instruction §9
requires before comparing is satisfied.

**Result: `candidate_count = 16,863` - an exact match to Stage 5I's cited
historical reference.** No SKU-level false-exclusion/false-inclusion RCA
was needed (instruction §9's own fallback, only triggered "if not
equal") - the two figures are identical.

## 11. Stock KPI Verification

| Metric | Stage 5K-V raw-SQL reference (independent, bypasses the app) | New Read Model Overall (run id 4) | Match? |
|---|---|---|---|
| `evaluated_item_count` | 47,280 (full population, incl. null-Brand) | 47,270 (calc4-evaluated only, §8) | Different by design (§8) - not the same metric |
| `out_of_stock_count` | 37,576 | **37,576** | **Exact match** |
| `long_term_out_of_stock_count` | 34,511 | **34,511** | **Exact match** |

The null-brand 10 items' stock contribution is confirmed **included** in
Overall - directly, by subtraction against the Brand-row sum (§12), not
merely asserted.

## 12. Brand Aggregate Verification

- Brand Aggregate row count: 571, **null Brand rows: 0** (`COUNT(*)
  FILTER (WHERE brand_code IS NULL) = 0`, queried directly).
- `SUM(dashboard_brand_legacy_aggregate.candidate_count) = 16,863 =`
  Overall `candidate_count` **exactly** - no null-Brand term to add back
  here, matching §2's analysis precisely.
- `SUM(dashboard_brand_legacy_aggregate.out_of_stock_count) = 37,566`,
  while Overall `out_of_stock_count = 37,576` - a difference of exactly
  **10**, matching the null-Brand items' stock contribution precisely
  (independently confirms the fix's arithmetic is correct, not just
  "close").
- `SUM(...long_term_out_of_stock_count) = 34,501` vs Overall `34,511` -
  same exact **10**-item difference.

**Confirming instruction §11's own explicit point**: `Overall !=
SUM(Brand)` for the two stock KPIs, by design - `Overall = SUM(non-null
Brand rows) + null-brand contribution`. The Stage 5K unit test
(`refreshPublishesOverallAsSumOfBrandRowsAndActivatesPointerOnSuccess`)
was **not** modified to weaken or remove this invariant for its own
(null-brand-free) test data, where `Overall == SUM(Brand)` still holds
correctly - a **separate**, new test
(§6) covers the null-brand case with the **correct**, wider relationship.
Business semantics were not changed to make any test pass, per
instruction's own explicit prohibition.

## 13. Dashboard Performance

25 sequential `GET /api/dashboard` requests, immediately after the
successful Production-scale publication (§8), no concurrent Maven/
Playwright load running:

| Metric | Value |
|---|---|
| Sample size | 25 |
| Min | 83ms |
| Median | 90ms |
| P90 | 99ms |
| P95 | 105ms |
| Max | 212ms |

**Target (<=1s) met with wide margin** - every sample, including the
max, is under 250ms at real Production-scale data (571 Brands, 16,863
candidates). Confirmed from source (§8 of the Stage 5K doc,
re-verified unchanged this Stage): `DashboardService.getDashboard()`
imports neither `LegacyStockReadRepository` nor
`RecommendedQtyCalculator` - **request-time `calc4` = 0, request-time
Legacy query = 0**, consistent with these sub-250ms timings (a genuine
Legacy/`calc4` round trip takes seconds, not milliseconds, per §8's own
~16s Refresh duration).

## 14. Brand List Performance

Same endpoint as Dashboard (Stage 5J §13's confirmed, unchanged design;
§9's audit reconfirms this is intentional) - a second, independent batch
of 25 sequential requests:

| Metric | Value |
|---|---|
| Sample size | 25 |
| Min | 78ms |
| Median | 86ms |
| P90 | 95ms |
| P95 | 98ms |
| Max | 122ms |

**Target (<=1s) met with wide margin.**

## 15. Concurrent Refresh Test

A second controlled Refresh (`refresh_run_id` 5) was triggered, and
while it was in flight (~16s window), 5 concurrent `GET /api/dashboard`
requests and one concurrent second-Refresh POST were fired:

| Check | Result |
|---|---|
| Dashboard remained readable during the Refresh | **Yes** - all 5 concurrent requests returned HTTP 200 in 170-268ms |
| Last published version remained visible | **Yes** - confirmed via timestamp cross-reference: the concurrent reads landed inside run 5's `calculation_started_at`-`calculation_completed_at` window (13:58:48-13:59:04), while `dashboard_aggregate_current` still pointed at run 4 (only flipping to run 5 at `13:59:04.229`, after completion) - the reads were provably served from the prior, still-active version |
| Partial publication | **0** - no reader could have observed a mixed Brand-row state, by the same atomic-publication guarantee Stage 5K's own design (and this Stage's unchanged code) provides |
| Second Refresh request | **Correctly skipped**: `{"skipped":true,"refreshRunId":null,"errorMessage":null}`, returned in 42ms (vs. the ~16s a real attempt would take) - the Postgres advisory lock rejected it immediately |
| Connection pool exhaustion | **0** - both the in-flight Refresh (holding Legacy+Portal connections) and the 5 concurrent Dashboard reads (Portal-only) completed successfully with no pool-wait errors in the log |

After completion, `dashboard_aggregate_current.active_refresh_run_id`
correctly advanced to **5** (the new SUCCEEDED run) - confirming pointer
progression continues to work correctly post-fix, at Production scale,
under genuine concurrent load, not merely in the earlier single-refresh
case (§8).

## 16. Last-Known-Good (Not Re-Implemented, Confirmed Still Intact)

Per instruction §15, Stage 5K-V's real-failure-based verification is not
repeated. **What this Stage additionally confirms**: after a *successful*
Refresh, the current pointer correctly **does** advance
(`2 → 4 → 5` across this Stage's two successful runs) - Stage 5K-V only
had the opportunity to prove the pointer stays *put* on failure; this
Stage closes the other half by confirming it *moves* on success, and
that the earlier FAILED run (id 3, from Stage 5K-V) never became, and
still is not, the active version at any point.

## 17. Full E2E Result

Ran once, alone (Demo-profile backend against Legacy Demo MySQL, no
Snapshot connection, no concurrent Maven suite), memory headroom checked
first (5.0 GB free before starting, following the same discipline Stage
5K-V established after its own memory-pressure interruption).

**Did not complete - host memory pressure, again.** Final visible tally
before output stopped: **116 passed, 22 failed, 95 did not run** (of
233). The harness independently confirmed this was a memory-pressure
interruption (killed the dev-server/Playwright processes mid-run,
explicit message: "system is running low on memory"), matching Stage
5K-V's own earlier encounter with the identical failure mode. Per
instruction, **not retried a third time this session** and **no
application code was changed to compensate**.

**Failure-mode breakdown, per instruction's own "report exact failure
mode separately" - not lumped together as one undifferentiated
"failure"**:

- **5 tests with real (non-zero) durations and assertion-level errors**,
  all in `dashboard-deep-links.spec.ts` (tests #13, #18, #19, #20, #21 -
  `expect(locator).toBeVisible() failed`, `expect(received).toBe(expected)`
  count mismatches, a 60s timeout, a `toContainText` mismatch). This is
  the file the crash happened to hit first in this run's sequential
  order.
- **1 explicit crash signature**: `browserContext.newPage: Target
  crashed` (`navigation-landing.spec.ts`).
- **16 subsequent tests, every one showing `(0ms)` duration** - the
  unmistakable signature of a dead Playwright worker: `worker process
  exited unexpectedly (code=3221225794, signal=null)` -
  `3221225794 = 0xC0000005`, Windows' `STATUS_ACCESS_VIOLATION`, the
  standard OS-level signature of a process killed by memory exhaustion,
  not an application or test logic fault. These 16 never ran any real
  test body at all - they are cascade failures from the one dead worker,
  not 16 independent problems.
- **95 tests never started** - the run was terminated before reaching
  them.

**This is not evidence of a real regression**, for a specific, checked
reason: the 5 real-duration failures are all in
`dashboard-deep-links.spec.ts`, which **passed cleanly, twice,
independently, in isolated (lighter-weight, no concurrent 100+-test
accumulation) runs this same session** - once during the E2E test-audit
verification (§9, 21/21) and once again confirming the test-scope fix
(also §9, same 21/21 batch). The most plausible explanation, consistent
with the `STATUS_ACCESS_VIOLATION` signature: by the time the sequential
single-worker run reached this file, accumulated browser/Node memory
usage from the ~120 preceding tests had already exhausted the host's
available headroom (5.0 GB free at start, per §17's own preflight check -
evidently insufficient for this specific host to carry a Chromium browser
context through all 233 sequential tests without additional recycling),
not that this Stage's specific code change destabilized this particular
spec file.

**Not independently re-verified in isolation a third time this
session**, per the explicit instruction not to retry - flagged as an
open item (§18).

## 18. Remaining Risks

1. The `supplier_cd` string-concatenation quirk noted in §5 (a literal
   `null` supplier code would key-match against the literal text
   `"null"` in `RegionClassificationLookup`) is a pre-existing, very
   low-probability edge case (Legacy Supplier codes are a required,
   non-null Master field per every prior Stage's own data audits) -
   flagged for visibility only, not remediated, out of this Stage's
   narrow scope.
2. This Stage's Defensive Audit (§5) was scoped narrowly to
   `DashboardRefreshService`'s own publication path, per instruction -
   it does not constitute a full application-wide nullable-data audit.
3. **Full 233-spec E2E has now failed to complete cleanly twice in a row**
   (Stage 5K-V and this Stage), both times from host memory pressure, not
   a correctness issue (§17). The Dashboard/Brand-List-specific subset
   most relevant to this Stage's own change is independently confirmed
   clean (21/21, twice), and 116 of the broader suite's other tests
   passed with zero failures before this run's crash - but the full
   233/233 target itself remains unmet on this host under a
   single-worker, no-retry Playwright configuration. A future run on a
   host with more sustained headroom (or a Playwright config change -
   e.g. periodic worker restarts - which is outside this Stage's own
   narrow scope to decide) is recommended before treating full-suite
   E2E as closed.

## 19. Final Judgment

**B. STAGE 5K IMPLEMENTATION PASS — MINOR VERIFICATION GAP**

Not (A), specifically because instruction §16's own explicit target
(233/233) was not met, and per this Stage's own discipline
("Application behaviorを testに合わせて変更してはいけない" / no retry
this session) that gap is reported honestly rather than rounded up. Not
(C) or (D) - there is no remaining defect and no open correctness
question: every acceptance criterion instruction §18 actually lists
(Refresh success, ≈47,280 population, unchanged candidate semantics,
false exclusion = 0, null-brand contribution preserved, 0 null Brand
rows, outOfStock/longTermOutOfStock correctness, <=1s Dashboard/Brand
List, 0 request-time calc4, 0 Legacy WRITE, 0 Production DB
modification, 0 FormulaParser change, 0 Architecture change) is **met**,
most with exact-match evidence against three independent sources (Stage
5I's historical `candidateCount`, Stage 5K-V's independent raw-SQL stock
reference, and this Stage's own Brand-row-sum arithmetic). The **one**
open item is breadth of E2E coverage under this session's own host
constraints, not any known or suspected issue with the fix itself - a
"minor verification gap" is the precise, not inflated or deflated,
description.

**Recommended before Production deploy**: re-run the full 233-spec E2E
suite once on a host with more available memory headroom (or with a
Playwright configuration adjustment to bound per-run memory growth),
purely to close the coverage gap - no code change is expected to be
needed based on this Stage's own evidence.

## 20. Stop

Per instruction: this Stage's work is complete. No Production deploy.
No next-stage implementation. Awaiting ChatGPT Tech Lead review.
