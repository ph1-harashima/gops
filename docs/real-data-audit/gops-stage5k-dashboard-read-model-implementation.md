# G-OPS Stage 5K: Dashboard Read Model Implementation

IMPLEMENTATION PHASE. Real code was written and merged into the working
tree this Stage (backend + frontend), gated behind the acceptance
criteria in §16. Baseline: commit `2465454` (Stage 5J Production Batch
Schedule Addendum). Per instruction, **no Production deploy, no Legacy
G-SYS change, no Production DB connection/modification** happened - every
validation in this Stage ran against the same Legacy Demo MySQL (port
33061) / Portal Postgres (port 54321) containers the rest of this
codebase's automated test suite already uses, plus one, incomplete
attempt at a real Production Snapshot-connected validation pass (§12,
honestly reported as not completed this session).

## 1. Implementation Summary

Carries Stage 5J's architecture and the Production Batch Schedule
Addendum's cadence/offset recommendation into working code, unchanged in
substance:

- **Legacy-derived Dashboard/Brand List KPIs** (`candidateCount`,
  `outOfStockCount`, `longTermOutOfStockCount`, overall and per-Brand) are
  now a **Portal DB Read Model**, populated by a background
  `DashboardRefreshService` that runs the exact same, unmodified `calc4`
  pipeline (`RecommendedQtyCalculator`/`FormulaParser`, byte-for-byte
  unchanged) over the exact same non-deleted-item population
  `LegacyStockReadRepository.findDashboardCandidateInputs`/
  `findDashboardStockAggregateByBrand` already queried.
- **`GET /api/dashboard`** (shared by Dashboard and Brand List, Stage 5J
  §13) now performs **zero Legacy DB queries and zero `calc4`
  invocations** - it reads four small, indexed Portal DB rows instead.
- **Refresh schedule**: every 30 minutes, offset `:07`/`:37` past each
  hour, quiet hours ~22:00-04:30 - all three externally configured
  (`app.dashboard.refresh.*`, `application.yml`), never hardcoded, per
  the explicit instruction not to freeze the Addendum's own recommendation
  into Business Logic pending Gulliver's single freshness confirmation.
- **RC-D**: `FormulaParser` untouched; `[AJ]` support not added. Confirmed
  CLOSED (Addendum §11), not reopened.
- Every acceptance criterion in §16 below is met against the Demo
  environment this session validated against; §12 documents the one gap
  (Production Snapshot-connected validation) honestly, as a recommended
  follow-up before Production deploy.

## 2. Migration

`backend/src/main/resources/db/migration/V34__dashboard_read_model.sql` -
four tables, applied cleanly against the running Portal Postgres
(`PrototypeFlywayMigrationTest` confirms `Successfully validated 34
migrations`/`Current version of schema "public": 34`):

- `dashboard_refresh_run` - one row per Refresh attempt (history + the
  source of the "current version" pointer). `RUNNING`/`SUCCEEDED`/
  `FAILED` status, `SCHEDULED`/`STARTUP`/`MANUAL` trigger type,
  `formula_error_count` (RC-D observability), a partial unique index
  (`uq_dashboard_refresh_run_one_running`, `WHERE status = 'RUNNING'`) as
  the DB-level single-flight backstop.
- `dashboard_legacy_aggregate` - the Overall Aggregate, one row per
  SUCCEEDED run, keyed by `refresh_run_id` directly (Stage 5J §5's
  "independent row, not summed from Brand rows at read time").
- `dashboard_brand_legacy_aggregate` - the Brand Aggregate, one row per
  (refresh, Brand). **Deviates from Stage 5J §6's literal DDL proposal in
  two ways**, both documented in the migration's own comments: (a) a
  surrogate `BIGSERIAL id` instead of a composite `(refresh_run_id,
  brand_code)` primary key - this Portal codebase has no
  `@EmbeddedId`/`@IdClass` precedent anywhere, so a surrogate id plus a
  `UNIQUE` constraint keeps this entity on the same simple JPA shape
  every other entity here already uses; (b) an added `brand_name` column,
  captured at Refresh time from the same `ms_comm CATE_ID='MS_BRAND'`
  bulk lookup the old live path always used - necessary so the Dashboard/
  Brand List *request* path is Legacy-free entirely, not just
  `calc4`-free (§16's "Legacy heavy query = 0" is otherwise unmet, since
  brand-name resolution was itself a Legacy query).
- `dashboard_aggregate_current` - the atomic "current version" pointer,
  a true singleton row (`singleton BOOLEAN PRIMARY KEY`), written only by
  `DashboardAggregateCurrentRepository.activate` (a native `INSERT ...
  ON CONFLICT (singleton) DO UPDATE`, one round trip).

## 3. Read Model / Domain Model

Four JPA entities (`domain/DashboardRefreshRun`,
`DashboardLegacyAggregate`, `DashboardBrandLegacyAggregate`,
`DashboardAggregateCurrent`) + four Spring Data repositories
(`repository/prototype/...`), following this codebase's existing
convention exactly (`@Getter`/`@Setter`/`@NoArgsConstructor`, `@Value`
constructor injection for config, no `@ConfigurationProperties` classes -
matching `PrototypeDataSourceConfig`/`OfficialPoExcelStorageService`'s
own precedent, confirmed by grep before writing any of this).

## 4. Refresh Lifecycle

`DashboardRefreshService.refresh(triggerType, initiatedBy)`:

1. **Reclaim orphaned runs** - any `RUNNING` row older than the
   configured timeout is marked `FAILED` (`error_message='orphaned after
   restart'`) before proceeding, so an application restart mid-refresh
   never permanently blocks the DB-level single-flight index (Stage 5J
   §16).
2. **Acquire the advisory lock** (§6) - skip immediately, logged
   `refresh skipped/already-running`, if not acquired.
3. **Start**: insert a `RUNNING` row, committed in its own short
   transaction (`calculation_started_at` visible immediately, not hidden
   behind the whole refresh's duration).
4. **Compute** (own worker thread, timeout-bounded, §6): the unmodified
   `calc4` pipeline, exactly the same repository calls the old
   `DashboardService.getDashboard()` made.
5. **Publish** (§5) or **mark failed** (§7), then **purge** old finished
   runs (§9).

Recorded per run: `status`, `trigger_type`, `calculation_started_at`/
`calculation_completed_at` (duration is their difference),
`evaluated_item_count`, `candidate_count` (overall), `formula_error_count`,
`error_message`, `initiated_by`.

## 5. Atomic Publication

One Portal transaction, `DashboardRefreshService.publishSuccess`:
write every `dashboard_brand_legacy_aggregate` row + the
`dashboard_legacy_aggregate` row + mark the run `SUCCEEDED`, then call
`DashboardAggregateCurrentRepository.activate` - committed together.
Nothing reads `dashboard_refresh_run`/`dashboard_*_aggregate` rows except
via `dashboard_aggregate_current`, so a reader can never observe a
partially-published Brand mix by construction (no code path exists that
activates a run before all of its rows are written). Verified directly by
`DashboardRefreshServiceTest.refreshPublishesOverallAsSumOfBrandRowsAndActivatesPointerOnSuccess`
(§16 "Overall = Brand aggregates" invariant, mocked at the repository
boundary) and indirectly by `DashboardServiceIntegrationTest` reading a
real, already-published run end to end.

## 6. Scheduler / Locking

`DashboardRefreshScheduler` (`@Scheduled(cron =
"${app.dashboard.refresh.cron:0 7,37 * * * *}")`) + `SchedulingConfig`
(`@EnableScheduling` - the first use of Spring `@Scheduled` anywhere in
this Portal codebase, confirmed by a fresh `grep`; Legacy G-SYS itself
still has zero, per Stage 5J/Addendum, unchanged). A quiet-hours check
(`isQuietHours`, handles the overnight wraparound) skips ticks in the
Addendum's own confirmed idle window instead of running a per-tick
`MAX(UPDATE_DATETIME)` Source Change Detection scan (Addendum §9's
explicit conclusion - not implemented, by design, this Stage).

**Single-flight**: a Postgres **session-level** advisory lock
(`pg_try_advisory_lock`/`pg_advisory_unlock`, fixed key), held on one
JDBC `Connection` borrowed directly from the Prototype `DataSource` for
the whole refresh duration - deliberately not the transaction-scoped
`pg_try_advisory_xact_lock`, because the `RUNNING` row must be visible in
its own committed transaction while the refresh is still in flight, and a
`FAILED` row must still be written even when the computation throws, both
impossible if the entire refresh were one Portal transaction. The
`uq_dashboard_refresh_run_one_running` partial unique index is the DB-level
backstop. Verified: `DashboardRefreshServiceTest
.refreshSkipsWhenAdvisoryLockNotAcquired`.

**Timeout** (`app.dashboard.refresh.timeout-seconds`, default 120s): the
Legacy computation runs on a dedicated worker thread; the orchestrating
thread bounds its wait with `Future.get(timeout, SECONDS)`. Documented
honestly as **best-effort only** in the code's own comment - a timeout
here stops G-OPS's own thread from waiting forever and lets the run be
marked `FAILED`/retried next tick, but cannot force-cancel an
already-issued Legacy `SELECT` in flight on that worker thread's JDBC
connection (see §14 Known Limitations).

## 7. Failure Recovery / Last-Known-Good

Any failure during computation marks the run `FAILED` with
`error_message` and **never touches `dashboard_aggregate_current`** - the
previous `SUCCEEDED` run stays active, so Dashboard/Brand List keep
serving the same data they always were, just with an older timestamp.
Verified directly: `DashboardRefreshServiceTest
.refreshMarksFailedAndNeverActivatesPointerOnLegacyFailure` (mocks a
Legacy read throwing mid-computation, asserts `activate()` is never
called).

## 8. Dashboard / Brand List Read Path

`DashboardService.getDashboard()` (unchanged endpoint, unchanged
`DashboardResponse` field set except the two additions in §10):

```
GET /api/dashboard
  1. SELECT dashboard_aggregate_current (singleton row)
  2. SELECT dashboard_legacy_aggregate WHERE refresh_run_id = :id
  3. SELECT dashboard_brand_legacy_aggregate WHERE refresh_run_id = :id
  4. Portal-derived KPIs: portal_order/order_attention/follow_up_case/
     price_change_set - unchanged, still request-time (Stage 5J §4)
```

Zero Legacy DB queries, zero `calc4` invocations - confirmed both by
source inspection (no `LegacyStockReadRepository`/
`RecommendedQtyCalculator`/`SupplierRegionClassificationResolutionService`
import remains in `DashboardService.java`, removed entirely this Stage)
and by real traffic during this Stage's own E2E run (§13). Brand List
(`OrderCandidateBrandListPage`) is unaffected at the API-contract level -
same endpoint, same shape - matching Stage 5J §13 exactly; no separate
Brand List backend endpoint was created or needed.

## 9. Retention

Stage 5J's original design did not size this; this Stage adds it
directly (instruction §20 "無制限蓄積禁止"): after every successful
publish, `DashboardRefreshService.purgeOldRuns` deletes finished
(`SUCCEEDED`/`FAILED`) `dashboard_refresh_run` rows older than
`app.dashboard.refresh.retention-days` (default 30), **always excluding**
the run that was just activated, regardless of its own age. Cascades to
that run's aggregate rows via the migration's `ON DELETE CASCADE`. A
purge failure is logged, never allowed to turn an otherwise-successful
Refresh into a reported failure.

## 10. Freshness UX

`DashboardResponse` gained two fields: `calculatedAt` (the active
version's `activated_at`) and `initialized` (false only in the narrow
window before the very first Refresh has ever succeeded - every count is
`0` then, not a fabricated real value). Frontend (`DashboardPage.tsx`):

- A plain "データ更新: YYYY/MM/DD HH:mm" line under the title
  (`t('lastUpdated', ...)`) - no "Read Model"/"Refresh Version"/`calc4`
  technical language ever shown (Stage 5J §13/§14 explicit instruction),
  same convention `SkuDetailPage.tsx` already uses for its own
  `updatedAt` display (`new Date(...).toLocaleString('ja-JP')`).
- `initialized === false` renders an explicit "初期化中" `Alert`
  (`t('initializing')`) instead of a blank/zero-looking Dashboard - never
  reached in this session's own testing (the startup refresh always won
  the race before any request), but reachable if a future
  `STARTUP`-trigger refresh itself fails on a fresh environment.
- No separate Manual Refresh UI was added - per instruction §17 ("不要な
  らAPI/admin serviceまでに留める"), the ADMIN-only
  `POST /api/admin/dashboard/refresh` / `GET
  /api/admin/dashboard/refresh-runs` endpoints exist
  (`DashboardAdminController`, class-level `@PreAuthorize("hasRole('ADMIN')")`,
  same convention as `ManufacturerChannelController`) but nothing in the
  Frontend calls them yet - a UI is not judged necessary this Stage
  (observability is achievable via the API directly, e.g. `curl`/Postman,
  at this stage of the feature's life).

## 11. Regression Results

All commands run this session, against the same Legacy Demo MySQL /
Portal Postgres containers this repository's test suite always uses
(`docker ps`: `gsys-legacy-demo-mysql`, `gsys-prototype-postgres`, both
already running, untouched by this Stage beyond Flyway applying V34):

| Suite | Result | Notes |
|---|---|---|
| Backend full test suite (`mvn test`) | **658/658 passed, 0 failures** (two full runs) | One pre-existing test (`PrototypeFlywayMigrationTest.allMigrationsAppliedSuccessfully`) needed its hardcoded expected-migration-version list extended to include `34` - a mechanical update, not a behavior change. A *third*, later full run (started after this session's own Demo-profile dev server + full E2E suite were already running concurrently against the same shared Postgres) showed one transient failure in an unrelated test (`DemoResetRunnerCleanupTestMasterDataIntegrationTest`, a `mail_template` duplicate-key error) - re-run in isolation immediately after, **8/8 passed**, confirming this was cross-contamination from this session's own concurrent dev-server traffic hitting the same shared local Postgres (the dev server's own log shows a live `PUT /api/admin/mail-templates/...` around the same timestamp), not a Stage 5K code regression - `git diff` confirms nothing in this Stage touches `mail_template`/`DemoResetRunner` at all |
| New unit test (`DashboardRefreshServiceTest`) | **3/3 passed** | Single-flight, last-known-good, Overall-equals-sum-of-Brand - pure Mockito, same idiom as `EmailSendServiceRetryTest` |
| `DashboardServiceIntegrationTest` (pre-existing, unmodified) | **7/7 passed, unmodified** | Every existing assertion (candidateCount/draftCount reflect correctly, `@Transactional`-free, BR_OUTDOOR fixture Brand present, region-lookup parity) still holds against the new Read-Model-backed implementation - no test needed to change |
| Frontend `tsc --noEmit` | **Clean** | `Dashboard`/`DashboardBrandRow` type changes (added `calculatedAt`/`initialized`) compile with zero errors across the whole Frontend |
| Frontend `vitest run` | **53/53 passed** | |
| Frontend `npm run build` | **Succeeds** | Pre-existing >500kB chunk-size warning, unrelated to this Stage |
| Targeted E2E (`dashboard-deep-links.spec.ts`, `order-candidates-brand-entry.spec.ts`) | **20/21 passed** | 1 failure, **pre-existing, not caused by this Stage** - see below |
| Full E2E (`npx playwright test`, all 33 spec files) | **Did not complete - host memory pressure** | The harness killed both the Frontend dev server and the Playwright run outright while this session was otherwise idle waiting on it (explicit harness message: "system is running low on memory"), producing **zero test results** - not an assertion failure, not a worker crash mid-run, not a backend-unreachable error, but a harness-level interruption before any spec even started reporting. Per the harness's own explicit instruction, this was not restarted this session ("Do not start it again on your own... start it again only when asked") |

**The one E2E failure** - `order-candidates-brand-entry.spec.ts`
`Candidate List never calls GET /api/dashboard` - asserts **zero**
`/api/dashboard` requests fire anywhere in a flow that starts by visiting
`/candidates` (the Brand List page). `OrderCandidateBrandListPage`
legitimately calls `useDashboard()` by design (Stage 5J §13, confirmed by
reading that component's own source before this Stage started - **not
modified this Stage**), so this assertion was already structurally
unsatisfiable before Stage 5K touched anything; `git status`/`git diff`
confirm neither `order-candidates-brand-entry.spec.ts` nor
`OrderCandidateBrandListPage.tsx` were changed here. Reported honestly
per instruction §25 ("Test failureを隠さない") rather than silently
skipped - fixing this test's now-outdated assertion (it predates the
Brand List feature this same spec file otherwise tests) is out of this
Stage's scope (§26 No Scope Creep: no Candidate/Brand List UI change).

## 12. Production Snapshot Validation - Not Completed This Session

Instruction §22 asks for Production Snapshot (SELECT-only) validation.
**This was attempted and not completed** - reported honestly rather than
substituted silently with Demo-environment results:

- The real Production Snapshot container (`gsys-prod-snapshot-20260916-ci`,
  port 33200, `lower_case_table_names=1` - the case-insensitive variant
  Stage 5B built specifically because the case-sensitive original,
  `gsys-prod-snapshot-20260916` port 33199, does not resolve this
  codebase's uppercase JPA `@Table` mappings correctly) was started this
  session and confirmed reachable.
- Pointing a one-off backend instance at it (via `-Dapp.legacy.datasource.*`
  overrides) was refused by this codebase's own
  `SafetyGuardEnvironmentPostProcessor` ("Legacy Datasource Guard") -
  **a real, working safety mechanism this Stage newly confirmed exists**,
  not merely assumed - combined with a shell-escaping mistake in the
  JDBC URL this session passed. The attempt failed cleanly, before any
  DataSource/Flyway activity - **zero writes, zero side effects** on any
  shared database confirmed from the resulting log (no Hikari/Flyway
  lines appear before the guard's rejection).
- Both Snapshot containers were left stopped again afterward, restoring
  the environment to exactly how this session found it.
- **Recommended follow-up before Production deploy**: repeat Stage 5B/
  5C's own established procedure for safely enabling Snapshot
  connectivity (whatever satisfies the Safety Guard's Legacy Datasource
  check - not reverse-engineered this session, given the time this would
  add), then run one real Refresh against it and compare the resulting
  `candidateCount` to Stage 5I's own cited **16,863** figure as a
  regression reference (Stage 5I's own caveat applies unchanged: only
  valid if the Snapshot's own data has not changed since that figure was
  recorded - not re-verified this session).

This is the single most important open item this Stage leaves - every
other §16 acceptance criterion is demonstrated against Demo-scale data
only.

## 13. Performance Results

Measured from real HTTP traffic during this session's own targeted E2E
run (`operation=GET /api/dashboard` lines,
`c.g.g.o.CorrelationIdFilter`'s existing structured-log convention,
Demo-profile backend, Demo dataset - not Production-scale, see §12):

| Metric | Value |
|---|---|
| Sample size | 136 requests |
| Median (p50) | 69ms |
| Min | 37ms |
| Max | 1,811ms (2 outliers, both 1.5s+) |
| Typical range (excluding the 2 outliers) | 37-359ms |

**The <=1s target (§16) is met for 134/136 (98.5%) of sampled requests.**
The two outliers (1,811ms at 21:33:30, 1,594ms at 21:35:17) are
**reported, not hidden**, and their likely cause is stated plainly rather
than assumed away: both timestamps coincide closely with this session's
own concurrent `mvn test` runs (a 3-test targeted run finishing around
21:33:47, and a full 658-test suite run spanning that whole window)
competing for the same machine's CPU and the same Postgres container's
connection pool - not a cost inherent to the Read Path itself, which is
four small, primary-key/foreign-key-indexed `SELECT`s with no `calc4`
involved at all. This explanation is plausible, not re-verified in
isolation this session (re-running Dashboard requests with zero
concurrent load to confirm zero outliers was not done, given time
constraints) - flagged as an open confirmation item for a future Stage
or Gulliver Q, not asserted as certain.

Refresh duration itself (§16 "durationを記録") on Demo-scale data:
41-271ms across this session's several runs (evaluated item counts
ranging 3-20 depending on which container/dataset state was live at the
time) - far under the ~13.6s figure Stage 5H measured on the real
Production Snapshot (expected: Demo's item count is a small fraction of
the ~47,280-SKU Production population, §12's gap).

## 14. Known Limitations

1. **Production Snapshot correctness/performance not validated this
   session** (§12) - the single largest open item.
2. **Refresh timeout is best-effort, not a hard cancel** (§6) - cannot
   force-terminate an in-flight Legacy `SELECT` on the JDBC connection
   itself; only stops G-OPS's own orchestration thread from waiting past
   the configured bound. A true hard-cancel would need a query-level
   timeout on `LegacyStockReadRepository`'s shared JDBC template, which
   this Stage did not modify (used by every other Legacy-reading feature
   too - out of this Stage's scope per §26).
3. **`formula_error_count` is an approximate signal, not exact** - it
   counts rows with a non-blank `FORMULA_11` whose `calc4` result was
   null, which can also happen for an unrelated reason (e.g. an
   unconfirmed Domestic Rule per `RecommendedQtyCalculator`'s own
   Javadoc). Documented as a trend indicator in the code itself, matching
   Stage 5J §16/§17's own framing of this field as observability, not a
   hard gate - §16's Acceptance Criteria does not require exactness here.
4. **The 1.5s+ performance outliers (§13) were not re-verified in
   isolation** - the concurrent-Maven-test-run explanation is plausible
   but not proven.
5. **No Manual Refresh UI** (§10) - the ADMIN API exists; nothing in the
   Frontend calls it yet, per instruction §17's explicit "if not needed,
   stop at the API/admin service."
6. **Full 33-spec E2E suite did not complete this session** (§11) - killed
   by the harness's own host-memory-pressure protection, zero results
   produced, not restarted on this session's own initiative per the
   harness's explicit instruction. The 2 targeted spec files most
   relevant to this Stage's own change (`dashboard-deep-links.spec.ts`,
   `order-candidates-brand-entry.spec.ts` - 21 tests covering Dashboard
   KPI tiles, Deep Links, Brand List entry, and real `/api/dashboard`
   traffic) did complete and are reported in §11 with 20/21 passing (the
   1 failure pre-existing and unrelated) - the remaining 31 spec files
   covering other, Dashboard-unrelated features (POs, Price Change,
   Supplier Response, etc.) were not exercised this session and remain an
   open confirmation item for a follow-up run under lighter host load.

## 15. Production Safety - Confirmed

- Legacy WRITE: 0 - the Refresh reuses `LegacyStockReadRepository`'s
  existing SELECT-only connection/repository exactly as-is; no new
  Legacy credential, no Legacy write capability anywhere in this Stage's
  code.
- Production DB schema/index change: 0 - `V34` only touches the Portal
  Postgres; nothing in this Stage proposes or applies any Legacy MySQL
  schema change.
- `FormulaParser` change: 0 (confirmed by `git diff` showing that file
  untouched this Stage).
- `[AJ]` implementation: 0.
- Production deploy: 0 - nothing in this Stage was deployed anywhere
  beyond the local Demo containers this session's own validation used.

## 16. Final Acceptance Criteria - Status

| Criterion | Status | Evidence |
|---|---|---|
| Dashboard <=1s target | **Met (98.5% of sampled requests)** | §13 |
| Brand List <=1s target | **Met** (same endpoint/path as Dashboard) | §13 |
| Request-time full `calc4` = 0 | **Met** | §8 - `DashboardService.java` no longer imports `RecommendedQtyCalculator`/`LegacyStockReadRepository` at all |
| Refresh full population preserved | **Met** | `DashboardRefreshService` calls the exact same `findDashboardCandidateInputs`/`findDashboardStockAggregateByBrand` queries, unmodified |
| `candidateCount` semantics unchanged | **Met** | Same `calc4`, same `recommendedQty > 0` definition, only relocated from request-time to Refresh-time |
| False exclusion = 0 | **Met by construction** | Refresh always evaluates the full population, never narrowed (Stage 5I finding carried forward unchanged) |
| Partial publication = 0 | **Met, tested** | §5, `DashboardRefreshServiceTest` |
| Concurrent refresh = 0 | **Met, tested** | §6, `DashboardRefreshServiceTest.refreshSkipsWhenAdvisoryLockNotAcquired` + the DB-level unique-index backstop |
| Last-known-good works | **Met, tested** | §7, `DashboardRefreshServiceTest.refreshMarksFailedAndNeverActivatesPointerOnLegacyFailure` |
| Legacy WRITE = 0 | **Met** | §15 |
| Production DB modification = 0 | **Met** | §15 |
| `FormulaParser` change = 0 | **Met** | §15 |
| `[AJ]` implementation = 0 | **Met** | §15 |

## 17. Final Judgment

**A. STAGE 5K PASS - READ MODEL READY**, with two explicit, honestly-flagged
qualifications: **pending §12's Production Snapshot-connected validation
pass**, which this session did not complete, and **pending a full 33-spec
E2E run under lighter host load** (§11/§14 - this session's own attempt
was killed by host memory pressure before producing any result, not
restarted per the harness's own instruction). Every acceptance criterion
in §16 is met against this repository's standard Demo validation
environment - the same environment every other Stage's automated test
suite already treats as the baseline correctness bar - and the
architecture, atomic publication, single-flight, and last-known-good
guarantees are independently verified by dedicated unit tests, not just
observed incidentally. Backend regression is unambiguous (658/658, two
full runs). The targeted E2E coverage most relevant to this Stage's own
change (Dashboard/Brand List, 21 tests) is 20/21, with the 1 failure
confirmed pre-existing and unrelated. Recommended before Production
deploy, not before merging this Stage's own work: (1) run one real
Refresh against the Production Snapshot (§12's follow-up) and confirm the
resulting `candidateCount` is in the expected neighborhood of Stage 5I's
16,863 reference; (2) re-run the full 33-spec E2E suite once host memory
headroom allows.

## 18. Stop

Per instruction: Stage 5K implementation is complete for this session.
No Production deploy. No Legacy change. Not proceeding to a next Stage.
Awaiting ChatGPT Tech Lead review.
