# G-OPS Production Snapshot Real Data Audit — Stage 1B: Restore Compatibility Analysis + Clean Re-Restore

Scope: root-cause `ERROR 1118` from Stage 1, identify and verify a minimal,
non-dump-modifying compatibility setting in isolation, then discard the
Stage 1 partial restore and clean-rebuild the isolated instance for a
fresh, from-the-top restore. **The compatibility problem is fully
diagnosed and resolved and verified** (§2-9 below); **the clean re-restore
itself did not reach completion**, stopped partway through by a host
memory-pressure kill unrelated to the compatibility fix (§10-11). This is
a different kind of blocker than Stage 1's, and is reported separately.

## 1. Stage 1 Failure

Commit `44bef08`. Restore stopped at 10/41 tables on
`ERROR 1118 (42000): Row size too large (> 8126)` while creating
`his_stk_list`.

## 2. ERROR 1118 Root Cause

`his_stk_list`'s own `CREATE TABLE` DDL, read directly from the dump
(schema only, `awk` scoped to the `CREATE TABLE ... ) ENGINE=...;` block -
no data rows read):

| Characteristic | Value |
|---|---|
| Column count | **271** |
| `VARCHAR` columns | 165 (several up to `varchar(1000)`/`varchar(800)`) |
| `CHAR` (fixed) columns | 0 |
| `TEXT` columns | 2 (`WEB_DESCRIPTION`, `ADD_NOTE_1`) |
| `BLOB` columns | 0 |
| Generated columns | 0 |
| Indexes | 1 (`PRIMARY KEY (ID)` only) |
| `ROW_FORMAT` in the dump's DDL | **Not specified** - relies on server default |
| `ENGINE` | InnoDB |
| Charset / Collation | `utf8mb4` / `utf8mb4_0900_ai_ci` |

Root cause: this is a very wide "stock history" table (271 columns, mostly
optional `VARCHAR` under the 4-byte `utf8mb4` charset). InnoDB's row-size
check sums the worst-case *inline* byte reservation for every column not
eligible for off-page storage. The dump's own error text -
`"In current row format, BLOB prefix of 0 bytes is stored inline"` -
confirms `ROW_FORMAT=DYNAMIC` was already in effect (0-byte inline BLOB/TEXT
prefix is DYNAMIC's own defining behavior, vs. up to 768 bytes for
`COMPACT`/`REDUNDANT`) - the table was already using the best applicable
row format, and the sheer column count/width still exceeded the 8126-byte
inline limit. This is a genuine "too many wide columns" legacy-schema
characteristic, not a misconfigured row format.

## 3. his_stk_list DDL Characteristics

(Full table shown in §2 - no additional detail needed.)

## 4. Source/Target Setting Comparison

| Setting | Target (`gsys-prod-snapshot-20260916`, before any change) |
|---|---|
| `innodb_strict_mode` | `ON` (MySQL 8.0's own default) |
| `innodb_default_row_format` | `dynamic` (MySQL 8.0's own default) |
| `innodb_page_size` | `16384` (default) |
| `sql_mode` | `ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION` (default) |
| `character_set_server` | `utf8mb4` |
| `collation_server` | `utf8mb4_0900_ai_ci` |

None of these were changed by this Stage before the reproduction test -
they are `mysql:8.0`'s factory defaults. The dump's own DDL specifies no
`ROW_FORMAT`/other `CREATE TABLE` option for `his_stk_list` - the failure
is purely a consequence of the column-width sum vs. these (unmodified)
target defaults, confirmed identical on a plain, freshly-created MySQL 8.0
container.

## 5. Reproduction Result

Reproduced in an isolated, disposable database (`ddl_repro_test`, created
and later dropped on the same disposable snapshot container - **no
Production rows were imported into it**, DDL-only):

- With target default settings: **identical failure reproduced**
  (`ERROR 1118`, same message) - confirms this is a pure schema/target
  interaction, not something specific to a concurrent restore stream.
- With `SET SESSION innodb_strict_mode=OFF;` before the same DDL:
  **table created successfully, zero warnings.**

## 6. Compatibility Options Tested

| Option | Result |
|---|---|
| A. Target default settings | Fails (reproduces Stage 1's error) |
| B. `innodb_strict_mode=OFF` only (session-scoped) | **Succeeds, zero warnings, schema exactly as declared** |
| C. Explicit `ROW_FORMAT`/`innodb_page_size` change | Not tested - unnecessary once B fully resolved it with no side effects |
| D. Other dump-evidence-driven setting | None identified as needed |

## 7. Selected Minimal Setting

**Option B: `innodb_strict_mode=OFF`, applied only as a per-connection
session setting** (via the `mysql` client's own `--init-command`, not a
`SET GLOBAL`, and never written into the dump file itself) for the restore
connection only. Scoped exclusively to the dedicated
`gsys-prod-snapshot-20260916` container - never applied to
`gsys-legacy-demo-mysql`, `gsys-prototype-postgres`, or any Production/UAT
system, none of which this Stage ever connected to.

## 8. SHOW WARNINGS Result

Empty (0 rows) immediately after the `CREATE TABLE his_stk_list` statement
with `innodb_strict_mode=OFF` in both the isolated reproduction (§5) and
the real clean re-restore (§11) - `innodb_strict_mode=OFF` demoted the
specific row-size-too-large condition cleanly enough that MySQL did not
even need to surface a warning for it in this case.

## 9. Schema Equivalence Result

Compared the reproduced table (`information_schema.columns` /
`information_schema.tables`) against the dump's own declared DDL:

| Check | Expected (from dump) | Actual (created) | Match? |
|---|---|---|---|
| Column count | 271 | 271 | Yes |
| Engine | InnoDB | InnoDB | Yes |
| Charset/Collation | utf8mb4 / utf8mb4_0900_ai_ci | utf8mb4 / utf8mb4_0900_ai_ci | Yes |
| Row format | (unspecified, DYNAMIC by default) | Dynamic | Yes |
| `AUTO_INCREMENT` starting value | 78313 | 78313 | Yes |

No column was dropped, retyped, truncated, or silently converted to a
different storage class. **Schema equivalent: YES.**

**Clean Re-Restore Gate (§9 of the task instruction) - all items met:**
- [x] ERROR 1118 Root Cause understood
- [x] Minimal compatibility setting identified (Option B)
- [x] `his_stk_list` DDL succeeds
- [x] `SHOW WARNINGS` reviewed (empty)
- [x] Resulting DDL acceptable (schema equivalent, §9 table above)
- [x] No Dump modification (verified - `goo_20260916.dmp` was never opened for writing at any point in this Stage)
- [x] No `--force` required

Gate **passed** - proceeded to Clean Re-Restore (§10-11).

## 10. Clean Rebuild Procedure

1. Confirmed `gsys-legacy-demo-mysql` and `gsys-prototype-postgres` both
   still `Up`/healthy (untouched) before any teardown.
2. Stopped and removed the **Stage 1 partial** container
   (`gsys-prod-snapshot-20260916`, 10/41 tables) and its dedicated volume
   (`gops-prod-snapshot-20260916-data`) - the only container/volume
   touched by this step.
3. Re-confirmed `gsys-legacy-demo-mysql`/`gsys-prototype-postgres` still
   `Up`/healthy and their own volumes (`backend_legacy-demo-mysql-data`,
   `backend_prototype-postgres-data`) unaffected, immediately after.
4. Created a **new** dedicated volume (same name,
   `gops-prod-snapshot-20260916-data` - a fresh Docker volume object, not
   the old one) and a **new** container instance (`gsys-prod-snapshot-20260916`,
   new container ID, same port `33199`, same database name
   `goo_prod_snapshot_20260916`).
5. Restored from the top of `goo_20260916.dmp` again, in full, via
   `mysql --init-command="SET SESSION innodb_strict_mode=OFF"` - the
   dump content itself was streamed unmodified; only the connection's own
   session setting differs from Stage 1's attempt. **No `--force` was
   used** - the first genuine SQL error would still have stopped the
   restore, exactly as required.

## 11. Restore Result

**Did not reach completion - interrupted by host memory pressure,
not a SQL error.** The background restore process was killed by the
harness's own out-of-memory protection while the session was otherwise
idle (host free memory: ~3 GB before the restore, dropping to ~2.1 GB
after the kill - the same class of interruption seen repeatedly earlier in
this project's session during Full E2E runs, now recurring during a large
DB restore). Per instruction, the process was **not** restarted
automatically.

**Before the interruption, 18 of 41 tables were successfully created and
loaded**, including `his_stk_list` itself (the exact table Stage 1 failed
on) - **direct, real-restore confirmation that the Option B fix works**,
not just in the isolated §5 reproduction:

`MS_ITEM`, `batch_job_execution`, `batch_job_execution_context`,
`batch_job_execution_params`, `batch_job_execution_seq`,
`batch_job_instance`, `batch_job_seq`, `batch_step_execution`,
`batch_step_execution_context`, `batch_step_execution_seq`,
`his_arr_list`, **`his_stk_list`**, `mig_brand`, `mig_brand_conv`,
`mig_conv`, `mig_stk_list`, `ms_comm`, `ms_formula`.

The `mysql` client's own restore log shows **zero `ERROR` lines** for this
attempt (only the standard command-line-password advisory) - the halt was
external (the process was killed), not a SQL rejection.

## 12. Structural Verification

| Requested table (task's own naming) | Actual dump table | Present in this attempt? |
|---|---|---|
| `MS_ITEM` | `MS_ITEM` | **Yes** |
| `MS_STK` | `ms_stk` | No - not yet reached when killed |
| `MS_COMM` | `ms_comm` | **Yes** |
| PO header | `tr_po` | No - not yet reached |
| PO detail | `tr_po_dtl` | No - not yet reached |
| `TR_ARR` | `tr_arr` | No - not yet reached (`his_arr_list`, the history table, was) |
| `HIS_STK_LIST` | `his_stk_list` | **Yes** - the table Stage 1 failed on |

**Dump expected table count: 41. Restored (this attempt): 18.** Views/
Triggers/Procedures/Functions: dump contains 0 of each (confirmed Stage 1
§3) - trivially "0 of 0" either way, not a gap.

## 13. Basic Row Counts

`COUNT(*)` only, for tables that exist in this attempt (no distribution/
relationship analysis):

| Table | Row count |
|---|---|
| `MS_ITEM` | **116,842** |
| `his_arr_list` | 282,592 |
| `his_stk_list` | **67,199** (the table Stage 1 could not even create) |
| `ms_comm` (all categories) | 3,825 |
| `ms_comm` where `CATE_ID='MS_SUPPL'` (Supplier) | **602** |
| `ms_comm` where `CATE_ID='MS_BRAND'` (Brand) | **1,504** |

`ms_stk`, `tr_po`, `tr_po_dtl`, `tr_arr`: not available (not yet restored
when the process was killed).

## 14. Audit User Grants

**Not created.** Per the task's own Ownership/Gate design, the dedicated
`SELECT`-only audit user is created only after a **complete** restore -
since this attempt reached 18/41, not 41/41, that step was not attempted.

## 15. Resource Usage

| | Before restore | After the kill |
|---|---|---|
| Host free memory | ~3.0 GB | ~2.1 GB |
| `gsys-prod-snapshot-20260916` container memory | 451 MiB | (container still running) |
| `gsys-legacy-demo-mysql` container memory | 455 MiB (unaffected) | unaffected |
| `gsys-prototype-postgres` container memory | 70 MiB (unaffected) | unaffected |

No stray Playwright/Chromium/Node test process was found before this
Stage's restore attempt (checked via `tasklist`/`wmic` - only the project's
own 2 legitimate Vite dev-server `node.exe` processes were running, left
untouched). Docker's own per-container memory usage stayed modest and
well under its 7.7 GB limit throughout - the memory pressure was host-wide,
not attributable to this restore's own container footprint specifically.

## 16. Remaining Risks

- **Host memory capacity remains the single blocking risk** - not a
  compatibility or dump-content issue (that is now fully resolved and
  verified, §2-9). This is the same class of interruption that has now
  affected this project's session multiple times (Full E2E runs earlier,
  and now this restore) - an environment-capacity issue outside this
  task's own technical scope to resolve.
- The current container holds a **genuinely partial** (18/41 tables) but
  **real** Production dataset (116,842 `MS_ITEM` rows, 67,199
  `his_stk_list` rows, etc.) - correctly isolated (no G-OPS/Legacy Demo/
  Portal connection), but not yet a complete, audit-ready snapshot.
- A retry, when host memory allows, should be able to resume from a clean
  state quickly since the compatibility fix (§7) is already proven to work
  end-to-end for the one table that previously blocked progress entirely.

## 17. Stage 2 Readiness

**Not ready.** The compatibility blocker from Stage 1 is fully resolved and
verified (Clean Re-Restore Gate, §9, passed in full) - but the restore
itself has not yet reached 41/41 tables, so no Business Data Audit can
begin. No further restore attempt was made automatically after the kill,
per instruction.
