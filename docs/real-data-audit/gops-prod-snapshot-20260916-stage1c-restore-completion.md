# G-OPS Production Snapshot Real Data Audit — Stage 1C: Clean Restore Completion

Scope: complete a full, clean, from-the-top restore of `goo_20260916.dmp`
using the compatibility setting already root-caused and verified in Stage
1B (`c569eba`) - no re-investigation of the compatibility question itself.
**Restore succeeded: 41/41 tables.**

## 1. Stage 1B Commit

`c569eba` - compatibility fully resolved and verified
(`innodb_strict_mode=OFF`, session-scoped only; schema equivalence
confirmed). Not re-examined this Stage, per instruction.

## 2. Memory Preparation

Host free memory before this Stage: **0.6 GB** of 15.9 GB - critically low.
Checked for stoppable project processes (`tasklist`/`wmic`): no stray
Playwright or Chromium test processes were running (none found, matching
every prior check this session). The only project-owned processes found
were the G-OPS backend dev server (`java.exe`, Spring Boot, ~418 MB) and
the frontend dev server (`node.exe` ×2, `npm run dev` + Vite, ~40 MB +
135 MB) - both legitimate, but genuinely unused for this Stage's own scope
(Stage 1C explicitly does not connect G-OPS to anything). Stopped all
three (`taskkill /F`) - freed host memory to **~1.0 GB**. No user Chrome
process or any non-project process was touched. The dominant host memory
consumer (`vmmem`, Docker Desktop's own VM, ~5.4 GB) was left untouched -
stopping it would have taken down the very Docker containers (including
`gsys-legacy-demo-mysql`/`gsys-prototype-postgres`) this task must not
touch.

## 3. Processes Safely Stopped

- G-OPS backend dev server (Spring Boot, `java.exe`)
- G-OPS frontend dev server (`npm run dev` + Vite, `node.exe` ×2)

Neither is part of any test suite; both are simply not needed while Stage
1C never connects G-OPS to anything. Not restarted automatically by this
Stage (out of this task's own scope - Stage 1C's restrictions explicitly
forbid connecting G-OPS to the snapshot, and restarting the dev servers has
no bearing on that restriction either way).

## 4. Clean Container/Volume Recreated

Confirmed `gsys-legacy-demo-mysql`/`gsys-prototype-postgres` both
`Up`/healthy immediately before teardown. Discarded the Stage 1B partial
container (`gsys-prod-snapshot-20260916`, 18/41 tables) and its volume
(`gops-prod-snapshot-20260916-data`) - the only container/volume touched.
Re-confirmed both existing Demo instances still `Up`/healthy, unaffected,
immediately after. Created a fresh volume (same name, new object) and a
new container instance - same isolation as every prior Stage: port
`33199`, database `goo_prod_snapshot_20260916`, dedicated volume, no
existing volume mounted.

## 5. Restore Start / Result

`cat goo_20260916.dmp | docker exec -i gsys-prod-snapshot-20260916 mysql
--init-command="SET SESSION innodb_strict_mode=OFF" -u root -p***
goo_prod_snapshot_20260916` - the dump streamed from the top, unmodified.
**No `--force`.** No `SET GLOBAL` of any kind - `innodb_strict_mode=OFF`
was applied only as this one connection's own session setting via
`--init-command`, exactly as verified in Stage 1B.

**Result: succeeded, exit code 0.** The restore log contains **zero
`ERROR` lines** (only the standard command-line-password advisory) and
**zero SQL warnings**.

## 6. Restore Duration

17 minutes 55.7 seconds (`real 17m55.721s`).

## 7. SQL Errors

0

## 8. SQL Warnings

0

## 9. Memory Kill

**NO** - this attempt completed without interruption.

## 10. Completion Gate

| | Expected (dump) | Restored | Match? |
|---|---|---|---|
| Tables | 41 | **41** | **Yes** |
| Views | 0 | 0 | Yes |
| Triggers | 0 | 0 | Yes |
| Procedures/Functions | 0 | 0 | Yes |

**41/41 - Completion Gate: PASS.**

## 11. Structural Verification (Core Tables)

| Table | Present? |
|---|---|
| `MS_ITEM` | Yes |
| `ms_stk` | Yes |
| `ms_comm` | Yes |
| `tr_po` | Yes |
| `tr_po_dtl` | Yes |
| `tr_arr` | Yes |
| `his_arr_list` | Yes |
| `his_stk_list` | Yes |

All 8 required core tables present, alongside the other 33 tables (Spring
Batch infrastructure, migration-staging (`mig_*`), transaction/logistics
(`tr_bl*`, `tr_inv*`, `tr_rmt*`, `tr_packing_inv`, `tr_prod_desc`,
`tr_item_yahoo_dtl`), work tables (`wk_cpo`, `wk_stk`), and Master/admin
(`ms_item_category`, `ms_item_grp`, `ms_item_outlet`, `ms_stk_bk`,
`ms_user`, `ms_formula`, `sys_send_mail`) - the complete 41-table set
matches the dump's own `CREATE TABLE` inventory exactly.

## 12. Basic Row Counts

`COUNT(*)` only - no distribution, relationship, or content analysis:

| Table | Row count |
|---|---|
| `MS_ITEM` | 116,842 |
| `ms_stk` | 1,518,842 |
| `ms_comm` (all categories) | 3,825 |
| `tr_po` | 8,376 |
| `tr_po_dtl` | 183,480 |
| `tr_arr` | 17,722 |
| `his_arr_list` | 282,592 |
| `his_stk_list` | 67,199 |
| Supplier (`ms_comm` where `CATE_ID='MS_SUPPL'`) | 602 |
| Brand (`ms_comm` where `CATE_ID='MS_BRAND'`) | 1,504 |

No relationship/cardinality/distribution analysis was performed - counts
only, per instruction.

## 13. Audit User Grants

Created `gops_snapshot_audit`@`%`. `SHOW GRANTS` output, verbatim:

```
GRANT USAGE ON *.* TO `gops_snapshot_audit`@`%`
GRANT SELECT ON `goo_prod_snapshot_20260916`.* TO `gops_snapshot_audit`@`%`
```

(`USAGE ON *.*` is MySQL's own standard placeholder line every account
shows - it grants no capability by itself, only confirms the account
exists.) Exactly one real grant: `SELECT` on `goo_prod_snapshot_20260916.*`.
No `INSERT`/`UPDATE`/`DELETE`/`CREATE`/`ALTER`/`DROP`/`TRUNCATE`/`EXECUTE`/
`FILE`/`SUPER`/`GRANT OPTION` present. Verified structurally via
`SHOW GRANTS` - no actual `UPDATE`/`DELETE`/etc. was attempted against the
data, per instruction.

## 14. Privacy Handling

Every count above is an aggregate `COUNT(*)`. No individual row, order
content, customer name, personal name, email, phone number, address, or
credential appears anywhere in this document, the restore log, or any
command output quoted here. The audit user's own password
(`snapshot_readonly_audit_local_pw`) is a locally-generated, disposable,
read-only-scoped credential for this isolated container only - stated once
here for completeness, never written into any other committed file, and
not a Production credential.

## 15. Remaining Risks

- Host memory remains generally tight on this machine (recovered to ~1.8 GB
  free after this restore completed and Docker released memory back) -
  worth keeping in mind for any future heavy operation on this host, though
  no longer a blocker for Stage 1's own goal (Stage 1 itself is now
  complete).
- The two dev servers stopped in §2/§3 were not restarted by this Stage -
  they will need to be started again before any future G-OPS UI/E2E work
  resumes (unrelated to this Stage's own scope).
- This container now holds a **complete** copy of real Production data
  (41/41 tables, ~2.2M total rows across the counted tables alone) -
  correctly isolated (no G-OPS/Legacy Demo/Portal/Production/UAT
  connection at any point), but it is real data and should continue to be
  treated with the same care as Production itself for as long as it exists
  locally.

## 16. Stage 2 Readiness

Restore is complete and verified (41/41 tables, all 8 required core tables
present, dedicated read-only audit user created and SELECT-verified).
Per this task's own explicit instruction, **Stage 2 (Business Data Audit -
Supplier×Brand analysis, PO pattern analysis, or any relationship/
distribution analysis) was not started**, regardless of this successful
completion.
