# G-OPS Official PO / PO Number — Final Reality Audit

**Status: AUDIT ONLY. No implementation.** G-OPS functional code change: 0. Legacy G-SYS change: 0. Production connection: 0. Production write: 0. UAT connection: 0. Snapshot write: 0 (all analysis via a SELECT-only account, verified below).

**Date**: 2026-09-23. **Author**: Claude Code (SEPG), per Techlead (ChatGPT) instruction relayed by the user.

---

## Required Numerical Summary

| Metric | Value |
|---|---|
| Total `TR_PO` | 8,376 |
| Legacy-shape (parses to a real Supplier **and** real Brand match) | 8,374 / 8,376 = **99.98%** |
| 3+3+3-shape (BR-08, no separator, 9 chars, e.g. `ABCXYZ001`) | **0 / 8,376 = 0%** |
| Other (neither — the 2 anomalous placeholder records) | 2 / 8,376 = 0.02% |
| Recent 12m — Legacy-shape match | 1,651 / 1,651 = **100%** |
| Recent 12m — 3+3+3-shape | 0 / 1,651 = 0% |
| Recent 6m — both match | 1,067 / 1,067 = 100% |
| Recent 3m — both match | 583 / 583 = 100% |
| Parser Supplier Master match | 8,374 / 8,376 = 99.98% |
| Parser Brand Master match | 8,374 / 8,376 = 99.98% |
| Both match | 8,374 / 8,376 = 99.98% |
| Parse failure (length < 11) | 1 / 8,376 = 0.01% |

---

## 2. Scope / Safety

READ ONLY throughout. `SELECT`-only against an isolated, local Docker copy of the Production Snapshot (`gsys-prod-snapshot-20260916-ci`, port 33200) restored from the untouched source dump `.local/prod-snapshot-20260916/goo_20260916.dmp` (never modified). No Production or UAT connection at any point. No write, schema change, migration, or index change to any database. No Legacy or G-OPS code change. `gsys-legacy-demo-mysql` and `gsys-prototype-postgres` were never touched.

**Environment note, in the interest of full transparency**: two full-restore attempts of this Snapshot were interrupted by host memory pressure (unrelated to this task's own resource use — see the session's own record for detail) and were not retried a third time per explicit instruction. Instead, the partially-restored container (23 of 41 tables present when the second attempt stopped) was inspected in place. **All tables this audit actually needs — `tr_po`, `tr_po_dtl`, `ms_comm`** — were already present and verified to **exactly** match the documented Production baseline (Stage 1C/5B: `tr_po`=8,376, `tr_po_dtl`=183,480, Supplier=602, Brand=1,504 — all four confirmed via live `COUNT(*)`, not the InnoDB `information_schema` estimate, which was checked first and found to differ slightly for large tables as expected). No selective/partial restore was therefore necessary. Tables not needed for this audit (`ms_stk`, `ms_stk_bk`, `his_stk_list`, `ms_item`, etc.) were left as-is, some incomplete — they are not used anywhere in this report's conclusions.

A dedicated `gops_snapshot_audit`@`%` account was created fresh (random password, generated for this new container instance, never extracted from any running process) with exactly `GRANT SELECT ON goo_prod_snapshot_20260916.*` — verified via `SHOW GRANTS`, and by live-testing that `INSERT`/`UPDATE`/`DELETE`/`ALTER`/`CREATE` are all rejected (`ERROR 1142`) while `SELECT` succeeds and the underlying data (`tr_po` count) is unaffected. The credential is stored only in `.local/prod-snapshot-20260916/audit.env` (confirmed covered by `.gitignore`'s `.local/` entry via `git check-ignore -v`). The root password used only to create this container/user was never written to any file and has been discarded.

---

## 3. Legacy PO Number Parser (Re-Verified)

`phasep-gulliver/gulliver/src/main/java/jp/ne/glv/utilities/BusinessLogicUtil.java`:

- `getSupplierCd(poNo)` (:1028-1036): `if (poNo==null) return null; if (poNo.length()<4) return null; return poNo.substring(0,4).toUpperCase();`
- `getBrandCd(poNo)` (:1045-1053): guard `length()<8`, returns `poNo.substring(5,8).toUpperCase()`.
- `getIdCd(poNo)` (:1061-1069): guard `length()<11`, returns `poNo.substring(9,11)` — **2 characters**, not 3 as its own Javadoc comment incorrectly states (the code, not the comment, is authoritative). All three return `null` on a too-short string; none throw.

**Call-site audit (re-confirmed)**: these getters are load-bearing, not vestigial, at multiple Legacy Import batches (`PrOfficialPoImportBatch`, `PrInitialPoImportBatch`, `PrCreditPoImportBatch`, `PrBLInvImportBatch`). In `PrOfficialPoImportBatch.java` specifically: PO No. is read from a fixed Excel cell (row 4, col J); `:375` rejects the file outright ("INVALID PO FORMAT") if `getIdCd()` returns null (i.e., PO No. shorter than 11 characters) — **before** any Supplier/Brand check runs; `:401-408` looks up the parsed Supplier code against the real `ms_comm`/`MS_SUPPL` master, rejecting ("SUPPLIER CODE IN PO# IS INVALID") on no match; `:411-420` does the same for Brand, additionally cross-checked against a separate Brand Name cell; `:938-939` then **writes** `trPo.setSupplierCd(supplierCd); trPo.setBrandCd(brandCd)` — i.e., **the PO No. is the actual data source for `TR_PO.SUPPLIER_CD`/`BRAND_CD`**, not an independent validation-only check.

---

## 4. Production Snapshot PO Number Population

Schema confirmed directly (`DESCRIBE`, not assumed): `TR_PO.PO_NO varchar(30)`, primary key. 8,376 rows, all non-null, all non-blank, all 8,376 distinct (no duplicates). `ORDR_DATE` ranges from a placeholder `1900-01-03` (44 rows carry a null/placeholder date — pre-real-data or draft artifacts, not investigated further as out of this audit's scope) to `2026-09-16` (the dump date).

---

## 5. Format Distribution

**Length distribution**:

| Length | Count | % |
|---|---|---|
| 11 | 6,749 | 80.6% |
| 19 | 1,269 | 15.1% |
| 12 | 197 | 2.4% |
| 20 | 75 | 0.9% |
| 13 | 80 | 1.0% |
| 10, 14, 24, 25 | 6 | <0.1% |

**Dash-count distribution**:

| Dash count | Count | % |
|---|---|---|
| 1 | 6,948 | 83.0% |
| 2 | 1,426 | 17.0% |
| 0 | 2 | 0.02% |

**Format clusters** (empirically derived shape signature: letters→`A`, digits→`9`, everything else kept literal — not clusters I guessed in advance):

| Shape | Count | % | First seen | Last seen | Last 12m |
|---|---|---|---|---|---|
| `#AAA-AAA999` | 6,512 | 77.7% | 1900-01-03 | 2026-09-16 | 1,519 |
| `#AAA-AAA999-#AAA999` | 1,161 | 13.9% | 2019-05-31 | 2026-09-16 | 77 |
| `#AAA-AA9999` | 237 | 2.8% | 1900-01-12 | 2026-09-09 | 23 |
| `#AAA-AAA999A` | 182 | 2.2% | 2018-08-13 | 2026-09-09 | 10 |
| `#AAA-AA9999-#AAA999` | 108 | 1.3% | 2019-05-31 | 2026-02-11 | 3 |
| `#AAA-AAA999-A` | 73 | 0.9% | 2017-09-28 | 2019-04-24 | 0 |
| `#AAA-AAA999A-#AAA999` | 42 | 0.5% | 2019-07-08 | 2026-03-18 | 3 |
| (10 smaller clusters, ≤14 records each) | 46 | 0.5% | — | — | — |
| **2 anomalous outliers** (see §9) | 2 | 0.02% | n/a | n/a | 0 |

**Reading the dominant shape**: `#ABC-DEF123` = `{4-char Supplier code, including its literal `#`}` + `-` + `{3-char Brand code}{3-digit serial}`. This is **not** a dash-separated `Supplier-Brand-Serial` triple with two dashes as a naive reading of the parser's substring offsets might suggest — it is a **single dash** separating a 4-character Supplier code (itself starting with `#`) from a concatenated 3-char-Brand + 3-digit-serial tail. The `#AAA-AAA999-#AAA999` cluster (13.9%) is the same base shape with a second `-#XXX999` segment appended — consistent with the Legacy source finding (prior Official PO audit) that the same Excel file tracks multiple invoices/arrivals against one PO over time; this is very likely a revision/invoice-tracking suffix, not a different base PO numbering scheme (its own leading segment is byte-for-byte the same `#AAA-AAA999` shape).

**BR-08's proposed shape** (`{Supplier3}{Brand3}{Serial3}`, no separator, 9 characters, e.g. `ABCXYZ001`) **does not appear in the format-cluster table at all** — it would require dash-count 0, and only 2 records (0.02%) have zero dashes, both confirmed anomalous (§9), neither matching BR-08's shape either.

---

## 6. Recent PO Format

| Window | Total | Dominant/parseable shape both-match | % |
|---|---|---|---|
| Last 12 months | 1,651 | 1,651 | **100%** |
| Last 6 months | 1,067 | 1,067 | **100%** |
| Last 3 months | 583 | 583 | **100%** |

No BR-08-shaped (3+3+3, no separator) PO exists in any of these recent windows either. The Legacy dash-based shape is not a legacy-only artifact fading out — it is the **exclusive, currently-in-use** format, and its dominant sub-shape's share has been *increasing*, not decreasing (see §14).

---

## 7. Supplier Segment Validation

Real Supplier Master (`ms_comm` where `CATE_ID='MS_SUPPL'`, 602 rows, confirmed exact match to baseline): **593 of 602 (98.5%) are exactly 4 characters, starting with a literal `#`** (e.g. shape `#XXX`). Only 9 (1.5%) are plain 3-character codes with no `#` prefix — pre-existing minor/legacy exceptions, not a competing convention at any real scale.

This is an exact structural match to what `getSupplierCd()`'s `substring(0,4)` extracts from the dominant PO No. shape (`#ABC-...` → `#ABC`). **The Legacy parser's Supplier segment and the real Supplier Master's code shape were built for each other** — this is not a coincidental overlap.

**On the 2026-09-17 rule's "Supplier abbreviation = 3 characters"**: no real Legacy Supplier code of this shape (plain 3 chars, no `#`) exists at any meaningful scale (9/602 = 1.5%, and even those don't correspond to what a PO No.'s Supplier segment actually contains, which is always 4 characters per the parser's own fixed `substring(0,4)`). G-OPS's own `OfficialPoShortCode` entity (confirmed in the source-only investigation) is a **separate, new, G-OPS-only, Admin-populated field** — it does not derive from, alias, or reuse the real Legacy Supplier code in any way. These are three genuinely distinct concepts that must not be conflated: **Legacy Supplier Code** (`#XXX`, 4 chars, existing, real), **G-OPS Official PO Short Code** (3 chars, new, Admin-assigned, no real-world precedent yet), and **Supplier display name** (unrelated, free text).

---

## 8. Brand Segment Validation

Real Brand Master (`ms_comm` where `CATE_ID='MS_BRAND'`, 1,504 rows, exact match to baseline): overwhelmingly exactly 3 characters, no prefix (sampled distribution across `S/M/B/C/A/T/R/P/K/L/G/F/D/H/N/O/W/E/J/V...` leading letters, all length 3). This **does** match both the Legacy parser's `getBrandCd()` (`substring(5,8)`, 3 chars) and BR-08's assumed "Brand略称3文字" — Brand is the one segment where the two proposed formats do not actually conflict.

---

## 9. ID Segment Reality

`getIdCd()` returns `substring(9,11)` — exactly **2 characters**, despite its own Javadoc comment claiming 3. For the dominant shape `#ABC-DEF123` (positions 0-10), this extracts only `"23"` from the 3-digit serial `"123"` — **the first digit of the visible 3-digit serial is silently dropped and never captured by `getIdCd()`**. Per the Legacy source investigation, the returned ID value has no dedicated master lookup and no `TR_PO` column write of its own (consistent with the prior finding that G-SYS gives the ID segment no independent business meaning) — its only confirmed use at Import time is the null-check gate (§3), not the actual 2-character value. This 3-digit-vs-2-character discrepancy is a pre-existing Legacy quirk, not something this audit's format question turns on.

**The two anomalous outlier records** (§5's "2 anomalous" row): `PO_NO` values shaped like `_P_190213171249_#VNX_002` and `_P_190415171324_#NHK_002` — 24 characters, underscore-delimited, containing what looks like a timestamp and an embedded real-looking Supplier code (`#VNX`, `#NHK`) deep inside the string, both with **`ORDR_DATE = NULL`**. These read as unfinished/draft/placeholder artifacts (the leading `_P_` plus a 12-digit timestamp is not consistent with any real issued-PO convention seen elsewhere in the other 8,374 records) rather than a second legitimate PO numbering scheme. They are the **only** two records in the entire 8,376-row table with zero dashes, and neither matches BR-08's shape either — they are simply malformed/incomplete, not evidence of any alternate real format.

---

## 10. Header vs. Line Brand Analysis

**Schema fact, confirmed directly (not assumed)**: `TR_PO_DTL` has **no `BRAND_CD` or `SUPPLIER_CD` column at all** (`DESCRIBE tr_po_dtl`: `PO_NO, LINE_NO, ITEM_CD, SERIES, MODEL_NO, MODEL, COLOR, DESCRIPTION, QTY_PO, PRC_UNIT, AMT_LINE, DEL_FLG, ...` — no brand/supplier field). Only `TR_PO` (the header) carries `SUPPLIER_CD`/`BRAND_CD`, and per §3 those header columns are themselves written **from the PO No.'s own parsed segments** at Import time, not from any independent source.

**Consequence for the PO-No-embedded Brand question**: the PO No.'s Brand segment can only ever correspond to the **header-level** Brand — there is no "line Brand" column in the transaction data for it to alternatively mean. A genuine per-line Brand concept, if one exists at all, would have to be derived by joining `TR_PO_DTL.ITEM_CD` to `MS_ITEM.BRAND_CD` (the Item Master) — a fundamentally different, item-master-level concept, not a PO-transaction-level field.

**Scope limitation, stated honestly**: `MS_ITEM` was present in the partially-restored container inspected for this audit but was **not confirmed complete** (106,967 of the documented 116,842 baseline rows — the restore had not reached this table's full population before it stopped on an unrelated later table). Per this audit's own minimal-footprint instruction, `MS_ITEM` was **not** used for any conclusion in this report — the prior audit's own citation of "~2.7% of POs have a line Item Brand differing from the header Brand" (Stage 2) is **not re-verified here**; it stands as previously reported, not re-confirmed or contradicted by this Stage. If a future Stage needs a verified item-level Header-vs-Line comparison, it would need `MS_ITEM` fully and verifiably restored first — a distinct, scoped follow-up, not performed here.

---

## 11. Official PO Import Call Chain

(Re-confirmed from the source-only investigation, consistent with §3.)

1. PO No. is read from Excel, fixed cell coordinates (row 4, column J — `ROW_IDX_PO_NO=3, COL_IDX_PO_NO=9`, 0-indexed).
2. Max-length validation only (`isValidLength(poNoStr, 30)`) — no minimum-length or format/regex validation of its own; the **effective** minimum-length gate (11 characters) is an indirect side effect of `getIdCd()` returning null below.
3. `getIdCd(poNoStr) == null` → reject "INVALID PO FORMAT" (this is where a too-short PO No., including any 9-character BR-08-shaped one, is rejected — before Supplier or Brand is ever examined).
4. `getSupplierCd()` → looked up against `ms_comm`/`MS_SUPPL`; no match → reject "SUPPLIER CODE CAN NOT BE FOUND IN PO#" / "...IS INVALID."
5. `getBrandCd()` → looked up against `ms_comm`/`MS_BRAND`, **and** cross-checked against a separate Brand Name Excel cell; mismatch → reject "BRAND CODE IN PO# IS INVALID."
6. `getIdCd()`'s actual 2-character value: no further validation, no master lookup, no dedicated `TR_PO` column.
7. On success: `TR_PO.SUPPLIER_CD`/`BRAND_CD` are populated **from the same parsed values**, not from any other cell.

---

## 12. Current 3+3+3 Compatibility Test

Static/mechanical evaluation only (no code executed, no test artifact created or left in the repository, no data written) — tracing `ABCXYZ001` (BR-08's own example, 9 characters, 0 dashes) through the exact logic in §3/§11:

1. `isValidLength(poNoStr, 30)` → passes (9 ≤ 30).
2. `getIdCd("ABCXYZ001")` → `length() = 9 < 11` → returns `null`.
3. `:375` → **file rejected immediately: "INVALID PO FORMAT."**
4. Supplier/Brand validation (§11 steps 4-5) is **never reached** — the rejection happens at the very first structural gate.

**This is a hard, deterministic, 100%-reproducible rejection, not a silent misparse, wrong-match, or edge case.** Every single BR-08-shaped PO No. G-OPS could generate today would fail identically, for the same reason, every time.

Cross-checked against the real data (§5-§9): this mechanical conclusion is fully consistent with the empirical finding that **zero of 8,376 real historical POs** — across more than a decade of real Production use — have ever used this shape.

---

## 13. Downstream PO Number Dependencies

The same `getSupplierCd`/`getBrandCd`/`getIdCd` substring-parsing pattern recurs, independently, in `PrInitialPoImportBatch.java`, `PrCreditPoImportBatch.java`, and `PrBLInvImportBatch.java` (credit-note cross-referencing and B/L invoice brand/supplier derivation) — **not confined to the Official PO batch alone**. Any coexistence design (Decision Matrix §18) that changes PO No. shape only for Official PO Import, while leaving these other batches' identical parsing logic unchanged, would need each of these call sites individually re-verified against the new shape — they were not re-traced call-by-call in this Stage (out of scope; flagged here as a concrete, non-hypothetical follow-up item, not a vague caveat).

No substring-based parsing of `PO_NO` was found anywhere in the G-OPS Portal side beyond the already-known `OfficialPoNumberGenerator`/`OfficialPoShortCode`/`OfficialPoSequenceService` (§17) — G-OPS does not itself re-parse a PO No.'s internal structure anywhere.

---

## 14. Historical Format Change Analysis

| Year | Total POs | Dominant-shape count | % dominant | Avg. dash count |
|---|---|---|---|---|
| 2017 | 7 | 4 | 57% | 1.43 |
| 2018 | 205 | 154 | 75% | 1.22 |
| 2019 | 861 | 640 | 74% | 1.23 |
| 2020 | 989 | 685 | 69% | 1.29 |
| 2021 | 930 | 605 | 65% | 1.30 |
| 2022 | 846 | 585 | 69% | 1.27 |
| 2023 | 927 | 776 | 84% | 1.14 |
| 2024 | 980 | 889 | 91% | 1.07 |
| 2025 | 1,251 | 1,102 | 88% | 1.10 |
| 2026 (partial, through 09-16) | 1,332 | 1,264 | 95% | 1.05 |

**No clean "format changed on date X" cutover exists.** The base `#Supplier-BrandSerial` shape (with or without a revision/invoice suffix, §5) has been in continuous, real use since at least 2017, and its *dominant single-segment* variant's share has been rising steadily (57%→95%), not falling — the opposite of what a "Legacy is being phased out" narrative would predict. The declining average dash count over the same period reflects fewer of the multi-segment revision-suffixed POs recently, not any drift toward a no-dash format — dash count never approaches 0 in the aggregate (it bottoms out around 1.05, i.e. still essentially always ≥1 dash).

---

## 15. Real Official PO File Search (Re-Verified)

Re-confirmed, repository-wide (both `g-sys` and `phasep-gulliver`): **no customer-originated, pre-G-OPS Official PO Excel or PDF sample exists.** `backend/data/official-po/{demo,local,test}/` (4,512+ files) are all confirmed G-OPS-generated output — excluded from consideration.

**New finding this Stage**: git-tracked Legacy developer test fixtures do exist — `phasep-gulliver/gulliver/testfile/OfficialPO.xlsx` (and `initialPO.xlsx`/`initialPO2.xlsx`/`initialPO3.xlsx`), part of the Legacy repository's own development history (not G-OPS output). `OfficialPO.xlsx`'s own PO No. cell contains `dpk-01` (6 characters) — a real Legacy-side artifact, but **shorter than the batch's own 11-character minimum**, matching **neither** the dominant real-data shape **nor** BR-08's shape. It is not wired to any automated test and its authoritativeness as "the real format" is unconfirmed — noted here as a genuine data point, not resolved. Given the overwhelming real-`TR_PO`-data evidence above (99.98%/100% match to the dash-based shape), this single unreferenced dev fixture does not change this report's conclusion; it is flagged for completeness only.

---

## 16. Excel vs. PDF Directory Reality (Re-Verified)

Re-confirmed two independent ways: (1) no PDF-handling code (iText/PDFBox/FOP/etc.) anywhere in `phasep-gulliver/gulliver/src/main/java/jp/ne/glv/batch/`; (2) **no PDF library is even a Maven dependency** in `phasep-gulliver/gulliver/pom.xml` — not merely unused in one class, but structurally absent from the entire Legacy module. **Legacy G-SYS has no PDF capability anywhere in this codebase.** PDF generation/handling remains entirely a G-OPS-side (and/or human/offline) concern; nothing in source or data contradicts or extends the prior audit's finding here.

---

## 17. Current G-OPS Generator

`backend/src/main/java/com/glv/gsysportal/service/OfficialPoNumberGenerator.java`: `generate()` returns `supplierShortCode + brandShortCode + String.format("%03d", seq)` — **exactly BR-08's 3+3+3, no-separator shape**, already fully implemented and already in active use for G-OPS's own generation path.

- Supplier/Brand 3-char short codes come from a dedicated, **G-OPS-only** entity `OfficialPoShortCode` (table `official_po_short_code`), keyed to the existing Legacy Supplier/Brand code but holding an independently Admin-assigned 3-character value — Legacy's own `ms_comm` has no such column (re-confirmed, §7).
- Serial uniqueness/concurrency is handled correctly: `OfficialPoSequenceService` uses a single atomic `INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING` per `(supplierCode, brandCode)` pair — a real, race-free sequence, not an unguarded `MAX+1`.
- `OfficialPoFileNaming` (`OfficialPO_{Supplier}_{Brand}_{yyyyMMdd}_{PONo}_{Revision}.{ext}`) is self-labeled a working assumption; independently re-confirmed that the Legacy batch never parses the filename for any business value (§16-adjacent), so this naming pattern carries no Import Contract risk regardless of its own accuracy.
- **Compatibility verdict**: mechanically incompatible with Legacy Import as it stands today (§12) — a 9-character PO No. is rejected at the very first validation gate, before Supplier or Brand is ever checked.

---

## 18. Coexistence / Migration Options

| | **Option A**: keep Legacy format for G-OPS too | **Option B**: adopt 3+3+3, coexist with Legacy history | **Option C**: separate G-OPS internal number from the Official PO No. |
|---|---|---|---|
| Legacy Import compatibility | Full — matches the format Legacy's parser and 99.98%/100%-matching real data already expect | **Broken today** — every 3+3+3 PO No. is rejected at the first gate (§12); would require a Legacy-side code change (prohibited scope for G-OPS, and this audit found `PrOfficialPoImportBatch`/3 other batches all share the identical parsing logic — §13) | Not applicable to Official PO Import itself — the Official PO No. actually sent to Legacy would still need to satisfy whichever format Legacy requires |
| G-OPS impact | `OfficialPoNumberGenerator` needs to change output shape to `#Supplier(4,incl.#)-Brand(3)Serial(3digit)`; `OfficialPoShortCode` would need to store/derive the real 4-char `#XXX` Legacy Supplier code instead of (or alongside) a new 3-char abbreviation | None to the generator itself, but a real-world Legacy-side accommodation would be needed for these PO Nos to ever actually import | G-OPS already effectively has this distinction (Portal management number vs. Official PO No., per BR-07) — Option C only changes which number a Supplier-facing/Legacy-facing role plays |
| Legacy/G-SYS change required? | **No** | **Yes** (or the PO would simply never successfully import) | Depends entirely on what format is chosen for the Legacy-facing number — this option alone doesn't resolve the format question, it only clarifies which number plays which role |
| Historical compatibility | Perfect — matches 99.98% of 8,376 real historical POs, 100% of the last 12 months | None — 0 historical precedent (§5-§6) | Neutral — doesn't touch historical data either way |
| Supplier/Brand identification | Already validated against real Supplier (598/602 `#XXX`) and Brand (1,504, 3-char) masters, at scale, continuously since 2017 | Would need brand-new Admin-populated identification (already exists as `OfficialPoShortCode`, §17) with no historical continuity | Same as whichever underlying format is chosen |
| Revision/Invoice linkage | The `#AAA-AAA999-#AAA999` suffix pattern (13.9% of real data, §5) already demonstrates Legacy's own convention for tracking revisions/invoices against a base PO No. | Would need an entirely new revision-suffix convention invented from scratch | Neutral |
| Risk | Lowest — reuses a format proven at scale for a decade | Highest — guaranteed Import failure unless Legacy itself changes, which is out of this project's stated scope entirely | Medium — doesn't eliminate the core format question, just reframes which number needs to satisfy it |

**This audit's own observation** (not a Business Rule decision, which remains the customer's to make): Option A is the only one of the three that requires **zero Legacy change**, has **zero historical incompatibility**, and uses **already-existing, already-validated** master data structures (`#XXX` Supplier codes, 3-char Brand codes) that G-OPS's own `OfficialPoShortCode`/`OfficialPoSequenceService` infrastructure could be pointed at with a formatting change alone, not an architectural one.

---

## 19. Remaining Gulliver Questions

Reduced to exactly what source and Production Snapshot data together cannot settle:

1. **Given that Legacy's real, continuously-used PO No. format is `{4-char Supplier code, including its `#`}-{3-char Brand code}{3-digit serial}` (empirically confirmed: 99.98% historical match, 100% in the last 12 months, in active use through the most recent data in the Snapshot, 2026-09-16) — was the 2026-09-17 confirmed rule (`{Supplier3}{Brand3}{Serial3}`, no separator) intended as a *new, forward-only* numbering convention for G-OPS-issued Official POs (accepting that a Legacy-side accommodation would be needed before such a PO could ever actually import), or was it an imprecise restatement of this same real format?** *(Blocking — every downstream Official PO design decision depends on this answer; §18's Option A vs. B choice is otherwise a Gulliver decision this audit cannot make.)*
   - **What's confirmed**: the real format, its scale of validated use, and the exact rejection mechanism a 3+3+3 number would hit.
   - **Why this needs Gulliver, not more investigation**: this is a forward-looking intent question ("what did the customer mean/want") that no amount of additional source reading or data querying can resolve — it is not a fact about the system, it is a fact about the customer's own prior statement.
   - **What changes based on the answer**: if "imprecise restatement," `OfficialPoNumberGenerator` should be corrected to emit the real Legacy-compatible shape (Option A, §18) with no Legacy change needed at all. If "genuinely new," this project needs to separately scope and request the Legacy-side Import batch change across all four affected batch classes (§13) before any G-OPS-generated Official PO No. could ever successfully reach `TR_PO`.

2. **Is the `#AAA-AAA999-#AAA999` suffix pattern (13.9% of all real POs, used for revisions/additional invoices per the Excel-as-living-document structure already confirmed in the prior Official PO audit) something G-OPS's own Revision model (already implemented, `docs/supplier-response-revision-workflow.md`) needs to reproduce in its own generated Official PO numbers, or is that purely a Legacy-internal bookkeeping convention G-OPS need not replicate?**
   - **What's confirmed**: the suffix pattern exists, is real, is actively used, and its base segment is identical to the non-suffixed dominant shape.
   - **Why this needs Gulliver**: whether G-OPS's Revision feature needs to visually/structurally mirror this convention in the Official PO No. itself, versus tracking revisions purely in G-OPS's own database (as it already does), is a product design choice, not a fact this audit can determine.
   - **What changes based on the answer**: scopes whether any future Official PO No. generation work needs a revision-suffix feature at all.

*(The original audit's third open question — the real Production `MS_COMM(FILE_IMP, OFFIC_PO)` directory path and polling schedule — remains open but is unrelated to the PO-number-format question this Stage focused on; it is not re-listed here to keep this list to genuinely new/blocking items for this specific Stage's scope.)*

---

## 20. Final Judgment

**A — LEGACY FORMAT CONFIRMED — G-OPS RULE MUST BE RECONCILED.**

- **Source**: `BusinessLogicUtil`'s parser (`substring(0,4)`/`substring(5,8)`/`substring(9,11)`) enforces, structurally, an ≥11-character format and is the actual write-source of `TR_PO.SUPPLIER_CD`/`BRAND_CD` at Import time (§3, §11) — re-confirmed unchanged from the prior audit. A 9-character BR-08-shaped PO No. is rejected deterministically at the very first validation gate, before Supplier or Brand is even examined (§12) — this is not a probabilistic risk, it is a certainty every time.
- **Production Snapshot**: 8,374 of 8,376 real historical POs (99.98%), and 100% of the last 12/6/3 months, parse successfully through this exact logic and match the real Supplier and Brand masters (§4-§8). Zero of 8,376 real POs have ever used BR-08's proposed shape (§5-§6). The format has been in continuous use since at least 2017 with no cutover, and its dominant sub-shape's share is *increasing*, not fading (§14). The two exceptions are anomalous placeholder-like artifacts, not evidence of a competing real format (§9).
- **Current G-OPS**: `OfficialPoNumberGenerator` already implements exactly the BR-08 shape (§17) — meaning, as built today, G-OPS's own Official PO number generation would produce a number that Legacy's real, currently-operating Import batch would reject 100% of the time, for every single Supplier and Brand, with no exception.

This is not a marginal or theoretical discrepancy — it is a complete, empirically-verified format mismatch between what G-OPS currently generates and what the real, actively-used Legacy system will accept. Reconciliation (Gulliver Question 1, §19) must happen before any further Official PO integration work proceeds; §18's Option A (adopt Legacy's real, already-proven format) is the path requiring the least change, the least risk, and the least dependency on any out-of-scope Legacy modification — but the final choice remains a Gulliver decision, not one this audit makes for them.

**STOP.** No code changed. No Legacy change. No Production/UAT connection. No Production write. No Snapshot write (verified: all analysis via `SELECT`-only credentials; `tr_po` count independently re-checked as unchanged, still 8,376, after the write/DDL-rejection tests in §2). Awaiting ChatGPT Tech Lead review.
