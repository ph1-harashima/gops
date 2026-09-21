# G-OPS Production Snapshot Real Data Audit — Stage 1: Isolated Restore + Integrity Verification

Scope: extract the archive into an isolated, git-ignored working directory,
confirm dump format/version evidence, stand up a fully isolated local MySQL
instance, and attempt the restore. **The restore did not complete** - it
stopped partway through on a genuine MySQL 8.0 row-size compatibility
error. No dump content was modified. No workaround was applied without
explicit approval. This document records exactly what was found and where
Stage 1 stopped.

## 1. Stage 0 Commit

`4c91b16` - "G-OPS Production Backup Real Data Audit Stage 0: archive
inspection + gitignore hardening" (`.gitignore` + Stage 0 report only -
verified via `git diff --cached --stat` and `git check-ignore` before
committing that the backup archive itself was never staged).

## 2. Archive Extraction

Extracted via `7z x` into `.local/prod-snapshot-20260916/` - a new,
git-ignored scratch directory (`.gitignore` updated first: a whole-directory
`.local/` rule, confirmed with `git check-ignore -v` against the exact
extraction target path *before* extracting). Never written into any tracked
project source directory. Extracted file: `goo_20260916.dmp`, 5,221,243,233
bytes - matches the archive's own uncompressed-size metadata from Stage 0
exactly.

## 3. Dump Format

Confirmed via `file` (type detection only): `UTF-8 text, with very long
lines, CRLF line terminators` - a plain-text logical dump, not a binary/
physical format. Read (structure only, see §5 for the exact
personal-data-avoidance method used):

```
-- MySQL dump 10.13  Distrib 8.0.13, for Win64 (x86_64)
-- Host: localhost    Database: goo
-- Server version	8.0.13
```

Structural scan (counts and first-match line numbers only, no data rows
read) across the full 5GB file:

| Object | Count |
|---|---|
| `CREATE TABLE` (= table count) | 41 |
| `CREATE DATABASE` | 0 (target DB must be pre-created before import) |
| `USE` statements | 0 (no embedded DB-name switch - safe to import into any target name) |
| `DEFINER` | 0 (no views/procedures/triggers/events with a definer account) |
| `CREATE ... VIEW` | 0 |
| `CREATE ... PROCEDURE/FUNCTION` | 0 |
| `CREATE ... TRIGGER` | 0 |
| GTID statements | 0 |
| `LOCK TABLES` | 41 (one per table, standard mysqldump wrapping) |

**Dump Format Gate: PASS.** Plain-text, MySQL-compatible, single-database
logical dump with no exotic objects (no views/procs/triggers/DEFINER/GTID
to reconcile). Not encrypted, not corrupted, not a physical/binary/
proprietary format.

## 4. Source Version Evidence

- Dump tool: `mysqldump Distrib 8.0.13` (from the dump's own header, not
  inferred from any spec document).
- `MS_ITEM` and `ms_stk`'s own `CREATE TABLE` DDL specify
  `COLLATE=utf8mb4_0900_ai_ci` - this collation was introduced in MySQL 8.0
  and does not exist in 5.7 or earlier, so the **source server was
  genuinely MySQL 8.0.x** (confirmed by DDL evidence, not merely taken on
  the dump header's word or the project spec doc's claim).
- Other tables (`ms_comm`, `tr_po`, `tr_po_dtl`, `tr_arr`) use the plain
  `utf8` charset (3-byte, pre-mb4) rather than `utf8mb4` - the schema is a
  charset mix, not uniform, which matters for §11.

## 5. Target MySQL Version

`mysql:8.0` (resolved to server version **8.0.46** on container start) -
selected because the source's own DDL (§4) requires an 8.0-capable
collation; no earlier MySQL/MariaDB version would even accept this schema.
Same major.minor series as the dump's own 8.0.13 origin - a normal,
same-series upgrade-path restore, not a cross-version migration.

## 6. Isolation Configuration

| | |
|---|---|
| Container | `gsys-prod-snapshot-20260916` (new, dedicated) |
| Image | `mysql:8.0` |
| Port | `33199` (host) → `3306` (container-internal, not host 3306) |
| Database | `goo_prod_snapshot_20260916` (pre-created via `MYSQL_DATABASE`) |
| Volume | `gops-prod-snapshot-20260916-data` (new, dedicated - never mounts an existing volume) |

Confirmed before and after container creation: `gsys-legacy-demo-mysql`
(port 33061, `legacy_demo`) and `gsys-prototype-postgres` (port 54321,
`gsys_portal`) both remained `Up`/healthy throughout, never restarted,
never connected to, never had any command issued against them during this
Stage. Host ports 3306/33061/54321/33199 were checked via `netstat` both
before container creation (only 33061/54321 in use) and confirmed correct
after (33199 now also in use, by the new container only).

## 7. Restore Result

**Did not complete.** `cat goo_20260916.dmp | docker exec -i
gsys-prod-snapshot-20260916 mysql -u root -p*** goo_prod_snapshot_20260916`
ran for 45 seconds, successfully created and loaded 10 of 41 tables (all 9
Spring Batch infrastructure tables, all empty, plus `his_arr_list` with
282,592 rows), then stopped on the 11th table (`his_stk_list`) with a hard
MySQL error (the plain `mysql` CLI stops at the first error unless
`--force` is passed, which this restore attempt did not use).

## 8. Restore Warnings/Errors

Exactly one error, verbatim (structural - table/column names and a byte
count, no data row content):

```
ERROR 1118 (42000) at line 611: Row size too large (> 8126). Changing some
columns to TEXT or BLOB may help. In current row format, BLOB prefix of 0
bytes is stored inline.
```

**Root cause** (confirmed via DDL + container variable inspection, not
guessed): `his_stk_list` (274 lines of DDL - a very wide "stock history"
table, structurally similar in shape to `ms_stk`'s own repetitive PO/
shipment-tracking columns) is declared `CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci` (4-byte charset - larger per-column byte
reservation than the plain `utf8` most other wide tables in this dump use).
The target container's own settings - `innodb_default_row_format=dynamic`
and `innodb_strict_mode=ON` - are MySQL 8.0's own **factory defaults**,
not anything this Stage changed. This means the *identical* error would
occur importing this exact dump into any stock/default MySQL 8.0 instance,
not something specific to this isolated container's configuration - a
genuine dump/target compatibility finding, not a setup mistake.

No dump content was rewritten to work around this. No target-server
setting (e.g. `innodb_strict_mode`) was changed to force it through -
that decision is deferred to explicit review (§16).

## 9. Schema Object Counts (partial - restore incomplete)

| | Count |
|---|---|
| Tables created | 10 of 41 |
| Views | 0 (dump contains none - §3) |
| Triggers | 0 (dump contains none - §3) |
| Procedures/Functions | 0 (dump contains none - §3) |

## 10. Core Table Existence

| Required table | Present after this attempt? |
|---|---|
| `MS_ITEM` | **No** - not yet reached (restore stopped before it) |
| `ms_stk` (MS_STK) | **No** - not yet reached |
| PO tables (`tr_po`, `tr_po_dtl`) | **No** - not yet reached |
| Arrival tables (`tr_arr`, `his_arr_list`) | `his_arr_list`: **Yes** (282,592 rows); `tr_arr`: **No** - not yet reached |
| Supplier/Brand master (`ms_comm`, filtered by `CATE_ID`) | **No** - not yet reached (confirmed via `docs/GSYS_Specification.md` §Entity table that Supplier/Brand are `ms_comm` config rows under `CATE_ID='MS_SUPPL'`/`'MS_BRAND'`, not separate dedicated tables - no dedicated Supplier/Brand table exists in this schema at all) |

None of the tables this Stage was specifically asked to confirm (§11 of the
task instruction) were reached before the restore stopped.

## 11. Basic Row Counts

Only the 10 successfully-created tables can be counted (all `COUNT(*)`,
counts only, no row content read or printed):

| Table | Row count |
|---|---|
| `batch_job_execution` | 0 |
| `batch_job_execution_context` | 0 |
| `batch_job_execution_params` | 0 |
| `batch_job_execution_seq` | 1 |
| `batch_job_instance` | 0 |
| `batch_job_seq` | 1 |
| `batch_step_execution` | 0 |
| `batch_step_execution_context` | 0 |
| `batch_step_execution_seq` | 1 |
| `his_arr_list` | **282,592** |

`MS_ITEM`/`ms_stk`/PO/`tr_arr`/Supplier-Brand counts: not available (§10).

## 12. Audit User Grants

**Not created.** Creating the dedicated `SELECT`-only audit user was the
final step of a successful restore (§13-14 of the task instruction); since
the restore itself did not complete, this step was not attempted this
round - there is no coherent, complete dataset yet to scope a read-only
user against.

## 13. Read-only Verification

Not performed, for the same reason as §12.

## 14. Privacy Handling

- The dump file was never opened/viewed in full - only `file` (type
  detection), `grep -c`/`grep -m1 -n` (structural counts and single
  matching lines for non-data patterns like `DEFINER`/`GTID`), and `awk`
  scoped precisely to `CREATE TABLE ... ) ENGINE=...;` blocks (schema DDL
  only) were used to inspect it.
- Before printing any header content, the exact line number of the first
  `INSERT INTO` statement was located first (`grep -n -m1`), and only lines
  strictly before it were read - guaranteeing zero data rows were ever
  displayed, logged, or transcribed into this document.
- The one row count that reveals real Production scale (`his_arr_list`:
  282,592) is an aggregate count, not row content - consistent with the
  Stage 0 privacy rule (aggregate counts are permitted; individual field
  values are not).
- No personal name, email, phone number, address, or credential from the
  dump appears anywhere in this document, the restore log, or the
  structural-scan output.
- The restore container's own root password
  (`snapshot_audit_local_only_pw`) is a locally-generated, disposable
  credential for this isolated container only - not a Production
  credential, and is not repeated here beyond this one explanatory
  sentence; it is not written to any committed file.

## 15. Remaining Risks

- **Blocking**: `his_stk_list`'s row-width vs. MySQL 8.0 strict-mode
  default is a genuine compatibility issue that will recur on any retry
  using default settings - needs an explicit decision (§16) before Stage 1
  can proceed further.
- Because the `mysql` CLI stopped at the first error, **30 of 41 tables
  were never even attempted** (not merely `his_stk_list` itself) - the true
  extent of any *other* tables that might hit the same or a different
  issue is unknown until a retry is authorized.
- Host memory is tight (3.9 GB free of 15.9 GB at the time of this Stage,
  consistent with the repeated Full E2E memory-pressure interruptions
  earlier in this project's session) - worth keeping in mind for a longer
  retry attempt, independent of the row-size issue itself.
- The partially-restored container/volume (`gsys-prod-snapshot-20260916`,
  10 tables including 282,592 real `his_arr_list` rows) was left running,
  not torn down, so a retry can resume/rebuild from a known state rather
  than re-extracting the 4.9GB dump from scratch - this itself means real
  Production data is currently sitting in this container, exactly as Stage
  1's own design intends (isolated, not connected to G-OPS, not exposed).

## 16. Stage 2 Readiness

**Not ready** - the restore is incomplete, and no Business Data Audit can
meaningfully start on a database missing `MS_ITEM`, `ms_stk`, every PO/
Arrival business table, and the Supplier/Brand master rows. A resolution
decision for §8's blocker is needed first.

**Proposed resolution (not applied - awaiting explicit approval, per this
task's own "勝手にDumpを書き換えない" instruction)**: set
`innodb_strict_mode=OFF` on this isolated, disposable target container
only (a server-side session/global setting, applied via `SET GLOBAL` or a
container restart flag - the dump file itself would remain byte-for-byte
unmodified) and retry the restore with the `mysql` CLI's `--force` flag so
a retry does not halt entirely on the first table that needs it. This is a
standard, well-understood technique for importing older/wide legacy table
designs into a strict-by-default modern MySQL server, not a dump rewrite -
but it is a judgment call this task's own instructions reserve for explicit
review rather than autonomous action, so it was not applied.
