# G-OPS Stage 5J Addendum: Production Batch Schedule Confirmation & Refresh Timing Finalization

AUDIT / DESIGN UPDATE ONLY. No code, migration, scheduler, Read Model,
Dashboard, or Brand List change was made in this Addendum. Stage 5J
(`gops-stage5j-dashboard-read-model-refresh-architecture.md`) is accepted
as-is and not re-litigated except where this Addendum's new Production
evidence directly supersedes a specific Stage 5J finding (§9's "15分周期
unconfirmed" and §21's Q-A/Q-D, both explicitly revisited below). Baseline:
commit `19895ec` (Stage 5J). This Addendum's own source-code cross-checks
(FormulaParser, `DashboardCandidateInputsQuery.sql`, batch class hierarchy,
repository-level table access, repo-wide `@Scheduled` search) were read
directly from `phasep-gulliver/` and `backend/` in this session - no
Production Snapshot database connection was available or used this
session; all Production Batch Schedule facts in §1 are taken as reported
by Ernest, not independently re-measured.

## 0. Baseline / Purpose

Stage 5J's architecture (§12 of that Stage, restated in §12 below) is
unchanged and is not reopened. This Addendum's purpose, per instruction,
is narrower:

1. Fold newly-provided Production Batch Schedule evidence (Windows Task
   Scheduler, from Ernest) into the record.
2. Map each confirmed Batch to the Legacy source classes/tables already
   audited in Stage 5D/5I/5J.
3. Decide a concrete Refresh cadence and offset.
4. Close or keep-open RC-D (`FormulaParser` `[AT]`/`[AJ]`) using Ernest's
   new clarification.
5. Issue a final Stage 5K go/no-go judgment.

## 1. New Production Evidence — Batch Schedule

Recorded verbatim as reported (Windows Task Scheduler, Production
G-SYS) - **Operational evidence, not repository Source**. No `.bat` file
matching any of these names exists anywhere under this repository
(`phasep-gulliver/` or the root repo) - confirmed by an explicit
`find -iname "*.bat"` sweep of `phasep-gulliver/`, which found exactly one
unrelated file (`gulliver/create_package.bat`, a Maven packaging script,
no relation to these jobs). This is consistent with the batches being
Windows Task Scheduler jobs that invoke this project's own Batch `.jar`
(each Legacy Batch class here is a standalone Spring Boot
`CommandLineRunner` with its own `main()`, confirmed in §2) - the
scheduling wrapper itself is Infrastructure/Operations-layer, not
something ever committed to Source, matching every prior Stage's finding
on this point.

| Task | Start | Repeat | Duration | Notes |
|---|---|---|---|---|
| `G-15MinutesBatch` (`15MinuteBatch.bat`) | 05:35 | every 15 min | 14h (→ last tick 19:35) | Bulk Change ID, Product Description Import, Bulk Delete Item, Initial PO, Official PO, WH ETA, Summary of Cargo, Send Mail |
| `G-PO Import Batch` | 05:25 | every 15 min | 14h (→ last tick 19:25) | Named separately from `G-15MinutesBatch` in Task Scheduler despite `15MinuteBatch.bat`'s own item list already including Initial PO/Official PO - see §1.1 discrepancy |
| `G-1Hour` | 05:50 | every 1h | 16h (→ last tick 21:50) | Task Scheduler evidence for the hourly batch |
| `HourlyBatch_1.bat` (Ernest's textual summary) | - | **every 2 hours** per Ernest's summary | - | Credit PO, Moving Average Cost Import, Price Import, Item Color Import, Stock In Report, New Item Bulk Registration, Stock List Download, Stock List Download Outlet - see §1.1 discrepancy |
| `3HourBatch(12pm)` | 12:00 | - | - | `3HourlyBatch.bat` - EC Item Update |
| `3HourBatch(15pm)` | 15:00 | - | - | same |
| `3HourBatch(18pm)` | 18:00 | - | - | same |
| `3HourBatch(21pm)` | **21:18** | - | - | Recorded exactly as reported - not "corrected" to 21:00 (see §1.1) |
| `DailyBatch.bat` | 05:00 | - | - | Logizero Download & Import, Tempostar Download & Import, Month End Stock Update, PO Plan |
| `DbBackUp.bat` | 23:59 | - | - | Database backup |
| `CheckLogizeroInventory` | 05:30 | every 30 min | 2h (→ 07:30) | |
| `CheckProductCSV` | 07:30 | every 15 min | 45 min | **Currently Disabled** |
| `G-Month End Stock Purge` | 04:05 | - | - | |
| `G-Send Mail Purge` | 04:05 | - | - | Fires the same minute as the two other 04:05 Purge jobs - see §4 |
| `G-WK_CPO Purge` | 04:05 | - | - | |
| `G-StocklistDownload` | 05:00 | every 2h | 16h (→ 21:00) | |
| `Latest_Stocklist_Download_Scheduler` | Ready (trigger not visible in supplied screenshot) | - | - | Not plotted in §4 - no confirmed trigger |
| `PriceChangeDownloadSecondBatch` | 07:00 | - | - | |
| `StockQtyDiscrepancyChecker` | 08:00 | - | - | |
| `InvTempostarStkUpload` | 20:45 | - | - | |

### 1.1 Discrepancies — Recorded, Not Silently Reconciled

Per instruction, these are **not resolved by assumption**:

1. **`HourlyBatch_1.bat` = "every 2 hours" (Ernest's text) vs. `G-1Hour` =
   "every 1 hour" (Task Scheduler screenshot).** §2's source trace found
   no code-level evidence that distinguishes an "hourly" job list from a
   "2-hourly" job list - both names point at the same operational concept
   (a periodic multi-item Import batch run), and only one Task Scheduler
   entry (`G-1Hour`) was supplied as visual evidence. **Cannot be proven
   from Source or from the evidence supplied.** Recorded as an open
   confirmation item: is `HourlyBatch_1.bat` = `G-1Hour` (and Ernest's "2
   hours" is a description error), or are these two distinct schedules
   (in which case the 2-hour one has no Task Scheduler screenshot in
   evidence)? This matters directly for §4/§6 - it changes how often
   `MsPriceListImportBatch`/`MsMoveAveCostImportBatch`/
   `PrCreditPoImportBatch`/`MsStkListDownloadBatch` (§2) actually run.
2. **`G-PO Import Batch` (05:25, every 15 min) vs. `G-15MinutesBatch`
   (05:35, every 15 min) both existing as separate Task Scheduler
   entries, while `15MinuteBatch.bat`'s own item list already includes
   "Initial PO"/"Official PO".** Two readings are both consistent with
   the evidence: (a) `G-PO Import Batch` is a second, independent
   15-minute job specifically for PO Import that duplicates/supersedes
   the PO-related items already listed under `15MinuteBatch.bat`, offset
   10 minutes earlier; or (b) `15MinuteBatch.bat`'s own item list is a
   historical/approximate description and PO Import has since been split
   into its own dedicated Task. **Not reconciled from evidence supplied**
   - recorded as a second open confirmation item. Practically, for §4's
   timeline this does not change much (both are 15-minute jobs touching
   the same PO tables either way), but it does mean **the true
   PO-related-write cadence during business hours may be two interleaved
   15-minute cycles (effectively touching `tr_po`/`tr_po_dtl` roughly
   every 5-10 minutes), not one** - relevant to §9.
3. **`3HourBatch(21pm)` starts at 21:18, not 21:00**, unlike the other
   three 3-hour ticks (12:00/15:00/18:00 exactly). Recorded verbatim, not
   assumed to be a typo - could reflect a deliberate stagger to avoid a
   21:00 collision with `G-1Hour`'s own last tick region, or could be an
   unrelated one-off manual edit to that Task's trigger. Not resolvable
   from the evidence supplied.

## 2. Source-to-Schedule Mapping

Direct source trace of `phasep-gulliver/gulliver/src/main/java/jp/ne/glv/batch/`
(41 classes matching `*Batch*`, confirmed by `grep -r "class \w*Batch"`).
Every class in this package is a standalone Spring Boot
`CommandLineRunner` (`extends AbstBatch`/`AbstImportBatch`, its own
`public static void main(String[])` calling
`executeRun(ThisClass.class, args)` - **no shared dispatcher, no
`@Scheduled`, confirmed by a fresh repo-wide
`grep -r "@Scheduled" phasep-gulliver/` returning zero matches**, matching
Stage 5D/5I/5J's own repeated finding exactly. **Deployment mechanism, source-confirmed, not inferred**: each Batch class
is packaged as its own standalone jar via a root-level
`batch_pom_<ClassName>.xml` Maven descriptor (33 such files exist at
`phasep-gulliver/gulliver/`, one per Batch, confirmed by direct listing),
and `AllUsersRestController.java:578-587` (`tempostarImportRun()`) shows
the concrete invocation pattern for at least one of them - a `ProcessBuilder`
shelling out to `java -jar InvTempostarStkUploadBatch.jar` from a working
directory `C:\g-sys-batch` (line 579-580) that does **not exist inside
this repository** - it is an external deploy directory, confirming why no
`.bat` file for any of these Task Scheduler jobs is found on disk here
(§1). Each `.bat` file therefore almost certainly runs one
`java -jar <ClassName>.jar` per line item from that external directory,
sequentially, inside the same wrapper script - consistent with one `.bat`
covering several named "batches" (e.g. `15MinuteBatch.bat`'s 8 items).
Several Purge jobs (`MsMonthEndStockPurgeBatch`, `SysSendMailPurgeBatch`,
`WkCpoPurgeBatch`) are additionally reachable via an on-demand REST
endpoint in the same controller (lines 442-499) - worth noting since it
means those three could in principle also be triggered by the
application itself, not only by Task Scheduler, though no evidence this
session found suggests that actually happens in practice.

Evidence-basis legend: **Direct** = the batch class file itself
references the named repository/table; **Inherited** = the table is only
confirmed reachable via a shared base class (`AbstImportBatch` →
`MsCommRepository`; `AbstractMsItemImportBatch` → adds `MsItemRepository`,
`TrArrRepository`; `AbstractMsStandardImportBatch` → adds
`MsFormulaRepository`, `MsItemRepository`, `MsStkRepository`,
`TrArrRepository`, `TrBlDtlRepository`) and this Addendum did not trace
that subclass's own `execute()` body line-by-line to confirm the
inherited capability is actually invoked; **Not Found** = no class in
Source matches the named Batch at all.

| Batch (Task/item name) | Java class | Evidence | `ms_item` | `ms_stk` | `ms_formula` | `tr_po`/`tr_po_dtl` | `tr_arr` | `ms_comm` |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|
| Bulk Change ID | `MsItemMasterIdBulkChange` | Direct | R/W | R/W | - | R | R/W | R |
| Product Description Import | `PrProductDescriptionImportBatch` | Direct | R/W | - | - | - | - | R |
| Bulk Delete Item | `MsItemMasterBulkDelete` | Direct | R/W (`DEL_FLG`) | R/W | - | R | - | R |
| Initial PO | `PrInitialPoImportBatch` | Direct | R | R/W | - | R/W (Delete&Recreate) | R/W | R |
| Official PO | `PrOfficialPoImportBatch` | Direct | R | R/W | - | R/W (Delete&Recreate) | R/W | R |
| WH ETA | `PrEtaWhMailBatch` | Direct | R | - | - | - | R/W (`ETA_WH_SEND_FLG`) | R |
| Summary of Cargo | `PrBLInvImportBatch` (**confirmed** - literal string `"Summary of Cargo ID:"` at `PrBLInvImportBatch.java:2033`, part of a generated mail/report body) | Direct | R | R/W | - | R/W | R/W | R |
| Send Mail | `SysSendMailBatch` | Direct | - | - | - | - | - | R (recipient list only, `SYS_SEND_MAIL` table not in this KPI set) |
| Credit PO | `PrCreditPoImportBatch` | Direct | R | R/W | - | R/W | R/W | R |
| Moving Average Cost Import | `MsMoveAveCostImportBatch` | Inherited only (`AbstractMsItemImportBatch`: `ms_item`, `tr_arr`, `ms_comm`) - **no direct `MsStkRepository`/`MsFormulaRepository` reference found; likely writes a cost field on `ms_item` or `ms_stk` but this Addendum could not confirm which without deeper trace** | R/W (likely) | Unconfirmed | - | - | Inherited | R |
| Price Import | `MsPriceListImportBatch` | Direct | R | R/W | - | R/W | R/W | R |
| Item Color Import | `MsItemColImportBatch` | Inherited only (`AbstractMsStandardImportBatch`: `ms_item`, `ms_stk`, `ms_formula`, `tr_arr`, `ms_comm`) - no direct repo reference in the subclass itself | Inherited | Inherited | Inherited | - | Inherited | Inherited |
| Stock In Report | `PrStkInReportImportBatch` | Direct | R | R/W | - | R/W (also auto-generates Credit PO from Stock-in discrepancy, Stage 5D §9 / `legacy-procurement-workflow-reverse-engineering.md`) | R/W | R |
| New Item Bulk Registration | `MsItemMasterBulkUpload` (name correspondence only - matched independently by two separate search passes this Addendum, `grep -i "new item\|bulk regist"`, but no explicit "New Item Bulk Registration" label string found in the file itself) | Direct | R/W | R/W | R/W | - | - | R |
| Stock List Download | `MsStkListDownloadBatch` | Direct (`MsFormulaRepository`/`MsItemRepository`/`MsStkRepository`/`TrPoRepository` all directly referenced) - **read-only confirmed**: no `.save(`/`.saveAll(`/`.delete(` call found anywhere in the file | R | R (not R/W - corrected from an earlier pass in this session that assumed write access) | R | R | - | R |
| Stock List Download Outlet | `MsStkListDownloadRemoteBatch` (**"Remote" assumed to mean "Outlet" - not textually confirmed; near-identical to `MsStkListDownloadBatch`, differs mainly in a default `isOutlet` flag**) | Direct (if correct) | R | R (read-only, same basis as above) | R | R | - | R |
| EC Item Update | `MsECItemAttributeImportBatch` | Inherited only (`AbstractMsItemImportBatch`) - no direct repo reference in the subclass itself | Inherited | - | - | - | Inherited | Inherited |
| Logizero Download & Import | `InvLogizeroStkDownloadBatch` + `InvLogizeroStkImportBatch` (two separate classes; a third pair, `MIMOSA_InvLogizeroStkDownloadBatch`/`MIMOSA_InvLogizeroTempostarStkUploadBatch`, also exists and was **not** distinguished from the non-`MIMOSA_` pair - both found, purpose of the `MIMOSA_` variant not traced this Addendum) | Direct | R (Import only) | R/W | - | - | - | R |
| Tempostar Download & Import | `SlTempostarImportBatch` (Sales/`SOLD_QTY`) + `SlTempostarDownloadBatch` (internals unconfirmed - `docs/legacy-stock-sales-data-reverse-engineering.md` §7 also left this one unread, filename-inferred as a Tempostar-download-family batch only) | Direct (`SlTempostarImportBatch`) | R | R/W (`SOLD_QTY`, clear-and-replace) | - | - | - | R |
| Month End Stock Update | `MsMonthEndStockUpdate` | Direct | - | R/W (`STK_QTY_LAST_MONTH`) | - | - | - | R (gated by `MS_COMM.VAL1` flag, `MON_PRC_STK_UPD` - **Source-confirmed structural anomaly, re-stated from the existing reverse-engineering doc**: no code anywhere sets this flag to `"UPDATE"`, so this step's actual real-world trigger frequency is unconfirmed even though the batch itself is scheduled daily) |
| PO Plan | `MsOrderJudgementImportBatch` (**dead code - see note**) | **Entirely commented out** - every line of the file, including `//package jp.ne.glv.batch;` at line 1 and the class declaration itself, verified directly this Addendum. Its still-active abstract parent, `AbstractMsOrderJudgementImportBatch`, would touch all six flagged tables if a concrete subclass existed, but none does today. **Operational risk, not resolved by this Addendum**: if Production Task Scheduler's "PO Plan" line item still points at a `MsOrderJudgementImportBatch.jar`, that job is either failing outright or has been a silent no-op for however long this class has been commented out - worth an explicit Ernest/Gulliver confirmation of whether "PO Plan" is still expected to do anything today | - | - | - | - | - | - |
| Database backup | Not a Java class - OS-level `mysqldump`/equivalent, outside this codebase entirely (consistent with `DbBackUp.bat` being an infra script, not an application Batch) | N/A | - | - | - | - | - | - |
| `CheckLogizeroInventory` | **Not Found** by this name; closest candidates are the `InvLogizero*` pair above, but no class named `CheckLogizeroInventory` exists | - | - | - | - | - | - | - |
| `CheckProductCSV` | **Not Found** - no matching class; also reported **Disabled** in Task Scheduler, so not currently active regardless | - | - | - | - | - | - | - |
| `G-Month End Stock Purge` | `MsMonthEndStockPurgeBatch` | Direct - repo check confirms **only** `WkStkRepository` referenced, no `MsStkRepository` - purges the `WK_STK` staging table, not `ms_stk` itself | - | - (purges `WK_STK` staging only, corrected from an earlier pass in this session that assumed `ms_stk`) | - | - | - | - |
| `G-Send Mail Purge` | `SysSendMailPurgeBatch` | Direct (by name match) | - | - | - | - | - | - |
| `G-WK_CPO Purge` | `WkCpoPurgeBatch` | Direct (by name match) | - | - | - | - | - | - |
| `StockQtyDiscrepancyChecker` | `StkQtyDiscrepancyCheckerService` | Direct | - | R (read-only check, `ms_comm`+`ms_stk`) | - | - | - | R |
| `InvTempostarStkUpload` | `InvTempostarStkUploadBatch` | Direct | - | R/W (Selenium push to Tempostar **and** Logizero admin screens per the existing reverse-engineering doc, §7) | - | - | - | R |
| `PriceChangeDownloadSecondBatch` | `SlTempostarPriceChangeDownloadSecondBatch` | Direct (by name match, and independently confirmed in the existing Tempostar reverse-engineering doc §7 table) | - | - | - | - | - | R |
| `Latest_Stocklist_Download_Scheduler` | Same family as `MsStkListDownloadBatch`/`MsStkListDownloadRemoteBatch` (unconfirmed which one) | Unconfirmed | - | - | - | - | - | - |

**Key question answered**: after which jobs can the Dashboard
Legacy-derived KPIs (`candidateCount`/`outOfStockCount`/
`longTermOutOfStockCount`, sourced from `ms_item`, `ms_stk`, `ms_formula`,
`tr_po`/`tr_po_dtl`, per Stage 5I §11) actually change? **Every job in the
15-minute and PO-Import columns, plus Credit PO/Price Import/Stock In
Report (hourly-or-2-hourly per §1.1's unresolved ambiguity), Logizero and
Tempostar Daily jobs, and Month End Stock Update** all write to at least
one of those five tables. The only jobs in this Batch Schedule confirmed
**not** to touch any Dashboard-KPI-relevant table are `SysSendMailBatch`
(mail queue only), the three Purge jobs (deletion of unrelated
audit/history tables), `DbBackUp.bat` (infra-level, no application table
writes), `StockQtyDiscrepancyChecker` (read-only), and Stock List
Download (+Outlet) (read-only, confirmed this Addendum - see §2/§3).

## 3. Refresh-Relevant Job Classification

| Class | Jobs |
|---|---|
| **A. DIRECTLY REFRESH-RELEVANT** (writes `ms_item`, `ms_stk`, `ms_formula`, `tr_po`/`tr_po_dtl`, or `tr_arr` - all five feed `calc4`'s inputs per §"2. Source-to-Schedule Mapping" and Stage 5I §11) | Bulk Change ID, Bulk Delete Item, Initial PO, Official PO, WH ETA, Summary of Cargo, `G-PO Import Batch`, Credit PO, Price Import, Stock In Report, New Item Bulk Registration (class-mapping confidence: name-correspondence only, §2), Logizero Download & Import, Tempostar Download & Import, Month End Stock Update |
| **B. INDIRECTLY REFRESH-RELEVANT** (touches related Master/state but via a path not confirmed to feed `calc4`'s specific inputs, or via `ms_comm` reads only) | Product Description Import (writes `ms_item` non-formula fields - display/description only, not confirmed to affect `calc4`'s numeric inputs), Item Color Import (`ms_item` attribute, same caveat), Moving Average Cost Import (writes a cost field, not confirmed part of `calc4`'s `FORMULA_11`-`14` inputs), `InvTempostarStkUpload` (writes `ms_stk` but as an outbound push *to* Tempostar/Logizero, not a Legacy-internal state change per se - though the underlying `ms_stk` row it reads to build that push could itself have just changed via one of Class A's jobs) |
| **C. NOT DASHBOARD-RELEVANT** | Send Mail, EC Item Update (`ms_item` EC-attribute only, not `calc4` input), Stock List Download (+Outlet) - **read-only confirmed this Addendum** (§2), cannot change any KPI input by construction, the three Purge jobs (confirmed this Addendum to purge `WK_STK`/`SYS_SEND_MAIL`/`WK_CPO` staging tables, not `ms_stk`/`ms_item` directly), Database backup, `StockQtyDiscrepancyChecker` (read-only diagnostic), `PriceChangeDownloadSecondBatch` (Tempostar price-change data, unrelated to `candidateCount`/stock KPIs per Stage 5I §11's own KPI table), `PO Plan` (dead code - see §2's `MsOrderJudgementImportBatch` entry; classified here only because it currently does nothing, not because it would be irrelevant if reactivated) |

Per instruction, a G-OPS Refresh is **not** scheduled merely because a
Batch exists in Class B or C - only Class A jobs motivate the Refresh
cadence decision in §6.

## 4. Daily Timeline / Collision Map (04:00-22:00)

Built directly from §1's reported start times/repeat intervals (no
Source needed for this section - pure arithmetic layout of the evidence
already given).

```
04:05  |####  <- G-Month End Stock Purge + G-Send Mail Purge + G-WK_CPO Purge
       |       (THREE Purge jobs fire at the exact same minute)
05:00  |####  <- DailyBatch.bat (Logizero/Tempostar Import, Month End Stock
       |          Update, PO Plan) + G-StocklistDownload (first of 9 ticks,
       |          every 2h to 21:00)
05:25  |###   <- G-PO Import Batch (first of 14h/15min ticks, :25/:40/:55/:10)
05:30  |###   <- CheckLogizeroInventory (first of 5 ticks, every 30min to 07:30)
05:35  |###   <- G-15MinutesBatch (first of 14h/15min ticks, :35/:50/:05/:20)
05:50  |####  <- G-1Hour (first of 16h/1h ticks, every :50) — same minute as
       |          CheckLogizeroInventory's 2nd tick (06:00 is CLI's tick, not
       |          05:50 - no exact overlap here, but adjacent)
       ================================================================
       ^^^^ 05:00-06:30: HIGHEST OVERLAP WINDOW ^^^^
       Every recurring Class-A job family (§3) has started at least one
       cycle by 06:05. Both 15-minute-cadence jobs (G-15MinutesBatch,
       G-PO Import Batch) are mid-cycle. This matches the instruction's
       own suspicion about 05:00-06:30 exactly.
       ================================================================
07:00  |##    <- PriceChangeDownloadSecondBatch (Class C, no KPI impact)
07:30  |##    <- CheckLogizeroInventory's last tick + CheckProductCSV would
       |          start here if not Disabled
08:00  |#     <- StockQtyDiscrepancyChecker (Class C, read-only)
       ----------------------------------------------------------------
       09:00-11:00: only the two 15-min jobs + G-1Hour + G-StocklistDownload
       (odd hours) continue - a comparatively QUIET period relative to
       05:00-08:00, though still not idle (15-min jobs never stop until
       ~19:25/19:35).
       ----------------------------------------------------------------
12:00  |#     <- 3HourBatch(12pm): EC Item Update (Class C)
15:00  |#     <- 3HourBatch(15pm): EC Item Update
18:00  |#     <- 3HourBatch(18pm): EC Item Update
19:25  |      <- G-PO Import Batch's LAST tick (14h from 05:25)
19:35  |      <- G-15MinutesBatch's LAST tick (14h from 05:35)
       ----------------------------------------------------------------
       19:35-20:45: the two 15-minute Class-A job families have both
       finished for the day. G-1Hour continues to 21:50. This is the
       first sustained daytime QUIET window for the highest-frequency
       Class-A jobs specifically.
       ----------------------------------------------------------------
20:45  |#     <- InvTempostarStkUpload (Selenium push, Class B)
21:00  |#     <- G-StocklistDownload's LAST tick
21:18  |#     <- 3HourBatch(21pm): EC Item Update (irregular time, §1.1)
21:50  |      <- G-1Hour's LAST tick
23:59  |#     <- DbBackUp.bat
```

**High-overlap periods**: 05:00-06:30 (confirmed, as suspected by
instruction), and secondarily 04:05 (three simultaneous Purge jobs,
though all Class C/no-KPI-impact).

**Likely quiet periods for Class-A jobs specifically**: 19:35 (or 19:25,
whichever 15-minute family truly stops first per §1.1's unresolved
overlap) through roughly 21:50 (`G-1Hour`'s last tick), and the full
overnight window from ~22:00 to 04:05/05:00 the next day - **no Batch in
this evidence set runs between `DbBackUp.bat` (23:59) and the 04:05 Purge
jobs**, a ~4-hour genuinely idle window.

**Recurring collision windows**: every hour, the two 15-minute Class-A
families collectively occupy 8 of each hour's 12 five-minute marks (`:05,
:10, :20, :25, :35, :40, :50, :55`, from `G-15MinutesBatch`'s `:35/:50/:05/
:20` pattern and `G-PO Import Batch`'s `:25/:40/:55/:10` pattern) for the
entire 05:25/05:35-19:25/19:35 span - this is a standing, all-day
collision pattern, not limited to the 05:00-06:30 window; 05:00-06:30 is
distinguished only by every *other* job family also starting there.

## 5. Production Capacity Constraint

The `c5d.2xlarge` (8 vCPU / 16 GiB, G-SYS + MySQL co-located, 40-55% CPU /
60-65% RAM under normal Batch+Stocklist operation) figures restated in
this Addendum's own instruction are the same figures already recorded in
Stage 5J §10, where they were noted as **Operational context provided
directly in that Stage's instruction, not independently verified
repository Source** - this Addendum found no additional confirmation or
contradiction of them and treats them identically: taken at face value,
not re-derived. Stage 5J's own caveat stands unchanged: whether that
40-55%/60-65% baseline reflects the 05:00-06:30 peak specifically, or only
a more typical hour, remains unmeasured (§10 below revisits this as part
of the still-open Q-C).

## 6. Determine Refresh Cadence

| Option | Evaluation against §1-4's new evidence |
|---|---|
| A. Every 15 minutes | Matches the *fastest* Legacy cadence exactly, but §8 below shows this is not motivated by any actual freshness need - would run the ~13.6s `calc4` Refresh up to 96 times/day for no confirmed business benefit, and would need to interleave with 8-of-12 Legacy five-minute marks every hour regardless of offset chosen (§4) |
| B. Every 30 minutes | **Recommended** (see §8) - halves Option A's Legacy-adjacent load while still refreshing well inside any plausible staleness tolerance; still needs an offset (§7) to avoid the standing 8-of-12-marks collision pattern |
| C. Every 60 minutes | Materially reduces load further, but starts to risk visible staleness if `candidateCount`-affecting POs/stock changes cluster in a single 15-minute window and a user checks Dashboard just before the next hourly tick - not recommended as the default without a Gulliver freshness answer first (§14) |
| D. Legacy-batch-aligned + offset | Not usable as the *sole* mechanism - confirmed again this Addendum that Legacy has zero `@Scheduled`/cron-equivalent code (§2), so "batch completion" still has no in-process signal to align to; the *offset* half of this idea is retained and folded into Option B/§7, not adopted as a standalone trigger |
| E. Manual Refresh | Retained as designed in Stage 5J §8/§17 (ADMIN-only), unaffected by this Addendum |
| F. Hybrid | **Recommended, same as Stage 5J**: B (fixed 30-min schedule) + a *simplified* form of C from Stage 5J's own §8 (see §9's revised recommendation below - a quiet-hours skip, not a per-tick DB scan) + E (manual override) |

## 7. Offset Analysis

§4 established the standing collision pattern: Legacy's two 15-minute
Class-A families occupy minute-marks `:05, :10, :20, :25, :35, :40, :50,
:55` of every hour, all day, whenever either interpretation of §1.1's
open questions is true (both readings still produce 15-minute-cadence
writes). The **unoccupied marks are `:00, :15, :30, :45`** - but `:00` is
also `G-StocklistDownload`'s own even-hour tick and (at 05:00) `DailyBatch`
's tick, so `:00` is not actually free during the 05:00-21:00 span either.

**Recommendation: offset G-OPS Refresh to `:07` and `:37` past each
hour** (i.e., 7 minutes after each half-hour boundary, for a 30-minute
cadence per §6's Option B). This lands outside every mark in §4's
standing pattern (`:05/:10/:20/:25/:35/:40/:50/:55`) and outside `:00`
(Stocklist/Daily), with a minimum 2-minute buffer on the nearest Legacy
tick (`:05` before, `:10`/`:20` after) in both directions.

**Caveat repeated from instruction, not assumed away**: actual per-batch
execution *duration* is not in the evidence supplied (only start
times/repeat intervals) - a `:05` tick job could still be running past
`:07` if it takes several minutes to complete, in which case this offset
would coincide with an in-progress Legacy write rather than a quiet gap.
Given InnoDB/MySQL MVCC means a concurrent Legacy `INSERT`/`UPDATE` does
not typically block a plain `SELECT` under default isolation, this is a
CPU/RAM-contention risk (§5), not a correctness risk to the Refresh's own
`calc4` read - but it is not provably safe from the evidence available.
Recommended offset is therefore explicitly conservative, not
mathematically optimal, pending confirmed batch-duration data.

## 8. Do We Actually Need Every 15 Minutes?

No - restating and now concluding Stage 5J §9's open question with this
Addendum's own evidence. Three independent facts converge on this:

1. **§4's timeline shows Legacy's 15-minute cadence is a standing,
   all-day pattern (8-of-12 marks/hour, 05:25/05:35-19:25/19:35), not a
   business-driven burst** - it is Legacy's own Import-polling interval
   for whatever files happen to be waiting in an Import Folder at that
   moment (per the existing Tempostar/Procurement reverse-engineering
   docs' own description of `AbstImportBatch`'s Import-Folder-polling
   design), not evidence that `candidateCount`-relevant *content* changes
   every 15 minutes in practice.
2. Stage 5I already established that `candidateCount` is driven by human
   PO/ordering-workflow cadence (~150/month cited in the original
   instruction, though that figure itself remains unconfirmed per Stage
   5I §2) - an order of magnitude slower than a 15-minute Import poll.
3. The ~13.6s `calc4` cost (Stage 5H, re-confirmed unchanged) at 96
   ticks/day (Option A) vs. 48 ticks/day (Option B) is a real, doubling
   difference in cumulative Legacy read-connection load for a KPI that,
   per point 2, plausibly does not need better than 30-minute freshness.

**Conclusion: 30-minute Dashboard freshness (§6 Option B) is the
Architecture recommendation.** This is an Architecture recommendation
only, not a Business Rule confirmation - reduced to the single Yes/No
question in §14 per instruction.

## 9. Source Change Detection Re-evaluation

Stage 5J §9 measured the `MAX(UPDATE_DATETIME)` pre-check at ~1.5-2.5s
across 7 tables and proposed it as an opportunistic skip-a-tick
optimization layered on the scheduled baseline. **This Addendum's new
evidence changes the calculus, not the number**: §4 shows Class-A jobs
write to at least one of the five `calc4`-input tables essentially
continuously from ~05:00/05:25 to ~19:25/19:35/21:50 (some job in the
two-15-minute-family pattern fires every 5-10 minutes across that entire
13-16 hour span). **During that span, a change-detection pre-check would
almost always find "something changed" and proceed to the full Refresh
anyway** - its skip-a-tick value is concentrated almost entirely in the
confirmed ~4-hour genuinely idle overnight window (`DbBackUp.bat` at
23:59 to the 04:05 Purge jobs, §4) plus whatever margin exists around
19:35-04:05 more broadly.

**Revised recommendation: Option B from the instruction's own list -
remove the per-tick `MAX(UPDATE_DATETIME)` scan from the initial Stage 5K
implementation.** Its ~1.5-2.5s cost, paid on every scheduled tick to
save a full Refresh only during a narrow overnight window, is not
justified now that the daytime "always busy" pattern is confirmed rather
than assumed. A materially simpler substitute captures the same benefit
without any per-tick DB scan: a **static quiet-hours configuration**
(e.g. skip scheduled ticks between ~22:00 and ~04:30, a config value, not
hardcoded) - zero query cost, and directly justified by §4's own
confirmed idle window, rather than inferred from an unindexed table scan
whose semantics (§9 of Stage 5J: does `UPDATE_DATETIME` mean "content
changed" or merely "a batch touched this row") were already flagged as
unconfirmable from data alone. If Gulliver/Ernest later confirms
`UPDATE_DATETIME` reflects genuine content changes, per-tick Change
Detection can be reconsidered as a Stage 5L+ optimization - not blocking
Stage 5K.

## 10. RC-D `[AT]`/`[AJ]` — New Confirmed Evidence

Ernest's clarification, recorded verbatim:

- `[AJ]` is Legacy formula data still stored in the database, representing
  the **same value** as `[AT]`: Stock Standard Quantity.
- `[AJ]` is the earlier Excel column position, before the Stock Standard
  column was moved to `AT`.
- Current Stock List calculation logic has no `[AJ]` handling.
- `[AJ]`-only formulas are **not currently effective** in Stock List
  calculation.
- Active Stock List calculation should be treated as `[AT]`-based.
- Nearly all `[AJ]` items were created/last-updated in 2019 (2,515 rows)
  or 2021 (1 row).
- All items with `[AJ]` under `FORMULA_11` have been **soft-deleted**.
- These formulas are used for Stocklist export/download and are not
  parsed by current formula logic for active items.

**Cross-check against this Addendum's own source reads (not merely
restated - independently verified this session):**

1. `FormulaParser.java` (`phasep-gulliver/gulliver/src/main/java/jp/ne/glv/
   utilities/FormulaParser.java:25-26`) confirms `PATTERN_AT_MULTIPLIER` /
   `PATTERN_AT_MULTIPLIER_DECIMAL` are hardcoded to `\[AT\]\*(\d+)/2` -
   **no `[AJ]` pattern exists anywhere in this class**, matching Ernest's
   "no `[AJ]` handling" statement exactly.
2. `DashboardCandidateInputsQuery.sql` (`backend/src/main/resources/
   legacy/DashboardCandidateInputsQuery.sql:51-60`), the query that feeds
   `candidateCount`'s `calc4` population, joins `ms_formula` **after**
   filtering `ms_item` to `WHERE (i.del_flg IS NULL OR i.del_flg = 0)`
   (line 60). **If Ernest's "all `[AJ]` items are soft-deleted" is
   accurate, every `[AJ]`-pattern `FORMULA_11` row is structurally
   excluded from Dashboard/`candidateCount`'s population before
   `FormulaParser` ever sees it** - not merely tolerated per Stage 5J
   §16's existing graceful degrade, but never reached at all in this
   specific, highest-traffic code path.
3. `RecommendedQtyReadQuery.sql` (line 138) has a second, narrower
   `del_flg` exception: `WHERE (:includeDeleted = TRUE OR i.del_flg IS
   NULL OR i.del_flg = 0)`. Tracing `includeDeleted=true`'s only caller
   (`LegacyStockReadRepository.java:111-117`, a `findBySkus` lookup for
   **specific, individually-requested SKUs** - e.g. SKU Detail or a Draft
   Order referencing a since-deleted item, per that method's own comment)
   confirms this is the *only* code path where a soft-deleted item's
   `FORMULA_11` could still reach `FormulaParser` and produce the
   previously-observed `FormulaParser ERROR` log lines (Stage 5J's
   introduction cites these logs as evidence the error is already
   tolerated). **This reconciles a fact that would otherwise look
   contradictory**: the errors are real and were really observed, but
   they come from single-SKU lookups of specific (possibly deleted)
   items, not from the bulk Dashboard/candidateCount population Ernest's
   answer is about.
4. **Independent corroboration of Ernest's row counts**: Stage 5D §8's
   own pattern classification of the 2,593 non-`[AT]` `FORMULA_11` rows
   found `888 + 841 + 659 + 77 + 51 = 2,516` rows matching the `[AJ]`
   family (`[AJ]*1/2` through `[AJ]*4/2` plus the decimal variant).
   Ernest's count - `2,515` (2019) `+ 1` (2021) `= 2,516` - **matches
   this static-formula-text classification exactly**, despite the two
   figures coming from independently different evidence (Stage 5D:
   regex-pattern classification of formula text; Ernest: a live-DB
   row-count grouped by update year). This is strong corroborating
   evidence, not a coincidence worth ignoring.

## 11. RC-D Decision

**RC-D = CLOSED. NO CODE CHANGE REQUIRED.**

Reasoning, precise:

- `FormulaParser` remains `[AT]`-based, unchanged, exactly as Stage 5J
  §17 already designed around (no modification proposed or made there
  either).
- Ernest's answer confirms Legacy's *own* live system also has no working
  `[AJ]` handling in current Stock List calculation - this was the one
  fact Stage 5D §8 explicitly could not determine from a ported-class
  read alone ("whether real Legacy G-SYS's own live system correctly
  handles the `[AJ]` variant... cannot be determined from source alone").
  **Now answered: it does not.** `FormulaParser`'s `[AT]`-only behavior is
  therefore not a G-OPS-side gap relative to Legacy - it is a faithful
  port of Legacy's own current (and, per Ernest, intentional) limitation.
- §10 point 2's SQL trace adds a fact beyond what Ernest's answer alone
  states: even independent of *whether* `[AJ]` should be supported, the
  soft-deleted status of every `[AJ]` row means Dashboard/`candidateCount`
  - the KPI this entire Stage 5J/Addendum sequence is about - **never
  evaluates one of these rows today**, by construction of the existing
  `del_flg` filter, not by luck.
- Do not add `[AJ]` support merely because old rows exist, per instruction
  - and per Ernest's own answer, doing so would not even change any
  currently-active SKU's behavior, since none of the affected rows belong
  to a non-deleted item.
- No `FormulaParser` semantic change, no Read Model schema change (Stage
  5J §17's `formula_error_count` observability field remains useful as
  designed - it will simply stay near zero for the Dashboard/Refresh path
  specifically, consistent with §10 point 3's finding that errors there
  come from a different, single-SKU code path).

## 12. Read Model Architecture — Reconfirmed

Stage 5J's architecture (§5-§7, §12-§13, §22 of that Stage) is unchanged
by this Addendum:

```
G-SYS Production DB
       |
       | SELECT ONLY
       v
G-OPS Background Refresh  (interval: 30 min, §6 — offset :07/:37, §7 —
       |                    quiet-hours skip ~22:00-04:30, §9 — no
       |                    per-tick MAX(UPDATE_DATETIME) scan, §9)
       | unchanged calc4
       | full correct population (~47,280 SKU, unchanged, §"2. Fixed
       |                          Stage 5I Findings" of Stage 5J)
       v
Portal DB Read Model
       |
       +-- Overall Legacy Aggregate
       |
       +-- Brand Legacy Aggregate
       |
       v
Atomic version publication (refresh_version pattern, Stage 5J §7,
                             unchanged)
```

Dashboard/Brand List request path: unchanged from Stage 5J §12/§13 - zero
Legacy DB queries, zero `calc4` invocations at request time.

## 13. Proposed Operational Behavior

| Parameter | Value | Basis |
|---|---|---|
| Refresh schedule | Every 30 minutes | §6/§8 |
| Refresh offset | `:07` and `:37` past each hour | §7 |
| Quiet-hours skip | ~22:00-04:30 (config value, not hardcoded) | §9, §4's confirmed idle window |
| Source Change Detection (per-tick) | **Not implemented in Stage 5K** (was a Stage 5J proposal; superseded by the quiet-hours skip, §9) | §9 |
| Single-flight | Postgres advisory lock + DB unique-index backstop | Stage 5J §12, unchanged |
| Timeout | Unchanged from Stage 5J §12 - specific value not fixed by this Addendum (no new evidence bears on it) | Stage 5J §12 |
| Retry | None - failed tick waits for next scheduled tick | Stage 5J §16, unchanged |
| Failure behavior | Last-known-good, `dashboard_aggregate_current` untouched | Stage 5J §7/§16, unchanged |
| Startup behavior | One-time bootstrap refresh if `dashboard_aggregate_current` has no row | Stage 5J §15, unchanged |
| Manual ADMIN refresh | Retained, unchanged | Stage 5J §8 Option E/§17-18 |
| Stale warning threshold | 3x the configured interval = 90 minutes (relative, per Stage 5J §14's own formula, now with a concrete interval to apply it to) | Stage 5J §14 + §6 of this Addendum |

## 14. Gulliver Confirmation

Per instruction, do not re-ask anything §1-§11 already answered (Batch
schedule existence, 15-minute cycle existence, `[AJ]` meaning). One
question remains, exactly as the instruction's own example phrases it:

> **G-OPSの発注候補Dashboardについて、G-SYS更新後、最大30分程度の反映遅延を
> 許容する運用で問題ないでしょうか？**

This single Yes/No question resolves §6/§8's interval choice (Stage 5J
Q-B) and, by extension, §13's Stale Warning threshold. §1.1's two
recorded discrepancies (`HourlyBatch_1` 1h-vs-2h; `G-PO Import Batch`
vs. `15MinuteBatch.bat`'s own PO items) are **not** escalated as
additional blocking Gulliver questions - per §3's classification, neither
affects the 15-minute-family collision pattern §4/§7 already account for,
so resolving them would refine §2's table precision but does not change
the §6/§7/§9 architecture decisions.

## 15. Stage 5K Implementation Scope Update

Restating Stage 5J §23 with this Addendum's changes only:

- **Migration**: unchanged (`V34__dashboard_read_model.sql`, Stage 5J §6).
- **Scheduler**: `@Scheduled` cron/fixed-rate expression now concretely
  proposable - `:07`/`:37` past each hour, every 30 minutes, gated by the
  quiet-hours config window (§9/§13) - **no Source Change Detection
  pre-check step** (removed from Stage 5K scope per §9; Stage 5J's
  original scope item for this is now deferred, not implemented).
- **RC-D**: removed from Stage 5K's list of open blockers entirely (§11 -
  closed, no code change). Stage 5J §17's `formula_error_count`
  observability field is unaffected and still recommended as designed.
- Every other Stage 5K scope item from Stage 5J §23 (Entities/
  Repositories, Refresh Service, `DashboardService` read-path rewrite,
  Manual Refresh endpoint, UI Staleness display, test/E2E minimums) is
  **unchanged** by this Addendum.

## 16. Acceptance Criteria — Reconfirmed

Stage 5J §20's list stands unchanged, plus one addition motivated by this
Addendum's own findings:

- Dashboard/Brand List: <=1s target, 0 request-time `calc4` invocations
  (unchanged).
- Refresh: full 47,280-SKU population, unchanged `candidateCount`
  semantics, 0 false exclusion, 0 partial publication, 0 concurrent
  refresh, 0 Legacy writes (all unchanged).
- **New**: `formula_error_count` on any Dashboard/Brand-List-triggered
  Refresh run should be **0 or near-0** in steady state, per §11's
  finding that the Dashboard population path structurally excludes every
  known `[AJ]`-pattern row today - a non-zero, *growing* count here in
  production would be a signal worth investigating (e.g. a newly-created,
  non-deleted SKU somehow acquiring an `[AJ]`-pattern formula), not
  expected but not designed as a hard gate either.

## 17. Final Judgment

**B. READY AFTER ONE GULLIVER FRESHNESS CONFIRMATION**

- The architecture itself (unchanged from Stage 5J) remains fully
  specified and implementable.
- Production Batch Schedule (Stage 5J Q-A) is now substantively answered
  by §1-§9: a concrete cadence (30 min), offset (`:07`/`:37`), and
  simplified Change-Detection posture (quiet-hours skip, not a per-tick
  scan) are all derived from real Operational evidence, not placeholders.
  Two narrow discrepancies remain unreconciled (§1.1) but do not block
  §6/§7/§9's conclusions (§14).
- RC-D (Stage 5J Q-D) is now **CLOSED** (§11) - one of Stage 5J's four
  listed Gulliver questions is fully resolved and removed from the open
  list.
- What remains blocking is exactly what §14 states: **one Yes/No
  freshness confirmation** (Stage 5J Q-B, now with a concrete 30-minute
  proposal to confirm or reject, rather than an unbounded question).
  Stage 5J's own Q-C (heavy-batch-avoidance) is substantially informed by
  §4/§5 (the 05:00-06:30 window is now concretely identified) but the
  underlying EC2 load-during-that-specific-window measurement remains
  unconfirmed - not re-escalated as a separate blocking question per §14,
  since the chosen offset (§7) already avoids the worst of it by
  construction, but worth Gulliver/Ernest's awareness if §14's answer is
  "no, need faster than 30 minutes" and a re-evaluation of Option A/C
  becomes necessary.

## 18. Stop

Per instruction: this Addendum is complete. No code, Flyway migration,
scheduler implementation, Read Model implementation, Dashboard change,
Brand List change, or Production connection change was made. Not
proceeding to Stage 5K. Awaiting ChatGPT Tech Lead review.
