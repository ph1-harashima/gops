# G-OPS Stage 5K-V: Production-Scale Final Verification

VERIFICATION ONLY. No business logic, architecture, Read Model schema,
scheduler, or UI change was made this Stage. Baseline: commit `324fd2d`
(Stage 5K implementation). This Stage found a genuine, previously-unknown
correctness defect at Production scale (§4/§13) - per instruction §10
("Any unexpected correctness difference: STOP and RCA"), the defect was
investigated to a precise, narrow root cause and **not patched** this
session; no business-rule adjustment was made to work around it.

## 1. Environment / Safety

Reused Stage 5B/5C's own established Snapshot Validation Gate mechanism
exactly - re-read from `SafetyGuardEnvironmentPostProcessor.java` and
`docs/real-data-audit/gops-stage5b-production-like-snapshot-environment.md`
before doing anything, per instruction. No guard code was modified,
weakened, or bypassed.

**Container**: `gsys-prod-snapshot-20260916-ci` (the case-insensitive
variant Stage 5B built specifically; the case-sensitive original,
`gsys-prod-snapshot-20260916`, was left stopped the entire session and
never connected to).

**Confirmed before any application startup**:
- `SHOW VARIABLES LIKE 'lower_case_table_names'` → `1`.
- `SELECT COUNT(*) FROM MS_ITEM` → `116,842` (matches Stage 5B/5D's own
  recorded figure exactly, confirming this is the same, unmodified restore).
- Legacy port: `33200`, mapped to the container's `3306`.

**Audit credential**: the dedicated `gops_snapshot_audit`@`%` account
Stage 1C/5B established (`GRANT SELECT ON goo_prod_snapshot_20260916.*`
only - re-verified via `SHOW GRANTS`, byte-for-byte identical to Stage
1C's own documented output). This session re-created the account (it did
not survive on the stopped/restarted container from prior Stages, exactly
as Stage 5B itself needed to do on its own new container) and additionally
had to reset its authentication plugin to `mysql_native_password` (from
the default `caching_sha2_password`) purely to get a **local mysql CLI
diagnostic query** working over plain TCP without SSL - this does not
affect the actual JDBC connection's security posture, which already used
`allowPublicKeyRetrieval=true` per the existing `LegacyDataSourceConfig`
URL convention. **Grant scope was re-verified unchanged after this reset**
(`SHOW GRANTS` - SELECT-only, same as before). A genuine, explicit write
attempt (`UPDATE ms_item ... LIMIT 1`) was tested and correctly rejected
(`ERROR 1142: UPDATE command denied to user 'gops_snapshot_audit'`) -
this account's SELECT-only enforcement was verified, not assumed.

**Application startup**: `SPRING_PROFILES_ACTIVE=local,snapshot-validation`
+ `GOPS_SNAPSHOT_VALIDATION_ALLOWED_DB_NAME=goo_prod_snapshot_20260916`,
both passed as process **environment variables**, not
`-Dspring-boot.run.arguments`, per Stage 5B's own documented fix for the
exact shell `&`-splitting failure this Addendum's predecessor session hit
last time (`APP_LEGACY_DATASOURCE_JDBC_URL`/`_USERNAME`/`_PASSWORD` env
vars, Spring Boot's relaxed-binding convention). `app.dashboard.refresh.enabled`
was set to `false` for this run (env var) so no background
`SCHEDULED` tick could race the one controlled Refresh this Stage
performed manually via the ADMIN API.

**Safety Guard engaged correctly, confirmed from the running log** (not
assumed):
```
SNAPSHOT VALIDATION MODE ACTIVE - Legacy datasource database name
'goo_prod_snapshot_20260916' is allowed for this run ONLY, via explicit
opt-in (snapshot-validation profile + GOPS_SNAPSHOT_VALIDATION_ALLOWED_DB_NAME
environment variable). READ-ONLY validation only - this must NEVER be
used to connect to real Production or UAT. The real Production schema
name ('goo') remains permanently denied regardless of this or any other
configuration.
```

**Live connection verified via `Get-NetTCPConnection` (TCP state, not log
inspection alone - same method Stage 5B used)**: the single running
process held exactly 5 `ESTABLISHED` connections to `127.0.0.1:33200`
(Legacy Snapshot) and exactly 5 to `127.0.0.1:54321` (local Portal
Postgres) simultaneously - matching both pools' configured
`maximum-pool-size`, cleanly separated, no cross-connection.

**Portal DB**: unchanged, local-only Postgres (`gsys_portal`, port
54321) - the same instance every other Stage's automated test suite and
this session's own Stage 5K work already used. Flyway's own log
confirmed its migration target was this Portal instance only, never the
Snapshot.

**No Production/UAT connection at any point** - `HARD_DENIED_LEGACY_DB_NAMES`
(`goo`) was never approached; only the pre-existing, already-isolated
Snapshot container was used throughout.

**Repository state recorded**: baseline commit `324fd2d`; `git status`
confirmed clean (no application code changes) immediately before and
after this Stage's work, aside from this deliverable document itself and
(session-only, not committed) log files.

**Cleanup**: the Snapshot-connected application instance was stopped
(`Stop-Process`) and the `gsys-prod-snapshot-20260916-ci` container was
stopped (`docker stop`) at the end of this Stage's Snapshot work,
restoring the environment to exactly the two containers (`gsys-legacy-demo-mysql`,
`gsys-prototype-postgres`) every other session already assumes running.
The `gops_snapshot_audit` account was left in place on the Snapshot
container's own volume (matching Stage 5B/5C's own precedent of
persisting, not deleting, this account between sessions).

## 2. Full Refresh Result

Triggered exactly once, via the existing ADMIN API
(`POST /api/admin/dashboard/refresh`, `DashboardAdminController` -
the same, unmodified endpoint Stage 5K built; no new trigger mechanism
created for this verification), authenticated as the existing seeded
`admin01` (ADMIN role) user.

| Field | Value |
|---|---|
| Trigger type | `MANUAL` |
| `refresh_run_id` | 3 |
| Started (UTC) | 2026-09-22 13:04:17.971657 |
| Completed (UTC) | 2026-09-22 13:04:34.836775 |
| Duration | **~16.9s** (client-measured round trip: 16.998s) |
| `status` | **FAILED** |
| `evaluated_item_count` | not recorded (failure occurred during publication, after the full population had already been read and evaluated in memory - see §4) |
| `candidate_count` | not recorded (same reason) |
| `formula_error_count` | not recorded (same reason - see §13 for FormulaParser activity observed live during this same attempt) |
| Published version | **None - the failed run was never activated** (§11 Last-Known-Good) |

**One full Refresh did NOT complete successfully over the real
population this Stage** - Objective §1 Question 1's answer is **No**,
for a specific, now-understood reason (§4), not an unknown one.

## 3. Population Result

The ~47,280 non-deleted-item population Stage 5I/5J/the Addendum all cite
was **independently confirmed reachable**, via a raw SQL query issued
directly against the Snapshot through the `gops_snapshot_audit` account
(bypassing the application entirely - this number does not depend on the
failed Refresh in any way):

```sql
SELECT COUNT(*) FROM ms_item WHERE (del_flg IS NULL OR del_flg = 0);
-- → 47,280 (exact match to Stage 5I's cited figure)
```

Of these, **10 items have `brand_cd IS NULL`** - the root cause of §4's
failure:

```sql
SELECT COUNT(*) FROM ms_item WHERE (del_flg IS NULL OR del_flg = 0) AND brand_cd IS NULL;
-- → 10
```

## 4. Root Cause Analysis (STOP per instruction §10)

**Defect**: `DashboardRefreshService.publishSuccess` builds the set of
Brand codes to persist as `stockAggregateByBrand.keySet() ∪
candidateCountByBrand.keySet()` (both `HashMap<String,...>`, which permit
a literal `null` key) and constructs one `DashboardBrandLegacyAggregate`
row per code with **no null check**. `DashboardStockAggregateQuery.sql`'s
`GROUP BY brand_cd` (no `WHERE brand_cd IS NOT NULL`) legitimately
produces a `brand_cd = NULL` group whenever any non-deleted item has no
Brand assigned - which the real Production Snapshot does (10 items),
while the small Demo dataset this Stage's own implementation session
validated against apparently does not (confirmed indirectly: Demo-scale
Refreshes, runs 1-2, both succeeded). This `null` key survives into the
`allBrandCodes` loop and is passed as `brandCode` to
`new DashboardBrandLegacyAggregate(runId, null, ...)`, which the V34
migration's `brand_code VARCHAR(10) NOT NULL` constraint correctly
rejects at INSERT time - failing the whole publish transaction (by
design, §5 of the Stage 5K doc: atomic, all-or-nothing).

**This is a regression from the pre-Stage-5K behavior**, not a newly
introduced business rule question. The OLD, removed
`DashboardService.buildBrandRows` (Stage 5E-era, verified via `git show`
of the pre-Stage-5K version of that file) explicitly guarded this exact
case:
```java
for (DashboardStockAggregateRow row : stockAggregateByBrand) {
    if (row.brandCd() != null) {                       // <- this check
        brandNameByCode.putIfAbsent(row.brandCd(), ...);
    }
}
```
i.e., the OLD code silently **excluded** a null-Brand row from ever
appearing in the per-Brand breakdown, while still folding its
`outOfStockCount`/`longTermOutOfStockCount` into the plain
**Overall** sum (a simple `.stream().mapToInt(...).sum()` over every row,
including the null-`brandCd` one - `Collectors.toMap`/keying by
`brandCd` was only used for the *lookup*, never for the summed total).
Stage 5K's `DashboardRefreshService` reimplemented this aggregation from
scratch (moving the logic from request-time to Refresh-time) and did not
carry this specific null-guard forward - an implementation gap in Stage
5K's own new code, not a change of intent, and not something either
`DashboardRefreshServiceTest`'s mocked-repository unit tests or the
Demo-data-backed `DashboardServiceIntegrationTest` could have caught,
since neither exercises a null-`brand_cd` row.

**Scope of the defect**: narrow and well-characterized - exactly 10 of
47,280 items (0.02%), affecting only the *aggregation/publication* step,
not `calc4`/`FormulaParser`/any Business Rule. The **overall**
`outOfStockCount`/`longTermOutOfStockCount` totals (§5) show what the
correct Overall figures would be if this defect did not block
publication - they already include these 10 items' contribution, since
that raw-SQL check does not filter on `brand_cd` at all.

**Not fixed this session** - per instruction §2/§10 ("Do NOT modify
business logic or architecture", "STOP and RCA"). **Recommended minimal
remediation** (for a future dedicated Stage, not applied here): restore
the same `brandCd() != null` guard in `DashboardRefreshService`'s
Brand-code aggregation, consistent with the pre-existing, already-accepted
OLD behavior (null-Brand items' stock counts continue to contribute to
the Overall total; they simply never get their own Brand-breakdown row,
exactly as before). This is a narrow bug fix restoring parity with
already-established, already-accepted behavior, not a new Business Rule
decision - but is explicitly flagged for Tech Lead review before
implementation, per this Stage's own scope boundary.

## 5. Candidate Count Comparison

**Not completed** - no Read Model was successfully published this
session to compare against (§2/§4). Per instruction §5's own caveat ("Do
NOT blindly require 16,863 unless the Snapshot data and calculation
baseline are confirmed identical") this was never going to be a strict
equality check even had the Refresh succeeded; moot regardless, since no
`candidate_count` value was produced.

**What WAS confirmed, from live logs during this exact failed attempt**:
`FormulaParser` errors fired repeatedly during the (unpublished,
in-memory) `calc4` evaluation pass - e.g. `Expected 2-3 multipliers in
FORMULA_11, found 0: IF(ROUNDDOWN([AY]-([AS]*2/2),0)<0,0,ROUNDDOWN([AY]-([AS]*2/2),0))`
- confirming the RC-D-related per-SKU degrade path Stage 5J/Addendum
already documented as expected and already-tolerated is genuinely
exercised at Production scale, and did **not** itself crash or block the
Refresh (the run reached the *publication* step, i.e., every `calc4`
evaluation completed, before failing on the unrelated null-`brand_cd`
issue) - a real, Production-scale confirmation of Stage 5J §16's
graceful-degrade claim, independent of §4's defect.

## 6. KPI Comparison

**Not completed** for the same reason as §5. A partial, independent
cross-check was performed instead (raw SQL, bypassing the application
entirely) to establish a reference baseline for the future remediation's
own regression check:

```sql
SELECT
    SUM(CASE WHEN current_stock = 0 THEN 1 ELSE 0 END)                  AS out_of_stock_count,
    SUM(CASE WHEN current_stock = 0 AND open_po = 0 THEN 1 ELSE 0 END)  AS long_term_out_of_stock_count,
    COUNT(*)                                                             AS evaluated_item_count
FROM ( /* same current_stock/open_po derivation as DashboardStockAggregateQuery.sql */ ) t;
```

| Metric | Value (raw SQL, full population incl. the 10 null-Brand items) |
|---|---|
| `evaluated_item_count` | 47,280 |
| `out_of_stock_count` | 37,576 |
| `long_term_out_of_stock_count` | 34,511 |

This is an **independent** verification (never touched
`DashboardRefreshService`/JPA/the Read Model tables at all) that the
Overall stock-aggregate figures a future successful Refresh should
produce are in this neighborhood - recorded as a reference for the
remediation Stage's own correctness check, not as this Stage's own
"comparison" deliverable (there is nothing on the "NEW" side to compare
it against yet).

## 7. Brand Aggregate Comparison

**Not completed** - blocked by §4 (no Brand rows were ever committed).

## 8. Refresh Duration

**~16.9s** for the failed attempt (§2) - population read + full `calc4`
evaluation over all ~47,280 items completed within this window (the
failure occurred at the persistence step, after evaluation finished).
Somewhat higher than Stage 5H's own ~13.6s Production-scale figure, both
measured on the same Snapshot; plausibly explained by this session's
concurrent JVM/Docker overhead (host was also running the Legacy Demo
MySQL and local Portal Postgres containers throughout, unlike a dedicated
measurement run) - not independently isolated to confirm.

## 9. Dashboard Performance

**Not meaningfully testable this session** - Objective §6 explicitly
calls for measurement "after successful publication", which did not
happen at Production scale (§2/§4). One single-sample check was taken
immediately after the failed Refresh, primarily as part of the
Last-Known-Good verification (§11), not as a performance benchmark:
`GET /api/dashboard` returned the still-active, older (Demo-scale) `run
2` data normally, confirming the read path itself was completely
unaffected by the concurrent Production-scale failure - but this is a
correctness/availability observation, not the 20-sample min/median/P90/
P95/max benchmark Objective §6 asks for, which requires a successfully-published
Production-scale Read Model to be meaningful and was not attempted
against stale Demo-scale data to avoid mislabeling it as a Production-scale
result.

## 10. Brand List Performance

Same as §9 - not meaningfully testable this session, same reason.

## 11. Concurrent Refresh Test

**Not performed as originally scoped** (§7's design - request Dashboard/
Brand List repeatedly *while* a Refresh is running - needs a Refresh
that is actually in flight for a measurable window, and this session's
one Refresh attempt failed rather than running to a state where
meaningful concurrent-read testing against it was planned separately).
**A related, real single-flight signal WAS observed incidentally**: no
second Refresh was attempted concurrently this session (Objective §2/§9's
"exactly one controlled full Refresh" instruction was followed literally
- only one was ever triggered), so this Stage adds no new single-flight
evidence beyond Stage 5K's own unit test
(`DashboardRefreshServiceTest.refreshSkipsWhenAdvisoryLockNotAcquired`,
still valid, unchanged, not re-run against the Snapshot this session).

## 12. Last-Known-Good Test

**PASS - verified against a real, not simulated, Production-scale
failure** (a stronger test than the "safe local test mechanism" the
instruction anticipated, since §4's genuine failure provided the
opportunity directly):

- `dashboard_aggregate_current.active_refresh_run_id` **before** the
  failed attempt: `2`.
- `dashboard_aggregate_current.active_refresh_run_id` **after** the
  failed attempt: `2` (**unchanged**, confirmed via direct query).
- `dashboard_refresh_run` id `3` (the failed attempt) is present with
  `status='FAILED'` and the full `error_message` recorded (§4) - failure
  observability confirmed working at Production scale, not just in the
  Demo-scale unit test.
- `GET /api/dashboard`, queried immediately after the failure, returned
  HTTP 200 with `run 2`'s data intact (`calculatedAt` matching run 2's
  own `activated_at`) - the Snapshot-scale failure caused **zero**
  disruption to what any Dashboard user would see.

This directly confirms Stage 5K's own
`DashboardRefreshServiceTest.refreshMarksFailedAndNeverActivatesPointerOnLegacyFailure`
unit test's guarantee holds under a real failure, not only a mocked one.

## 13. Full E2E Result

Ran once, alone (no concurrent Maven test suite, no Snapshot connection -
demo-profile backend against the Legacy Demo MySQL, matching the
instruction's explicit "memory-stable setup" / "avoid unnecessary dev
processes" guidance). Available memory headroom was checked before
starting (5.1 GB free of 15.9 GB total, after this session's own cleanup
of every stray process left over from the previous Stage 5K session).

**232/233 passed (25.2 minutes), zero worker crashes, zero
backend-unreachable errors.** The one failure is the exact same,
already-identified pre-existing issue reported in the Stage 5K
implementation doc's own §11
(`order-candidates-brand-entry.spec.ts` "Candidate List never calls GET
/api/dashboard" - `OrderCandidateBrandListPage` legitimately calls
`useDashboard()` by design, Stage 5J §13, unrelated to and unchanged by
any Stage 5K/5K-V work; confirmed again this session via the identical
failure text/stack). **No new failure was introduced by Stage 5K or by
this verification session** - every one of the other 232 tests,
including all Dashboard/Brand-List-relevant specs
(`dashboard-deep-links.spec.ts`, the remainder of
`order-candidates-brand-entry.spec.ts`), passed cleanly.

## 14. Remaining Risks

1. **§4's defect blocks Production-scale Refresh entirely** - the single
   most important finding this Stage produced. Until remediated, Stage
   5K cannot be considered Production-ready regardless of any other
   result.
2. Whether any *other* null-handling gap exists elsewhere in
   `DashboardRefreshService` (e.g., a null `supplier_cd` affecting region
   resolution) was not exhaustively audited this session - §4's specific
   defect was traced to its precise root cause and not generalized into
   a broader audit, per this Stage's own narrow verification scope.
3. §9/§10's performance/concurrency objectives remain **entirely
   unverified at Production scale** - blocked transitively by §4.
4. The 16.9s duration (§8) was not isolated from concurrent host load;
   Stage 5H's own ~13.6s figure remains the more carefully-measured
   reference.
5. Full E2E (§13) completed cleanly this session (232/233, the one
   failure pre-existing and unrelated) - no remaining risk from this
   area; this item is resolved, unlike the previous Stage 5K session's
   incomplete run.

## 15. Final Judgment

**C. STAGE 5K REMEDIATION REQUIRED**

Not (D) "CORRECTNESS MISMATCH - STOP / RCA REQUIRED", because the RCA is
already complete and precise (§4) - this is not an open, ambiguous
semantic question needing further investigation, but a specific,
narrowly-scoped, already-understood code defect (a missing null-Brand
guard, one that pre-Stage-5K code already handled correctly) with a clear,
low-risk, non-business-rule-changing recommended fix. Not (A) or (B),
since a full Refresh over the real Production population does not
currently complete at all - the feature is not Production-ready as-is,
regardless of how well everything downstream of a successful publish
performs (which remains unverified at Production scale as a direct
consequence, §9-§11).

**Recommended next step**: a narrowly-scoped remediation Stage that (1)
restores the null-`brandCd` guard in `DashboardRefreshService` consistent
with §4's analysis, (2) re-runs this exact Stage 5K-V verification
procedure against the same Snapshot to confirm one full Refresh completes
successfully and to complete §5/§6/§7/§9/§10/§11 for the first time, (3)
compares the resulting `candidateCount` against Stage 5I's 16,863
reference per this Stage's own §5 caveat (only meaningful if Snapshot
data is unchanged since that figure was recorded - not itself re-verified
this session).

## 16. Stop

Per instruction: verification is complete for this session. No
Production deploy. No next-stage implementation. No code was changed.
Awaiting ChatGPT Tech Lead review.
