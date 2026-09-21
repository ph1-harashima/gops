# G-OPS Stage 5J: Dashboard Read Model & Refresh Architecture Design

DESIGN ONLY. No code, migration, scheduler, Read Model, Dashboard, or Brand
List change was made in this Stage. Baseline: commit `e01e240`. Stage 5I's
Final Judgment (**B. PRECOMPUTED AGGREGATE REQUIRED**) is accepted as the
Tech Lead decision this Stage designs against, without re-litigating it.
Read-only investigation this Stage (Portal table row counts, Legacy
`UPDATE_DATETIME` feasibility) reused the same Production Snapshot
(SELECT-only) and Portal Demo DB already used throughout Stage 5B-5I.

## 1. Executive Summary

This Stage designs a Portal DB Read Model for Dashboard/Brand List's
Legacy-derived KPIs (candidateCount, outOfStockCount,
longTermOutOfStockCount, overall and per-Brand), computed by an
unattended background job that runs the exact same, unmodified `calc4`
pipeline over the exact same 47,280-SKU population Stage 5I confirmed is
Business-correct - no population narrowing, no meaning change. The
Dashboard/Brand List HTTP request path is redesigned to never invoke
`calc4`: it reads a Portal DB row instead.

Portal-derived KPIs (draftCount, awaitingApprovalCount, etc.) are
**deliberately not Read-Model'd** - Portal's own tables are tiny (577
Orders, 38 Follow-Up Cases, 48 Price Change Sets in the Demo DB used for
this sizing check) and every one of these KPIs is a simple current-status
count, so a plain request-time `SELECT ... GROUP BY status` is already
correct after every Portal event with zero extra engineering - no event
matrix, no cache invalidation, nothing to get subtly wrong.

Recommended architecture (detailed in §21): a single Spring Boot
`@Scheduled` background job, a Postgres advisory lock for single-flight,
an append-only `dashboard_refresh_run` history table with a one-row
"current version pointer" table for atomic publication, last-known-good
on any failure, and a configurable refresh interval (explicitly **not**
hardcoded to 15 minutes - Stage 5I already found that figure unconfirmed
in this repository, and this Stage independently confirms it stays
unconfirmed). RC-D (FormulaParser `[AT]`/`[AJ]`) errors are handled by
preserving the existing, already-graceful per-SKU degrade behavior -
nothing new is designed around RC-D, since the current running system
already tolerates per-SKU FormulaParser errors without crashing (confirmed
by this session's own repeated observation of `FormulaParser ERROR` log
lines never taking down a Dashboard/Candidate request).

## 2. Fixed Stage 5I Findings (Not Re-Examined)

- `MS_ITEM` physical rows: 116,842. `calc4`'s actual evaluated population:
  ~47,280 (`DEL_FLG IS NULL OR DEL_FLG = 0`).
- Current `candidateCount`: 16,863.
- Recency-based population restriction (Active Brand/Supplier by recent
  PO) is **not Business-safe** at any window tested - False Exclusion
  23.6% (12mo) up to 76.2% (1mo). **The Refresh job in this design
  computes the full ~47,280-SKU population every time, never a narrowed
  one.**
- `candidateCount`'s Business meaning is not changed by this design - the
  Read Model stores the same value the current live computation would
  produce, just computed on a schedule instead of per-request.

## 3. KPI Split

Reusing Stage 5I §11's Dependency table directly:

| KPI | Source | This Stage's Treatment |
|---|---|---|
| candidateCount (overall + per-Brand) | Legacy, `calc4` | **Read Model** (§5) |
| outOfStockCount (overall + per-Brand) | Legacy, SQL aggregate | **Read Model** (§5) - already cheap alone, but grouped with candidateCount so Dashboard reads one row, not two sources |
| longTermOutOfStockCount (overall + per-Brand) | Legacy, SQL aggregate | **Read Model** (§5) |
| draftCount, awaitingApprovalCount, awaitingSupplierCount, attentionCount | Portal, status count | **Request-time aggregate** (§13) |
| 問い合わせ中 (open Follow-Up Cases) | Portal, status count | **Request-time aggregate** |
| 価格変更（下書き） | Portal, status count | **Request-time aggregate** |

## 4. Read Strategy Comparison

| | Option 1 (Legacy Read Model only) | Option 2 (Both Read-Modeled) | Option 3 (Hybrid) |
|---|---|---|---|
| Legacy-derived | Read Model | Read Model | Read Model |
| Portal-derived | Request-time | Read Model + Portal Event refresh | Request-time |
| New moving parts | 1 (Refresh job) | 2 (Refresh job + Event listeners/invalidation) | 1 (Refresh job) |
| Portal data volume today | 577/38/48 rows | same | same |
| Staleness risk for Portal KPIs | None (always live) | New risk class (event delivery, invalidation bugs) | None (always live) |

**Recommendation: Option 3 (= Option 1 in practice, since Option 3's
Portal half is identical to Option 1's)**. At 577/38/48 rows, a Portal
aggregate query is a full-table scan measured in low single-digit
milliseconds - Read-Modeling it would add an event-driven invalidation
system (§13) to solve a problem that does not exist yet. This is exactly
the over-engineering this Stage's instruction prohibits. Revisit only if
Portal row counts grow into a range where `COUNT(*) ... GROUP BY status`
itself becomes measurably slow (not close today).

## 5. Proposed Read Model (Granularity)

Two granularities, both populated by the same single Refresh Run:

- **A. Overall Aggregate** - one row per refresh (`candidate_count`,
  `out_of_stock_count`, `long_term_out_of_stock_count`).
- **B. Brand Aggregate** - one row per (refresh, Brand code) with the same
  three metrics.

**Overall as independent row vs. SUM of Brand rows**: independent row
recommended. Deriving Overall via `SUM(...) GROUP BY` over ~570-600 Brand
rows on every Dashboard request is trivial in isolation, but storing it
directly means the Dashboard's hot read path is a single-row `SELECT` by
primary key (the cheapest possible query) instead of an aggregate scan,
and it structurally guarantees Overall and the Brand breakdown were
computed by the exact same Refresh Run (no window where one was summed
from a slightly different Brand snapshot than what's displayed
elsewhere). The cost is a handful of extra integers per refresh - trivial
next to the Brand rows already being written.

Dashboard and Brand List never run `calc4` independently and never will
under this design - both read from the same two tables.

## 6. Proposed Schema (DDL Proposal, No Migration Created)

Naming follows the existing convention observed directly in this
repository's migrations (e.g. `V32__sku_expected_restock.sql`): snake_case,
`BIGSERIAL PRIMARY KEY`, `TIMESTAMPTZ NOT NULL DEFAULT now()`, explicit
`VARCHAR` lengths, `ck_`/`uq_`/`idx_` constraint/index prefixes,
non-FK Legacy-sourced codes stored as plain `VARCHAR` (matching
`sku_expected_restock.sku_code`'s own precedent - Brand has no Portal
Master table). Proposed as `V34__dashboard_read_model.sql` (next available
version at this Stage's baseline).

```sql
-- One row per Refresh attempt (history + the source of the "current" pointer).
CREATE TABLE dashboard_refresh_run (
    id                          BIGSERIAL PRIMARY KEY,
    status                      VARCHAR(20)   NOT NULL,   -- RUNNING / SUCCEEDED / FAILED
    trigger_type                VARCHAR(20)   NOT NULL,   -- SCHEDULED / MANUAL / CHANGE_DETECTED
    calculation_started_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    calculation_completed_at    TIMESTAMPTZ,
    source_snapshot_time        TIMESTAMPTZ,              -- MAX(UPDATE_DATETIME) high-water-mark across tracked Legacy tables at start (§9)
    item_count_evaluated        INTEGER,
    candidate_count             INTEGER,                  -- overall, duplicated from dashboard_legacy_aggregate for quick health checks
    formula_error_count         INTEGER       NOT NULL DEFAULT 0,  -- RC-D observability, §16
    error_message                TEXT,
    initiated_by                VARCHAR(50),              -- ADMIN username for MANUAL; NULL otherwise
    CONSTRAINT ck_dashboard_refresh_run_status
        CHECK (status IN ('RUNNING','SUCCEEDED','FAILED')),
    CONSTRAINT ck_dashboard_refresh_run_trigger_type
        CHECK (trigger_type IN ('SCHEDULED','MANUAL','CHANGE_DETECTED'))
);

CREATE INDEX idx_dashboard_refresh_run_status_started
    ON dashboard_refresh_run (status, calculation_started_at);

-- Enforces single-flight at the DB level too (defense in depth alongside
-- the advisory lock, §12): at most one RUNNING row ever.
CREATE UNIQUE INDEX uq_dashboard_refresh_run_one_running
    ON dashboard_refresh_run ((status))
    WHERE status = 'RUNNING';

-- Overall Aggregate - one row per SUCCEEDED refresh (§5).
CREATE TABLE dashboard_legacy_aggregate (
    refresh_run_id              BIGINT PRIMARY KEY
        REFERENCES dashboard_refresh_run (id) ON DELETE CASCADE,
    candidate_count              INTEGER NOT NULL,
    out_of_stock_count           INTEGER NOT NULL,
    long_term_out_of_stock_count INTEGER NOT NULL
);

-- Brand Aggregate - one row per (refresh, Brand) per SUCCEEDED refresh.
CREATE TABLE dashboard_brand_legacy_aggregate (
    refresh_run_id                BIGINT NOT NULL
        REFERENCES dashboard_refresh_run (id) ON DELETE CASCADE,
    brand_code                    VARCHAR(10) NOT NULL,  -- Legacy-sourced, not a Portal FK (no Brand master table)
    candidate_count                INTEGER NOT NULL,
    out_of_stock_count             INTEGER NOT NULL,
    long_term_out_of_stock_count   INTEGER NOT NULL,
    PRIMARY KEY (refresh_run_id, brand_code)
);

-- Atomic "current version" pointer - single row, updated with one UPDATE
-- statement (§7). Never written by anything except a successful refresh's
-- final activation step.
CREATE TABLE dashboard_aggregate_current (
    singleton                   BOOLEAN NOT NULL PRIMARY KEY DEFAULT true,
    active_refresh_run_id       BIGINT NOT NULL
        REFERENCES dashboard_refresh_run (id),
    activated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_dashboard_aggregate_current_singleton CHECK (singleton)
);
```

`dashboard_aggregate_current` starts empty until the first successful
refresh (§16 Startup/Empty State) - `INSERT ... ON CONFLICT (singleton) DO
UPDATE` is the activation statement, a single atomic write.

## 7. Atomic Publication Design

**Recommendation: refresh_version pattern (Option B) realized via the
schema in §6** - not a staging-table-then-swap (Option A, which would
need a second full copy of the aggregate tables plus a rename/swap step)
and not a per-row "current" boolean flag (Option C, which risks exactly
the "Brand A is new, Brand B is old" partial-read window the instruction
explicitly forbids, since flipping ~570 individual flags is not atomic).

Sequence:
1. Insert a `dashboard_refresh_run` row with `status='RUNNING'` (this is
   also the single-flight lock's DB-level backstop, §12).
2. Run `calc4` over the full population; insert the Overall row and all
   Brand rows, tagged with this `refresh_run_id`. These are **not yet
   visible to any Dashboard read** - nothing reads `dashboard_refresh_run`
   rows directly except the pointer table.
3. On success: update `dashboard_refresh_run.status='SUCCEEDED'` and
   `calculation_completed_at`, then `UPSERT` `dashboard_aggregate_current`
   to point at this run - one statement, one transaction. Dashboard reads
   flip to the new data atomically at this instant.
4. On any failure: update `status='FAILED'` with `error_message`; **never
   touch `dashboard_aggregate_current`** - the previous successful run
   stays active untouched. This single rule satisfies §7's requirement by
   construction (no code path exists that could publish a partial result).

## 8. Refresh Trigger Analysis

| Option | Fit |
|---|---|
| A. Fixed Schedule | Primary mechanism - see §9 for why the interval is a config value, not a fixed literal |
| B. Legacy Batch-aligned | Not usable today - Legacy has zero `@Scheduled`/cron-equivalent annotations anywhere in Source (Stage 5I §12, re-confirmed); "batch completion" has no observable signal from a READ ONLY connection |
| C. Source Change Detection | Usable as a **pre-check gate**, not a replacement for A (§9/§10) |
| D. Portal Event | N/A - Portal events never affect Legacy-derived KPIs |
| E. Manual Refresh | Included, ADMIN-only (§17) |
| F. Hybrid | **Recommended**: A (fixed schedule, safety net) + C (skip a tick if nothing changed) + E (manual override) |

## 9. Source Change Detection

**Do not assume 15 minutes.** Stage 5I already found "15分周期Import"
unconfirmed in this repository; this Stage's own re-check (grep across
all of `docs/`) found no additional source for it either. The Refresh
interval in this design is a Spring configuration property with **no
committed default derived from that figure** - a conservative starting
value is proposed in §21, explicitly labeled as a placeholder pending
§20 Q-A/Q-C.

**Feasibility, measured directly against the Snapshot**: every table this
Stage's KPIs depend on has an `UPDATE_DATETIME` column with a real,
recent value:

| Table | Rows | MAX(UPDATE_DATETIME) | Indexed? |
|---|---|---|---|
| `ms_stk` | 1,518,842 | 2026-09-16 16:40:39 | No |
| `ms_item` | 116,842 | 2026-09-16 21:21:01 | No |
| `ms_comm` | 3,825 | 2026-09-16 11:34:46 | No |
| `ms_formula` | 98,751 | 2026-09-16 15:51:19 | No |
| `tr_po` | 8,376 | 2026-09-16 16:40:38 | No |
| `tr_po_dtl` | 183,480 | 2026-09-16 16:40:38 | No |
| `tr_arr` | 17,722 | 2026-09-16 15:46:16 | No |

**Cost, measured**: `EXPLAIN ANALYZE SELECT MAX(update_datetime) FROM
ms_stk` (the largest table) took **~1.26s** (a full table scan - no
index exists on `UPDATE_DATETIME`, and this Stage does not propose adding
one, consistent with the "no Production DB index" constraint every prior
Stage has honored). The other 6 tables are 8x-400x smaller and each cost
well under a second individually. **Combined estimated cost: ~1.5-2.5s**
for a full 7-table change check - genuinely cheaper than the ~13.6s full
recompute (roughly 5-9x), but **not free**, and not something to run on
every Dashboard request either (it would still be slower than the ~1s
target). Its correct role is as a pre-check inside the scheduled job, not
a replacement for scheduling, and not a per-request check.

**Open question this data cannot answer**: whether `UPDATE_DATETIME`
reflects genuine content changes (a row's value actually changed) or
merely "a batch touched this row" (e.g. an UPDATE that rewrites the same
value, which some batch frameworks do unconditionally). If it is the
latter, Source Change Detection degrades to "did any batch run," which is
still useful (skips ticks between batch runs) but less precise than "did
anything I actually care about change." **This cannot be determined from
data alone - it requires either Legacy Source code review (out of this
Stage's scope) or an Ernest/Gulliver answer** (§20 Q-A covers the
adjacent batch-cadence question; this is a related but distinct
sub-question worth folding into the same conversation).

**Conclusion**: full, precise per-KPI Change Detection is not confirmable
from data alone today. Per this Stage's own instruction (§10's "もし完全
なChange Detectionが不可能なら、Scheduled Full Refreshを安全なFallback
として設計する"), the design treats Change Detection as a **secondary,
opportunistic optimization** layered on top of a **mandatory scheduled
baseline**, never as a replacement for it.

## 10. Production Load Analysis

The EC2 sizing figures cited in this Stage's own instruction (`c5d.2xlarge`,
8 vCPU / 16 GiB, 40-55% CPU / 60-65% RAM under normal Batch+Stocklist) do
not appear in any document in this repository (searched, no match) -
they are used here as Operational context provided directly in this
Stage's instruction, not as an independently-verified repository Source,
noted for the same transparency reason Stage 5I applied to the 9/17
figures.

Taking those figures at face value: a background job adding one more
Legacy read-connection query burst (~13.6s of `calc4` evaluation reading
`ms_item`/`ms_stk`/`ms_formula`/`tr_po`/`tr_po_dtl`, all SELECT-only, no
writes) at a 15/30/60-minute cadence is a small, bounded, predictable
addition next to whatever the 40-55%/60-65% baseline already reflects -
but **"13.6 seconds is small" is not, by itself, a safe conclusion**,
per this Stage's own explicit instruction not to assume that. Two things
this data genuinely cannot answer: (1) whether that 40-55%/60-65%
baseline was measured *during* Heavy Batch/Stocklist peaks or only under
normal operation (the instruction itself says Heavy Batch Peak is
unconfirmed), and (2) whether the Refresh job's Legacy queries would
serialize behind or interleave with a concurrent Heavy Batch in a way
that matters. **This is §20 Q-C** - a Gulliver/Ernest question, not
something resolvable from the Snapshot alone (the Snapshot has no load/
concurrency information, only data). Until answered, the design defaults
to the most conservative posture available without more information:
configurable interval (not aggressive by default), a hard timeout (§12),
and no retry-storm risk (a failed tick simply waits for the next
scheduled tick, §12).

## 11. Portal Event Matrix

| Event | KPI(s) Affected | Mechanism |
|---|---|---|
| Draft created | draftCount +1 | Row's `status` becomes `DRAFT` |
| Submitted for approval | draftCount -1, awaitingApprovalCount +1 | `status` → `PENDING_APPROVAL` |
| Approved | awaitingApprovalCount -1, awaitingSupplierCount +1 | `status` → `AWAITING_SUPPLIER` (or terminal, depending on workflow stage) |
| Approved with changes / Returned for correction | awaitingApprovalCount -1, draftCount +1 (if returned) | `status` transition |
| Supplier response received | may set attentionCount +1 if quantity differs | `status`/attention flag change |
| Attention created | attentionCount +1 | flag set |
| Attention acknowledged/resolved | attentionCount -1 | flag cleared |
| Cancelled | removes the row from whichever active-status count it was in | `status` → `CANCELLED` |
| Follow-up opened | 問い合わせ中 +1 | `follow_up_case.status` = open |
| Follow-up closed | 問い合わせ中 -1 | `follow_up_case.status` = closed |
| Price Change draft created | 価格変更（下書き） +1 | `price_change_set.status` = `DRAFT` |
| Price Change completed/cancelled | 価格変更（下書き） -1 | `status` transition |

**Key structural observation driving §4's recommendation**: every one of
these KPIs is a **current-status count**, not a cumulative/historical
metric. A plain `SELECT status, COUNT(*) FROM portal_order GROUP BY
status` (and the equivalent for `follow_up_case`/`price_change_set`) is
**automatically correct immediately after any event in this table**,
because it re-reads the current state rather than tracking deltas. There
is no event-handling code to write, no invalidation to get wrong, and no
staleness window - request-time aggregate is not just simpler than
event-driven Read Model here, it is strictly more correct with less code.

## 12. Dashboard Read Path

```
GET /api/dashboard
  ↓
1. SELECT active_refresh_run_id FROM dashboard_aggregate_current
2. SELECT candidate_count, out_of_stock_count, long_term_out_of_stock_count
     FROM dashboard_legacy_aggregate WHERE refresh_run_id = :id
3. SELECT brand_code, candidate_count, out_of_stock_count, long_term_out_of_stock_count
     FROM dashboard_brand_legacy_aggregate WHERE refresh_run_id = :id
4. SELECT status, COUNT(*) FROM portal_order GROUP BY status   -- Portal-derived, request-time
   (+ follow_up_case, price_change_set equivalents)
5. Merge (2)+(3)+(4) into DashboardResponse, same shape as today
```

Zero Legacy DB queries. Zero `calc4`/`FormulaParser` invocations. All
four SELECTs are small, indexed, Portal-DB-only reads - this is the
`@Transactional`-free, short-transaction shape RC-F/RC-G already
established, just with the expensive Legacy work removed entirely rather
than merely un-held.

## 13. Brand List Read Path

Identical to Dashboard's (§12) - `OrderCandidateBrandListPage`'s
`useDashboard()` call is unaffected at the Frontend/API-contract level
(same endpoint, same shape); what changes is only what `GET
/api/dashboard` does internally. `GET /api/brands` (RC-B's lightweight
Brand-name lookup) is unrelated to this Read Model and is already
`calc4`-free - unaffected by this design.

## 14. Staleness UX

- **Normal**: "最終更新 HH:MM" sourced from
  `dashboard_aggregate_current.activated_at` (equivalently, the active
  run's `calculation_completed_at`).
- **Refresh in progress**: continue showing the last activated data
  unchanged (§7 guarantees this is always well-defined) with a small,
  non-blocking "更新中" indicator - never a loading spinner over live data.
- **Refresh failed**: continue showing the last activated data with its
  own (older) timestamp; no user-facing error. An ADMIN-only Warning
  (§17/§18) may additionally surface the failure, but a general Operator
  sees only a normal Dashboard with a timestamp - per this Stage's own
  instruction not to over-expose technical errors to general users.
- **Stale threshold proposal**: flag "may be outdated" once the active
  timestamp exceeds **3x the configured refresh interval** (e.g. if the
  interval is 15 minutes, warn past 45 minutes with no successful
  refresh) - a relative, not absolute, threshold, since the interval
  itself is not yet fixed (§9). This is a proposal, not a Business Rule
  confirmation - cheap to tune once §20 Q-A/Q-B are answered.

## 15. Startup / Empty State

**Recommended: (A) Initial refresh completes before Dashboard is
usable, with an explicit one-time bootstrap step, not (B) skeleton+
calculating shown to every early user or (C) purely-manual initial
refresh.** Concretely: the application itself triggers one `MANUAL`-type
refresh on startup if `dashboard_aggregate_current` has no row yet (a
one-time, idempotent check - if a row already exists, startup does
nothing extra). Until that first refresh succeeds, `GET /api/dashboard`
returns an explicit "not yet initialized" response (not a fabricated
zero-candidateCount, which would be actively misleading) and the
Frontend shows a clear "初期化中" state rather than a blank/zero
Dashboard. This only ever matters once per environment (first deploy of
this feature) - not a recurring operational concern.

## 16. Failure / Recovery

| Failure | Design Response |
|---|---|
| Refresh failure (any cause) | `dashboard_refresh_run.status='FAILED'`, `dashboard_aggregate_current` untouched - last-known-good keeps serving (§7) |
| Legacy DB unreachable | Same as above; timeout (§12) bounds how long the attempt blocks the single-flight lock |
| FormulaParser error (RC-D) on one SKU | **No change to existing behavior** - the current, unmodified `calc4`/`RecommendedQtyCalculator` path already logs and continues past a single SKU's formula error (confirmed repeatedly in this session's own backend logs); the Refresh job inherits this as-is. `formula_error_count` on the `dashboard_refresh_run` row counts how many SKUs hit this during that run, for observability (§18) - it does not change what gets counted as a candidate, and it does not fail the run |
| Partial Brand failure (e.g. one Brand's aggregation throws for an unrelated reason) | Treated as a whole-run failure, not a partial-success publication - §7's atomicity guarantee is unconditional; a "publish everything except the broken Brand" mode is explicitly not designed here, since that would itself be a silent, undocumented change to what a user sees (closer to the "false exclusion" risk Stage 5I warned about than to a safe degradation) |
| Timeout | Run marked `FAILED` at the configured max execution time (§12); lock released |
| Application restart during refresh | The Postgres advisory session lock (§12) is tied to the DB connection/session and is released automatically when that connection drops - no manual cleanup needed. The orphaned `RUNNING` `dashboard_refresh_run` row is left as `RUNNING` in the DB; the next scheduled tick's single-flight check should treat a `RUNNING` row older than the timeout threshold as abandoned and safe to supersede (mark it `FAILED` with `error_message='orphaned after restart'` before proceeding) |
| Portal DB write failure (during publication) | The three-table write in §7 step 2-3 happens inside one transaction; a write failure there rolls back entirely, leaving the run as `RUNNING` until the same restart-recovery handling above reclaims it - `dashboard_aggregate_current` is never at risk since it is only ever touched by a already-committed, already-successful run |

## 17. RC-D Interaction

FormulaParser is not modified in this Stage or proposed for modification
by this design. As detailed in §16, the Refresh job's only RC-D-related
addition is **observability** (`formula_error_count` per run) - it makes
the existing, already-tolerated per-SKU error rate visible over time
(e.g. "is this getting worse") without changing what happens when an
error occurs. If Gulliver later resolves RC-D (confirms `[AT]` vs `[AJ]`),
no Read Model schema change is needed - the count will simply drop.

## 18. Security

- The Refresh job uses the existing Legacy SELECT-only connection/user
  exactly as-is - no new Legacy credential, no Legacy write capability
  anywhere in this design.
- Manual Refresh (§8 Option E) is `ADMIN`-only, mirroring every other
  admin-gated endpoint in this codebase (`@PreAuthorize("hasRole('ADMIN')")`,
  the same convention `SupplierMasterController` already uses).
- No new endpoint is proposed that exposes refresh internals (error
  messages, timing) to non-ADMIN users - §14 already specifies that
  general Dashboard users see only a timestamp, never a failure detail.

## 19. Observability

Logged per refresh (matching this project's existing structured-log
convention, e.g. `CorrelationIdFilter`'s own `operation=... result=...
durationMs=...` pattern already used throughout the codebase):
`refresh start`, `refresh success` (with duration, evaluated SKU count,
candidate count), `refresh failure` (with cause), `formula_error_count`.
`dashboard_refresh_run`'s own rows already persist `last success`/`last
failure` queryable state - no separate Health/Admin table is needed; an
ADMIN-only read endpoint (or reuse of an existing admin screen) can
simply query `dashboard_refresh_run ORDER BY calculation_started_at DESC
LIMIT N`. External monitoring integration is explicitly out of this
Stage's scope, per instruction.

## 20. Acceptance Criteria (For Stage 5K)

Restating this Stage's own §25 list as the concrete Stage 5K gate,
unchanged:

- Dashboard initial response: <= 1s target.
- Brand List: <= 1s target.
- Dashboard request: 0 full `calc4` invocations.
- Brand List request: 0 full `calc4` invocations.
- Refresh: full 47,280-SKU Business semantics preserved (no population
  narrowing).
- `candidateCount`: current semantics preserved exactly.
- False exclusion: 0 (by construction - the Refresh always evaluates the
  full population, never a recency-narrowed one).
- Partial aggregate publication: 0 (guaranteed by §7's design).
- Concurrent refresh: 0 (guaranteed by §12's single-flight lock + DB
  unique-index backstop).
- Legacy WRITE: 0.

## 21. Gulliver / Business Questions

Re-evaluated against this Stage's own instruction to keep only what the
architecture decision genuinely needs:

**Blocking / architecture-relevant**:
- **Q-A**: What is the actual operational update cadence of the Legacy
  Import/Batch pipeline (Tempostar, Official PO, Arrival, Stocklist)?
  Directly sizes the Refresh interval (§9) - currently unconfirmed from
  Source.
- **Q-B**: How many minutes of staleness in Dashboard's 発注候補 count is
  operationally acceptable? Directly sets both the Refresh interval
  default and the Stale Warning threshold (§14).
- **Q-C**: What time-of-day does Heavy Stocklist/Batch processing run
  (if known operationally, even if not in Source)? Needed to decide
  whether the Refresh schedule should avoid a window (§10/§12).
- **Q-D**: RC-D - is `[AT]` or `[AJ]` (or both) the correct
  `FORMULA_11` multiplier pattern in real Production? Unrelated to this
  Stage's architecture (the Read Model tolerates the error either way,
  §16/§17) but remains the one open Business-logic question a future
  Stage would need before touching `FormulaParser` itself.

**Explicitly demoted to reference-only, not blocking this design**
(per this Stage's own instruction): "月150回とは何か" and "約200 Brand
とは何か" (Stage 5I §17 Q1/Q2/Q4/Q5) - none of this Stage's architecture
decisions (Read Model shape, publication strategy, trigger design, Portal
KPI treatment) depend on resolving those figures. They remain open,
useful context for a future Active Brand *display* feature (Stage 5I
§15), not for this Refresh design.

## 22. Recommended Architecture

**Legacy-derived**: scheduled full-population `calc4` Refresh via a
single Spring Boot `@Scheduled` job, interval **configurable** (property
`gops.dashboard.refresh-interval`, no committed default pending Q-A/Q-B -
a conservative starting value such as 15 minutes may be used operationally
while awaiting that answer, but is not asserted as correct here), gated by
a Postgres advisory lock for single-flight, with an opportunistic
Source Change Detection pre-check (§9) to skip a tick when the ~1.5-2.5s
`MAX(UPDATE_DATETIME)` sweep across the 7 tracked tables finds nothing
newer than the last successful run's `source_snapshot_time`.

**Portal-derived**: request-time lightweight SQL aggregate, no Read
Model, no event-driven invalidation - §4/§11's finding that this is both
simpler and strictly more correct at current Portal data volumes.

**Storage**: Portal DB, the three-table `refresh_run` / `legacy_aggregate`
/ `brand_legacy_aggregate` schema in §6, plus a one-row
`aggregate_current` pointer.

**Publication**: atomic version-pointer swap (§7) - the refresh_version
pattern, not staging-table-swap or per-row flags.

**Failure**: last-known-good always (§16) - a failed or hung Refresh
never affects what users see, only how current it is.

**UI**: Last Calculated Timestamp always shown; a relative Stale
Warning past 3x the configured interval (§14); no technical error ever
shown to non-ADMIN users.

**Orchestration**: Spring Boot `@Scheduled` + advisory lock is sufficient
at this scale - a separate worker/queue/AWS service is explicitly judged
over-engineering given the entire Refresh (full 47,280-SKU `calc4`) costs
~13.6s and runs at most every few minutes; nothing here needs independent
scaling, retry queues, or cross-process coordination beyond one lock.

## 23. Stage 5K Implementation Scope Proposal (Not Started This Stage)

- **Migration**: `V34__dashboard_read_model.sql` (§6's DDL, verbatim or
  lightly adjusted after Gulliver review).
- **Entities/Repositories**: `DashboardRefreshRun`, `DashboardLegacyAggregate`,
  `DashboardBrandLegacyAggregate`, `DashboardAggregateCurrent` JPA entities
  + Spring Data repositories (Portal-side, standard pattern already used
  throughout this codebase).
- **Refresh Service**: a new `DashboardRefreshService` reusing the
  existing `DashboardService`'s current Category A/B computation logic
  as-is (RC-A's `findDashboardStockAggregateByBrand`/
  `findDashboardCandidateInputs`/`calc4` pipeline is unchanged, just
  called from a scheduled context and written to the new tables instead
  of returned directly) plus the §7 publication/§12 locking logic.
- **Scheduler**: a `@Scheduled` method wiring `DashboardRefreshService`,
  the advisory-lock acquisition, and the Source Change Detection pre-check.
- **DashboardService change**: `getDashboard()` rewritten to the §12 read
  path (drop the `calc4` call entirely).
- **Brand List**: no change needed beyond what `DashboardService`'s
  change already provides (`OrderCandidateBrandListPage` keeps calling
  the same endpoint).
- **API**: no new public endpoint required for the read path itself; one
  new ADMIN-only Manual Refresh endpoint (§8 Option E, §18).
- **UI**: Last Calculated Timestamp + Stale Warning display (§14) on
  Dashboard and Brand List.
- **Tests minimum**: publication atomicity (a reader mid-refresh never
  sees a mixed Brand-row state), last-known-good on induced failure,
  single-flight (two concurrent triggers only run one refresh), startup
  empty-state behavior, `formula_error_count` observability, Portal KPI
  request-time correctness after each event type in §11's matrix,
  `candidateCount` value parity between the new Read Model output and
  the existing (to-be-removed) live-computation path for a known fixture
  - the strongest possible regression guard that this design changes
  *when* candidateCount is computed, never *what* it means.
- **E2E**: Dashboard/Brand List load-time assertion (<=1s), staleness
  indicator visibility, Manual Refresh (ADMIN-only, 403 for Operator).

## Final Judgment

**B. GULLIVER CONFIRMATION REQUIRED BEFORE IMPLEMENTATION**

The architecture itself (Read Model shape, publication strategy, failure
handling, Portal-KPI treatment, orchestration) is fully specified and
implementable as designed - nothing about it is blocked on unanswered
questions. What is blocked is **sizing** it correctly: the Refresh
interval, the Stale Warning threshold, and whether a batch-avoidance
window is needed all depend on §21's Q-A/Q-B/Q-C, none of which this
Stage could resolve from repository Source or the Snapshot alone (Stage
5I already established Q-A's underlying fact - Legacy batch cadence - as
unconfirmed, and this Stage's own investigation did not change that).
Implementing Stage 5K with a placeholder interval is possible but would
mean shipping a guess on the one parameter most likely to need Gulliver's
input; recommended to confirm Q-A/Q-B (at minimum) before Stage 5K starts.
