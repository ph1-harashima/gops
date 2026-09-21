# G-OPS Stage 5A — Legacy Schema Case Compatibility & Snapshot Safety-Gate Audit

Scope: cross-audit the real Production Snapshot's actual schema against
every table G-OPS's Legacy Adapter references, to determine conclusively
whether Stage 5's `MS_ITEM` case-mismatch finding is isolated or part of a
wider pattern, and to design (not implement) both a schema-compatibility
remediation and a Safety Guard mechanism that could sanction a controlled
Snapshot-validation connection without an Application code change.
**AUDIT / DESIGN ONLY. No Application, Test, Migration, or Legacy file was
changed this Stage** (`git status` confirms `backend/` clean; `phasep-gulliver`
was read-only accessed — `grep`/`cat`/`find` only, no `Edit`/`Write` call
made there).

## 1. Executive Summary

The Snapshot's real schema was cross-checked, table by table, against
every table G-OPS's Legacy Adapter actually queries (13 SQL sources: 8
`resources/legacy/*.sql` files plus 2 inline-SQL Java repository methods,
covering 8 distinct logical tables). **`MS_ITEM` is confirmed the *only*
case mismatch** — all 7 other G-OPS-referenced tables match case exactly
across G-OPS's SQL, Legacy Demo, and the real Snapshot. Checking the raw
`mysqldump` file directly (not just the restored container) confirms
`MS_ITEM`'s uppercase spelling is genuine, official Production naming, not
a Snapshot-restore artifact. Checking Legacy G-SYS's own JPA source found
something broader and more important than Stage 5 knew: **every single
Legacy entity** (`MsItem`, `MsStk`, `MsComm`, `TrPo`, `TrPoDtl`, `TrArr`,
`MsFormula`, `TrInvDtl`, and every other model class) declares its
`@Table(name=...)` in **uppercase** — yet the real dump shows only
`MS_ITEM` is physically uppercase; the other 7 are physically lowercase.
The only way Legacy's own uniformly-uppercase Hibernate mappings could
ever have worked against a mostly-lowercase real schema is if Production's
actual MySQL server compares table names **case-insensitively** — which,
if true, would mean this entire class of problem is confined to *this
local validation container's* own case-sensitive MySQL configuration, not
a defect that would occur against real Production itself. This is a
strong, well-evidenced inference, **not proven** — Production's own
`lower_case_table_names` setting cannot be confirmed without connecting to
it, which is prohibited. It is recorded as a Gulliver/environment
confirmation item, per the "推測禁止" instruction.

## 2. Snapshot Schema Inventory

Restarted `gsys-prod-snapshot-20260916`; re-verified `SHOW GRANTS FOR
'gops_snapshot_audit'@'%'` = SELECT-only on `goo_prod_snapshot_20260916`
before any query (unchanged from Stage 5). Full inventory via
`information_schema.tables` (`BINARY table_name`, exact case, no
collation normalization) — 40 tables total. Exactly one is uppercase:

```
MS_ITEM                          <- the only uppercase table
batch_job_execution ... wk_stk   <- all 39 others, lowercase
```

Independently cross-checked against the raw `mysqldump` file itself
(`.local/prod-snapshot-20260916/goo_20260916.dmp`, local, already
extracted in Stage 1 — read directly, not via a live connection):
`grep -oE "CREATE TABLE \`[A-Za-z_]+\`"` returns the identical result —
`` `MS_ITEM` `` uppercase, every other `` `<table>` `` lowercase. Two
independent sources (the live restored container and the original,
unmodified export file) agree exactly. No individual business/row data
was queried for this section — table names only.

## 3. G-OPS Legacy SQL Inventory

**8 `resources/legacy/*.sql` files** contain literal `FROM`/`JOIN`
table references (comment lines excluded):

| File | Tables referenced |
|---|---|
| `PriceReadQuery.sql` | `ms_item`, `ms_comm` |
| `RecommendedQtyReadQuery.sql` | `ms_item`, `ms_stk`, `ms_comm`, `ms_formula`, `tr_po`, `tr_po_dtl` |
| `SkuPoHistoryReadQuery.sql` | `ms_comm`, `tr_po`, `tr_po_dtl` |
| `WarehouseStockBySkuQuery.sql` | `ms_item`, `ms_comm`, `ms_stk` |
| `WarehouseStockListQuery.sql` | `ms_item`, `ms_comm`, `ms_stk` |
| `WarehouseStockListCountQuery.sql` | `ms_item`, `ms_stk` |
| `ArrivalExpectedBySkuQuery.sql` | `tr_po_dtl`, `tr_arr` (deliberately never `ms_item`/`ms_stk`, per its own header comment) |
| `ArrivalListQuery.sql` / `ArrivalListCountQuery.sql` | `tr_arr`, `ms_comm`, `tr_po_dtl` (subquery), `tr_inv_dtl` (subquery) — deliberately never `ms_stk` |

**4 wrapper files** (`PriceCandidateListQuery/CountQuery.sql`,
`StockSalesListQuery/CountQuery.sql`) contain no literal table references
of their own — they substitute `${BASE_QUERY}` (the file above they wrap,
loaded and inlined by the owning Repository's constructor) and add only
`LIMIT`/`OFFSET`/extra `WHERE` clauses.

**2 inline-SQL Java methods** (beyond the resource files — found by
grepping every `.java` file for a quoted `FROM`/`JOIN` literal, not just
`repository/legacy/`; nothing outside that package contains one):

| File:Method | SQL | Screen/Function |
|---|---|---|
| `LegacyPriceReadRepository.findDistinctItemGroupCodes()` | `SELECT DISTINCT item_grp_cd FROM ms_item WHERE ...` | Price Change's Item Group picker (Bulk Selection) |
| `OfficialPoPreflightReadRepository.findExistingItemCodes()` | `SELECT item_cd FROM ms_item WHERE item_cd IN (:skus) ...` | Official PO Preflight (SKU existence check before generating/importing) |

Both of these were **not** in Stage 5's original finding — Stage 5 only
observed the Candidate List failure directly. This audit's systematic
sweep found 2 additional, previously-undocumented `ms_item`-dependent call
sites.

**Total distinct logical tables G-OPS references: 8** — `ms_item`,
`ms_stk`, `ms_comm`, `ms_formula`, `tr_po`, `tr_po_dtl`, `tr_arr`,
`tr_inv_dtl`.

## 4. Case Compatibility Matrix

| G-OPS logical table | G-OPS SQL spelling | Legacy Demo actual | Production Snapshot actual | Legacy Source `@Table` | Match? | Affected queries | Affected screen/function | Severity |
|---|---|---|---|---|---|---|---|---|
| Item Master | `ms_item` | `ms_item` | `MS_ITEM` | `MS_ITEM` | **NO** | `PriceReadQuery`, `RecommendedQtyReadQuery`, `WarehouseStockBySkuQuery`, `WarehouseStockListQuery`, `WarehouseStockListCountQuery`, `LegacyPriceReadRepository.findDistinctItemGroupCodes`, `OfficialPoPreflightReadRepository.findExistingItemCodes` | Candidate List, Price Change (search + Item Group picker), Stock/Sales List, SKU Detail, Official PO Preflight | **P0** |
| Stock | `ms_stk` | `ms_stk` | `ms_stk` | `MS_STK` | YES | `RecommendedQtyReadQuery`, `WarehouseStockBySkuQuery`, `WarehouseStockListQuery`, `WarehouseStockListCountQuery` | (same as above, stock-dependent) | — |
| Code Master (Brand/Supplier/etc.) | `ms_comm` | `ms_comm` | `ms_comm` | `MS_COMM` | YES | `PriceReadQuery`, `RecommendedQtyReadQuery`, `SkuPoHistoryReadQuery`, `WarehouseStockBySkuQuery`, `WarehouseStockListQuery`, `ArrivalListQuery`, `OfficialPoPreflightReadRepository` (×4) | Candidate/Price Change/Stock-Sales/SKU Detail/Arrival/Official PO | — |
| Formula | `ms_formula` | `ms_formula` | `ms_formula` | `MS_FORMULA` | YES | `RecommendedQtyReadQuery` | Candidate List/Stock-Sales (Recommended Qty calc) | — |
| PO Header | `tr_po` | `tr_po` | `tr_po` | `TR_PO` | YES | `RecommendedQtyReadQuery`, `SkuPoHistoryReadQuery`, `LegacyPoConcurrencyReadRepository`, `FulfillmentReadRepository` | Candidate, SKU PO History, Official PO Concurrency Check, Fulfillment | — |
| PO Detail | `tr_po_dtl` | `tr_po_dtl` | `tr_po_dtl` | `TR_PO_DTL` | YES | `RecommendedQtyReadQuery`, `SkuPoHistoryReadQuery`, `ArrivalExpectedBySkuQuery`, `ArrivalListQuery`, `LegacyPoConcurrencyReadRepository`, `FulfillmentReadRepository` | Candidate, SKU PO History, Arrival, Official PO Concurrency, Fulfillment | — |
| Arrival | `tr_arr` | `tr_arr` | `tr_arr` | `TR_ARR` | YES | `ArrivalExpectedBySkuQuery`, `ArrivalListQuery`, `ArrivalListCountQuery` | Arrival List, SKU Detail (expected arrival) | — |
| Invoice Detail | `tr_inv_dtl` | `tr_inv_dtl` | `tr_inv_dtl` | `TR_INV_DTL` | YES | `ArrivalListQuery`, `ArrivalListCountQuery`, `FulfillmentReadRepository` | Arrival List, Fulfillment | — |

## 5. Scope of Case Mismatch

**Confirmed: `MS_ITEM` is the only case mismatch among every table G-OPS
actually references.** All 7 others match exactly, both in the live
restored Snapshot container and in the raw, unmodified dump file. This
formally confirms — rather than merely assumes — Stage 5's own observation
("MS_ITEM以外はlowercase"). Note this only speaks to the *8 tables G-OPS
touches*; the Snapshot's other 32 tables (Spring Batch infrastructure,
`mig_*` migration-staging, `tr_bl*`/`tr_inv*`/`tr_rmt*`/`wk_*` tables
G-OPS never queries) were inventoried for completeness (§2) but not
individually matrix-checked, since no G-OPS SQL references them.

## 6. Legacy Source Comparison

Legacy G-SYS's own JPA entity classes (`phasep-gulliver/gulliver/src/main/
java/jp/ne/glv/model/*.java`) were checked directly for `@Table(name=...)`
— **every one of the 8 relevant entities declares an uppercase name**:
`MS_ITEM`, `MS_STK`, `MS_COMM`, `MS_FORMULA`, `TR_PO`, `TR_PO_DTL`,
`TR_ARR`, `TR_INV_DTL` (and, checked incidentally, every other entity in
that package — `HIS_ARR_LIST`, `HIS_STK_LIST`, `TR_BL`, `WK_STK`, etc. —
follows the identical uppercase convention with no exception found).

Cross-referencing this against §2/§4: the real Snapshot physically stores
7 of these 8 tables in **lowercase** and only `MS_ITEM` in uppercase.
**Proven, source-level fact**: `MS_ITEM`'s uppercase spelling is Production's
own genuine table name at export time (§2's dump-file check) — not a
Snapshot-restore artifact.

**Well-evidenced inference, not proven**: Legacy's own Hibernate layer has
used a uniformly-uppercase table-name convention for every entity, for as
long as this codebase exists, and it demonstrably works against
Production (Legacy G-SYS is the live system of record). The only way an
`@Table(name="MS_STK")` mapping resolves correctly against a physically
`ms_stk`-named table is if the database server being queried compares
table names **case-insensitively** (MySQL's `lower_case_table_names=1` or
`2`, the common default on Windows/macOS MySQL installs — as opposed to
this local container's `lower_case_table_names=0`, the Linux/Docker
default, case-sensitive). If real Production's MySQL server is indeed
case-insensitive, Legacy's uppercase mappings and G-OPS's lowercase SQL
would **both** resolve correctly against it with zero conflict — meaning
this entire case-mismatch problem may be **specific to how this local
validation container happens to be configured**, not a defect that would
manifest against the real, live Production server.

**This is not proven** — Production's actual `lower_case_table_names`
value cannot be confirmed without connecting to it, which this Stage (and
Stage 5) both prohibit. Per the "推測禁止" instruction, this is recorded
as an open Gulliver/environment confirmation item (§9's Finding 4), not
asserted as fact.

## 7. Demo Compatibility

Legacy Demo (`legacy_demo`, `lower_case_table_names=0`, confirmed) has
exactly 9 tables, all genuinely lowercase, including `ms_item`. Verified
directly: `SELECT * FROM ms_item` succeeds against Demo; **by the same
case-sensitive logic, `SELECT * FROM MS_ITEM` would fail against Demo** —
symmetric to the Snapshot's own failure mode in reverse. Any remediation
that rewrites G-OPS's SQL to the Production spelling (`MS_ITEM`) without
also addressing Demo would break every existing Demo-based E2E/integration
test and the entire local dev workflow. **A blanket "change G-OPS SQL to
uppercase" fix is therefore rejected outright**, consistent with this
Stage's explicit instruction.

## 8. Remediation Options (Design Only — Not Implemented)

| # | Option | Production correctness | Demo compatibility | Test compatibility | Maintainability | Legacy change? | Production DB change? | Risk |
|---|---|---|---|---|---|---|---|---|
| A | Rewrite G-OPS SQL to `MS_ITEM` everywhere | Fixes Snapshot/Production (if case-sensitive there) | **Breaks Demo** (§7) | **Breaks existing tests** | Low (one hardcoded spelling swapped for another) | No | No | **Rejected** |
| B | Environment-specific table-name mapping (a config property substituted into the 9 affected SQL sources at load/build time, e.g. `app.legacy.table-name.ms-item`, defaulting to `ms_item`) | Fixes it, if explicitly configured per environment | Unaffected (default unchanged) | Unaffected (default unchanged) | Moderate — touches 7 SQL files + 2 Java methods, but additively (a placeholder + substitution, same mechanism `${BASE_QUERY}` already establishes) | No | No | Low |
| C | Compatibility view/alias inside the Snapshot container (e.g. `CREATE VIEW ms_item AS SELECT * FROM MS_ITEM`) | Local-validation-only workaround | Unaffected | Unaffected | Low effort, but doesn't solve the real Application-portability question | No | **Yes — Snapshot schema change, explicitly discouraged and out of scope for any Stage that prohibits Snapshot writes** | Rejected as a real fix; not usable in a WRITE-prohibited validation Stage at all |
| D | (see §6) Reconfigure the **local Snapshot validation container only** to match Production's real, inferred case-insensitive behavior (`lower_case_table_names=1`, requiring a from-scratch reinitialize + restore, Stage-1-equivalent effort) | Fixes it with **zero Application/SQL/Legacy code change**, IF the inference in §6 is confirmed correct | Unaffected | Unaffected | Highest — no code to maintain at all | No | No (only this local, disposable validation container) | **Lowest risk of all options, but entirely contingent on an unconfirmed fact (§6/§9 Finding 4)** |

## 9. Recommended Remediation

**Two-step recommendation, in order:**

1. **First, resolve the open fact question (§6) via Gulliver/environment
   confirmation**: what is real Production MySQL's actual
   `lower_case_table_names` setting? This is a zero-risk, no-connection-
   required question (it can be answered by whoever administers the real
   Production server, or by inspecting Production's own `my.cnf`/
   equivalent) and it determines which remediation path is actually
   necessary.
2. **If Production is confirmed case-insensitive** (as inferred, §6):
   prefer **Option D** — reinitialize only the local validation
   container. This requires no Application code change at all, carries no
   Demo/test risk, and most faithfully reproduces what a real
   G-OPS-to-Production connection would actually experience. **If
   Production is confirmed case-sensitive, or cannot be confirmed**:
   fall back to **Option B** (environment-specific table-name mapping) —
   the only remaining option that fixes the real mismatch without
   breaking Demo, without touching Legacy, and without any Production/
   Snapshot DB change.

Neither option was implemented this Stage, per instruction.

## 10. Safety Guard Analysis

Current state (unchanged since Stage 5): `SafetyGuardEnvironmentPostProcessor
.ALLOWED_LEGACY_DB_NAMES = Set.of("legacy_demo")`, hardcoded, with no
sanctioned path for a temporary, controlled Snapshot-validation connection
without an Application code edit. This is exactly the conflict Stage 5 hit
(its one attempted edit — even though pre-approved by you — was
independently denied at the Claude Code tool-permission layer; no
workaround was attempted).

## 11. Proposed Snapshot Validation Gate Design (Design Only — Not Implemented)

A config-driven exception path, satisfying every absolute condition in
the instruction:

- **A dedicated profile**, e.g. `snapshot-validation`, added to a
  *separate* allowlist from `ALLOWED_PROFILES` — never silently unioned
  into normal `local`/`demo`/`test` behavior, and never sufficient by
  itself (below).
- **A dedicated, narrowly-named property/env var**, e.g.
  `GOPS_SNAPSHOT_VALIDATION_ALLOWED_DB_NAME`, read **only** when the
  `snapshot-validation` profile is active. Profile alone grants nothing —
  both the profile *and* this property must be explicitly, simultaneously
  supplied for the exception to apply at all (satisfies "profileだけでは
  許可しない").
- **Exact-string match only** — the configured value is compared with
  `.equals()`, never a pattern/prefix/wildcard match, against the actual
  parsed Legacy JDBC URL database name (reusing the existing
  `parseJdbcUrl`, not a new parser).
- **`goo` (and, defensively, any name equal to Production's real schema
  name) is permanently, unconditionally rejected** — checked *before* the
  opt-in property is even read, so no configuration value, typo, or
  future change to the property can ever allow it through. This check is
  not itself configurable.
- **Default is deny**: with no `snapshot-validation` profile active (the
  normal case for every existing `local`/`demo`/`test` startup, unchanged),
  this entire code path never executes — current behavior is 100%
  preserved for every existing use.
- **Audit/log visibility**: whenever the exception path is actually taken,
  log at `WARN` (not `INFO`, not hidden behind a debug flag) with the
  exact allowed name and a message stating this must never be used
  against real Production — visible in every startup log, impossible to
  miss.
- This design, if built in a future Stage, would let a Controlled
  Snapshot Validation session set two env vars at process start and
  require **no source file edit at all** — closing exactly the gap Stage
  5 hit.

Not implemented this Stage, per instruction.

## 12. Findings

| # | Finding | Severity | Category |
|---|---|---|---|
| 1 | `MS_ITEM` is the sole case mismatch among all 8 G-OPS-referenced Legacy tables; confirmed via both the live Snapshot and the raw, unmodified dump file. Affects 7 SQL sources across 5 distinct screens/functions (2 of which — Price Change's Item Group picker, Official PO Preflight — were not previously documented). | **P0** | **REAL-DATA BLOCKER** |
| 2 | Every Legacy G-SYS JPA entity (not only `MsItem`) declares an uppercase `@Table` name, while 7 of 8 real Snapshot tables are physically lowercase — only resolvable if Production's MySQL compares table names case-insensitively. Not confirmed; recorded as an open fact question. | P1 | **GULLIVER CONFIRMATION** |
| 3 | If Finding 2 is confirmed true, the P0 blocker may be entirely local-validation-environment-specific (this container's own MySQL init setting), not a defect that would occur against real Production — significantly changes remediation priority/risk once confirmed. | P1 | SCHEMA COMPATIBILITY |
| 4 | The Safety Guard's allowlist has no sanctioned, reviewable path for a controlled Snapshot-validation connection without an Application code edit — a real, recurring operational gap (this Stage's own §11 design addresses it, not yet built). | P2 | SAFETY / DEVELOPER UX |
| 5 | `ms_stk`, `ms_comm`, `ms_formula`, `tr_po`, `tr_po_dtl`, `tr_arr`, `tr_inv_dtl` all match case cleanly across G-OPS SQL, Demo, and the real Snapshot — no issue found for any of them. | — | NO ISSUE |

## 13. Stage 5B Recommendation

Not decided here, per instruction. This Stage's own output (the
compatibility matrix, the two remediation-path recommendation in §9
contingent on confirming Finding 2/3, and the Safety Guard gate design in
§11) is handed off as the input a Stage 5B (if and when approved) would
implement — nothing here has been built.
