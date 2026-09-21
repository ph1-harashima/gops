# G-OPS Production Snapshot Real Data Audit — Stage 2: READ-ONLY Business Data Structure Audit

Scope: read-only structural/aggregate analysis of the fully-restored
2026-09-16 Production Snapshot (`goo_prod_snapshot_20260916`, Stage 1C,
commit `8e36471`), via the dedicated `gops_snapshot_audit` (SELECT-only)
account exclusively. Every number below is a `COUNT`/`AVG`/percentile or a
schema fact - no individual row, customer name, personal name, email,
phone, address, free text, or credential is reproduced anywhere in this
document. Analysis only - no Application, Test, or DB change.

## 1. Executive Summary

The Legacy `goo` schema's real data confirms a genuine **N:M**
Supplier↔Brand relationship (not 1:1, not purely 1:N) with a long tail on
both sides; **no separate Manufacturer entity exists** - Manufacturer and
Supplier are the same concept throughout the schema; PO Headers are
schema-guaranteed single-Supplier/single-Brand, matching G-OPS's own model,
but ~2.7% of POs contain at least one line item whose item-level Brand
differs from the header's declared Brand; the historic Legacy PO number
format (`#`-prefixed, 10-25 characters, always containing special
characters) is structurally unrelated to G-OPS's own 9-character
auto-numbering scheme - no collision risk, but no continuity either;
`ms_stk`'s ~13x row multiplication per SKU is a genuine, confirmed
Warehouse dimension; several real Production-side data-quality anomalies
were found (most concerning: `MS_ITEM.ITEM_STATUS` contains numeric-looking
values in addition to its expected status vocabulary) - these are Legacy
Production data characteristics, not G-OPS defects.

## 2. Schema Mapping

Confirmed via actual DDL (Stage 1B/1C) and cross-checked against
`docs/GSYS_Specification.md` - no guessing:

| Business Concept | Table | Key column(s) |
|---|---|---|
| Supplier | `ms_comm` | `WHERE CATE_ID='MS_SUPPL'`, code in `CODE_ID`, name in `CODE_NAME` |
| Brand | `ms_comm` | `WHERE CATE_ID='MS_BRAND'`, code in `CODE_ID`, name in `CODE_NAME` |
| SKU / Item | `MS_ITEM` | `ITEM_CD` (PK), `BRAND_CD` |
| Stock | `ms_stk` | `(WH_CD, ITEM_CD)` composite PK, `STK_QTY`, `STK_STANDARD`, `SOLD_QTY` |
| PO Header | `tr_po` | `PO_NO` (PK), `SUPPLIER_CD`, `BRAND_CD` |
| PO Detail | `tr_po_dtl` | `(PO_NO, LINE_NO)` composite PK, `ITEM_CD`, `QTY_PO` |
| Arrival | `tr_arr` | `(SUPPLIER_CD, PO_NO, INV_NO)` composite PK, `ETA`, `ETA_WH` |
| Sales Qty | `ms_stk.SOLD_QTY` | (see §9 - almost entirely on the `WH_CD='XX'` aggregate row) |
| Logical Qty | **Not a stored column** | No column named `LOGICAL_QTY` (or similar) exists anywhere in the 41-table schema - see §19 |
| Stock Standard | `ms_stk.STK_STANDARD` | (see §9 - also concentrated on the `WH_CD='XX'` row) |
| Formula | `ms_formula` | Keyed by a generic `ID`, holds 11 free-text `FORMULA_*` config strings - a small (row count not separately queried; out of this Stage's required table list), global pricing-formula config table, not a per-item table |
| Item Status | `MS_ITEM.ITEM_STATUS`, `MS_ITEM.DISCON` | Two separate fields - see §16 for a real data-quality finding on `ITEM_STATUS` |

## 3. Supplier Master

| | Count |
|---|---|
| Total rows (`CATE_ID='MS_SUPPL'`) | 602 |
| Distinct `CODE_ID` | 602 (no duplicates - consistent with the table's own composite PK) |
| Null/blank `CODE_NAME` | 2 |
| `DEL_FLG=1` (explicitly flagged deleted) | 21 |
| `DEL_FLG=0` | 0 |
| `DEL_FLG` NULL | 581 |

`DEL_FLG` is never explicitly `0` in this data - only `NULL` or `1`. The
active/inactive convention in this data appears to be "`NULL` or absent =
active, `1` = deleted," not a genuine boolean `0`/`1` pair - worth
confirming with Gulliver (§19) before any G-OPS logic assumes otherwise.

A second, distinct category `MS_SUPP` (note: no trailing `L`) was found
with exactly 2 rows, both `DEL_FLG=1` (already inactive) - almost
certainly a legacy typo/duplicate category, not a live concern (P3, §16).

## 4. Brand Master

| | Count |
|---|---|
| Total rows (`CATE_ID='MS_BRAND'`) | 1,504 |
| Distinct `CODE_ID` | 1,504 (no duplicates) |
| Null/blank `CODE_NAME` | 0 |
| `DEL_FLG=1` | 46 |
| `DEL_FLG=0` | 0 |
| `DEL_FLG` NULL | 1,458 |

Same `DEL_FLG` convention as Supplier (§3).

## 5. Supplier × Brand

Derived from `tr_po` (the only table carrying both `SUPPLIER_CD` and
`BRAND_CD` together on every row) - the direct, actual-business-transaction
source, not an assumption from the master tables alone (which have no
Supplier↔Brand link of their own).

| | Value |
|---|---|
| Distinct Suppliers appearing in `tr_po` | 310 (of 602 in the master - the rest have never had a PO in this snapshot) |
| Distinct Brands appearing in `tr_po` | 803 (of 1,504 in the master) |
| Distinct Supplier–Brand pairs | 1,146 |

**Brands per Supplier**: min 1, median 2, avg 3.70, P90 4, P95 15, max
**102** (one Supplier alone accounts for a large share of the long tail).

**Suppliers per Brand**: min 1, median 2, avg 1.43, P90 3, P95 4, max
**13**.

**Classification**:

| | Count | % |
|---|---|---|
| Supplier with exactly 1 Brand | 242 | 78.1% |
| Supplier with >1 Brand | 68 | 21.9% |
| Brand with exactly 1 Supplier | 585 | 72.9% |
| Brand with >1 Supplier | 218 | 27.1% |

**Conclusion: genuine N:M**, skewed toward mostly-1:1 but with a real,
non-trivial minority on both sides (roughly 1 in 5 Suppliers, and just
over 1 in 4 Brands, participate in a many-relationship). Neither a pure
1:1 nor a pure 1:N model would be accurate.

## 6. Manufacturer vs Supplier

No `Manufacturer`-named table, category, or code column exists anywhere in
the 41-table schema (confirmed by full structural inventory, Stage 1B/1C -
the only counterparty-identifying column used by both `tr_po` and `tr_arr`
is `SUPPLIER_CD`, referencing the single `MS_SUPPL` category in `ms_comm`).
The literal word "manufacturer" appears exactly once in the entire ~5.2 GB
dump, incidentally, inside one PO's own free-text shipping/payment-term
field - not as any structural element, and not reproduced here per the
Privacy rules.

**Conclusion: A — Manufacturer is treated as Supplier.** There is no
independent Manufacturer entity in this Legacy schema; every place a
"manufacturer" concept would apply (PO counterparty, Arrival counterparty)
uses `SUPPLIER_CD` exclusively.

## 7. SKU Structure

| | Count |
|---|---|
| Total `MS_ITEM` rows | 116,842 |
| Distinct `ITEM_CD` | 116,842 (no duplicates - PK-consistent) |
| `BRAND_CD` null/blank | 11 |
| `DISCON=1` | 39,551 |
| `DISCON=0` | 39,824 |
| `DISCON` NULL | 37,467 |
| `DEL_FLG=1` | 69,562 |
| `DEL_FLG` NULL or `0` | 47,280 |
| Items never appearing in any `tr_po_dtl` line (no PO-derived Supplier link at all) | 66,165 (56.6%) |

**Brand → SKU**: min 1, median 33, avg 110.64, P90 239, P95 372, max
**18,596** (one Brand alone holds nearly 16% of all SKUs - a major outlier
worth its own pagination consideration, §17).

**Supplier → SKU** (via `tr_po_dtl`→`tr_po` join, i.e. "has this Supplier
ever supplied this SKU"): min 1, median 19, avg 204.05, P90 274, P95 1,161,
max **7,279**.

Both distributions are heavily right-skewed (median far below average) -
a small number of Brands/Suppliers account for a disproportionate share of
SKU volume.

## 8. Stock Structure

| | Value |
|---|---|
| Total `ms_stk` rows | 1,518,842 |
| Distinct `ITEM_CD` represented | 116,835 (of 116,842 `MS_ITEM` rows - 7 SKUs have no `ms_stk` row at all) |
| Distinct `WH_CD` | **13** |

Rows-per-SKU: **13 rows for nearly every SKU** (min 3, max 13, avg 13.00) -
this conclusively confirms the row-multiplication dimension is
**Warehouse**, not date/store/other (13 distinct `WH_CD` values: 12
numbered codes `4`-`15`, plus one `XX` catch-all/aggregate code, each
appearing exactly 116,834 times - i.e. once per SKU, with only a handful of
exceptions).

**Column population is concentrated by `WH_CD` role**, not uniform:

| `WH_CD` | `STK_QTY` populated | `SOLD_QTY` populated | `STK_STANDARD` populated |
|---|---|---|---|
| Numbered codes (`4`-`15`, combined) | 12,110 of 1,402,008 rows (0.9%) | 0 | 0 |
| `XX` (aggregate row) | 0 | 3,897 of 116,834 (3.3%) | 51,308 of 116,834 (43.9%) |

**Conclusion**: `SOLD_QTY` and `STK_STANDARD` live almost exclusively on
the per-SKU `WH_CD='XX'` aggregate row, not on individual warehouse rows;
`STK_QTY` itself is sparsely populated even within specific warehouse rows
(most SKU×Warehouse combinations have no quantity recorded at all - likely
meaning "not stocked at that warehouse" rather than "stocked with quantity
zero," since 0/negative values never actually occur where `STK_QTY` is
non-null).

## 9. PO Structure

| | Value |
|---|---|
| Total `tr_po` rows | 8,376 |
| Distinct `PO_NO` | 8,376 (unique, matches PK) |
| `PO_NO` null/blank | 0 |
| Orphan `tr_po_dtl` rows (no matching header) | **0** |
| `SUPPLIER_CD` null | 0 |
| `BRAND_CD` null | 2 |

**Details per PO**: min 1, median 10, avg 21.94, P90 54, P95 84, max
**703**.

**1 PO = 1 Supplier**: guaranteed by schema (single `SUPPLIER_CD` column
per header row) - 0 multi-Supplier POs are structurally possible.

**1 PO = 1 Brand**: also a single `BRAND_CD` column per header - but
checking whether the *line items'* own item-level Brand
(`MS_ITEM.BRAND_CD`) actually agrees with the header's declared Brand:
**1,070 of 183,477 joined detail lines (0.58%) belong to an item whose own
Brand differs from its PO header's declared Brand, affecting 227 of 8,376
POs (2.7%)**. So while the schema *structurally* enforces one Brand per PO
header, real data shows a real (small but non-zero) minority of POs whose
actual line items span more than one Brand.

## 10. PO Number Pattern

Examined as anonymized, aggregate pattern data only - no PO number is
reproduced.

**Length distribution** (8,376 total):

| Length | Count | % |
|---|---|---|
| 11 | 6,749 | 80.6% |
| 19 | 1,269 | 15.2% |
| 12 | 197 | 2.4% |
| 13 | 80 | 1.0% |
| 20 | 75 | 0.9% |
| 10, 14, 24, 25 | 6 combined | <0.1% |

**Format**: 100% of PO numbers contain at least one non-alphanumeric
character (0 are purely numeric or purely alphanumeric); 99.98% (8,374 of
8,376) begin with `#`. **No duplicate PO numbers** (case-insensitive check
included).

**G-OPS 9-character model compatibility**: G-OPS's own Official PO
numbering (Supplier abbreviation 3 + Brand abbreviation 3 + 3-digit
serial, 9 characters, no special characters) is **structurally unrelated**
to this historic Legacy format - no Legacy `tr_po.PO_NO` value matches
G-OPS's 9-character, special-character-free shape at all. This means: no
collision risk between the two numbering schemes, but also no inherent
continuity - G-OPS's numbers are a new, independent scheme, not a
continuation of the Legacy one. (G-OPS's own numbering design was not
changed or evaluated for correctness here, per instruction - this is a
structural observation only.)

## 11. PO Volume

`ORDR_DATE` range: 1900-01-03 to 2026-09-16 (max exactly matches the
snapshot date - confirms live, current data; the 1900 dates are 2 rows, a
placeholder/default-date artifact, not real order dates - see §16).

**Monthly PO count, most recent 12 months** (2025-10 through 2026-09):
min 72, max 214, avg 133.9/month.

**Monthly PO Detail line count, same period**: ranged roughly 664 to
3,707 lines/month across the same 12 months (see the month-by-month table
below).

| Month | PO count | Detail lines |
|---|---|---|
| 2026-09 | 86 | 1,103 |
| 2026-08 | 162 | 2,455 |
| 2026-07 | 208 | 2,693 |
| 2026-06 | 214 | 3,707 |
| 2026-05 | 139 | 2,924 |
| 2026-04 | 169 | 3,005 |
| 2026-03 | 152 | 3,479 |
| 2026-02 | 99 | 1,614 |
| 2026-01 | 103 | 1,406 |
| 2025-12 | 103 | 2,157 |
| 2025-11 | 72 | 664 |
| 2025-10 | 100 | 1,710 |

These volumes are modest by web-application standards (low hundreds of
POs/month) - see §17 for the pagination/performance implication.

## 12. Arrival Structure

| | Value |
|---|---|
| Total `tr_arr` rows | 17,722 |
| `PO_NO` null/blank | 0 |
| `SUPPLIER_CD` null/blank | 0 |
| `BRAND_CD` null/blank | 0 |
| Orphan rows (`PO_NO` not found in `tr_po`) | 3 (0.017%) |
| `ETA` populated | 14,210 of 17,722 (**80.2%**) |
| `ETA_WH` populated | 17,566 of 17,722 (**99.1%**) |

Both ETA fields are well-populated in this Production data - `ETA_WH` in
particular is populated almost universally.

## 13. Candidate List Implication

Based on §5/§7's cardinality evidence: Brand-first navigation
(Dashboard → Brand → Candidate SKU) is **data-structurally supportable**
for the large majority of cases (72.9% of Brands map to exactly one
Supplier), but the 27.1% of Brands that map to multiple Suppliers (up to
13) mean a Brand-filtered Candidate list can legitimately mix SKUs from
different Suppliers under the same Brand - directly relevant to G-OPS's
own `MIXED_SUPPLIER_NOT_ALLOWED` Draft-creation rule (a user browsing by
Brand could select SKUs that turn out to belong to different Suppliers).
Additionally, one Brand alone holds 18,596 SKUs (§7) - far beyond a single
comfortable list page.

**Judgment: SUPPORTED WITH RISK.**
- Supported: the majority-1:1 Brand↔Supplier shape matches Brand-first
  navigation's implicit assumption.
- Risk: the minority-but-real multi-Supplier Brands create a real chance
  of hitting `MIXED_SUPPLIER_NOT_ALLOWED` from within a single
  Brand-filtered browse session, and at least one Brand's sheer SKU volume
  (18,596) needs real pagination, not just a long single page.

## 14. Search Axis Implication

| Axis | Real-data necessity evidence |
|---|---|
| Brand | 1,504 Brands, up to 18,596 SKUs under one Brand - a Brand filter is clearly necessary, not optional |
| Supplier | 602 Suppliers, up to 7,279 SKUs per Supplier - same conclusion |
| SKU | 116,842 distinct SKUs - a direct SKU-code search is necessary at this scale |
| PO Number | 8,376 distinct POs, format shown in §10 - a PO-number search remains directly useful, independent of G-OPS's own separate numbering |
| Portal Management Number | G-OPS-internal, not present in this Legacy data - no real-data evidence either way |

No additional strong candidate search axis emerged from this structural
pass beyond what G-OPS already assumes - recorded as a Candidate list only
per instruction (none identified this round).

## 15. Data Quality

Aggregate findings only, explicitly distinguishing **Production DB
data-quality characteristics** (not G-OPS's own responsibility) from
anything else:

| Sev | Finding | Evidence |
|---|---|---|
| **P1** | `MS_ITEM.ITEM_STATUS` (declared `varchar(30)`, presumably a status enum) actually holds 135 distinct values, including 185 rows with purely numeric-looking values (e.g. price/ID-shaped strings) and 251 rows with other non-standard values (e.g. a garbled `RETURNIDw/opage`, 243 rows) - alongside the expected `NULL` (61,573), blank (30,501), and `NEW` (24,332). This looks like historic column misuse/data entry drift in Production, not a G-OPS concern, but any G-OPS feature reading `ITEM_STATUS` as a clean enum should account for this. |
| **P2** | `ms_stk.SOLD_QTY`/`STK_STANDARD`/`STK_QTY` are NULL in 99.7% / 96.6% / 99.2% of rows respectively (§8) - largely explained by the confirmed Warehouse-row/aggregate-row split, but still means any naive `AVG`/`SUM` over `ms_stk` without filtering by `WH_CD` role would be misleading. |
| **P2** | 66,165 of 116,842 `MS_ITEM` rows (56.6%) have never appeared in any `tr_po_dtl` line - no PO-derived Supplier relationship exists for the majority of SKUs. |
| **P3** | `MS_SUPP` (missing the trailing `L`) is a 2-row, already-inactive (`DEL_FLG=1`) duplicate/typo category alongside the real `MS_SUPPL` - both are effectively dead, no live impact. |
| **P3** | 2 `tr_po` rows carry a `1900-01-03` placeholder `ORDR_DATE`, clearly not a real order date. |
| **P3** | 3 `tr_arr` rows reference a `PO_NO` not present in `tr_po` (0.017% orphan rate) - negligible in volume, worth a one-line mention only. |

No P0 (nothing found that would itself be a blocking correctness issue for
this audit's own purposes).

## 16. Performance Implication

Row counts and their implications for any future G-OPS↔Legacy-scale
interaction (informational only - **no index was added, no query was
changed**):

| Table | Rows | Implication |
|---|---|---|
| `MS_ITEM` | 116,842 | A full scan is non-trivial but not extreme; any SKU-code lookup should stay indexed (already PK'd on `ITEM_CD`). |
| `ms_stk` | 1,518,842 | The largest table by row count. A naive `JOIN` against `MS_ITEM` without filtering `WH_CD` (e.g. to just `'XX'` for aggregate figures) multiplies result rows ~13x unnecessarily - a real join-cardinality risk for any future integration. |
| `tr_po` | 8,376 | Small; full scans are cheap. |
| `tr_po_dtl` | 183,480 | Moderate; already keyed by `(PO_NO, LINE_NO)` with a `PO_NO` index - fine for header-scoped lookups. |
| `his_arr_list` | 282,592 | A pure history/log table by name - likely append-mostly; not on any live G-OPS query path today. |
| `his_stk_list` | 67,199 | Same category as above; also the table Stage 1's `ERROR 1118` traced to (271 columns) - if ever queried directly, its sheer column width is itself a cost, independent of row count. |

Overall: monthly transactional volumes (§11) are modest; the main
performance-relevant *structural* fact is `ms_stk`'s warehouse-row
multiplication (§8), which any future cross-system aggregation logic needs
to account for explicitly rather than assume 1 row = 1 SKU.

## 17. G-OPS Current Design Compatibility

- **Official PO numbering**: no overlap/collision risk with the Legacy
  `tr_po.PO_NO` format (§10) - the two are independent by construction.
- **`MIXED_SUPPLIER_NOT_ALLOWED` Draft rule**: consistent with the
  schema-level guarantee that a PO Header has exactly one Supplier (§9) -
  matches real data with zero exceptions at the header level.
- **Brand-first Candidate navigation**: supported with a real, non-trivial
  risk surface from multi-Supplier Brands and at least one very
  high-volume Brand (§13).
- **Manufacturer Channel / Manufacturer-facing concepts** in G-OPS map
  cleanly onto Legacy's own `SUPPLIER_CD` - no separate Manufacturer
  identity to reconcile (§6).

## 18. Findings / Risks

Summarized from §15-17 above - no new findings introduced here.

## 19. Questions Requiring Gulliver Confirmation

1. Is `DEL_FLG` genuinely a `NULL`-means-active / `1`-means-deleted
   convention throughout `ms_comm`/`MS_ITEM`, or does a real `0` ever get
   written by some other process not captured in this snapshot?
2. What is the intended semantics/source of `MS_ITEM.ITEM_STATUS`'s
   non-enum values (numeric-looking strings, `RETURNIDw/opage`) - a known
   legacy data-entry issue, or does some external import path write
   these deliberately?
3. Does "Logical Qty" (mentioned in this task's own instruction) correspond
   to an application-computed value derived from `ms_stk`'s columns (e.g.
   `STK_QTY` minus reserved minus incoming), rather than a stored column -
   no column of that name exists anywhere in the schema.
4. For the 227 POs (2.7%) whose line items span more than one Brand
   (§9) - is this an intentional, supported Legacy pattern, or itself a
   data-entry anomaly?

## 20. Recommended Next Stage

Not decided here, per instruction (analysis only, no recommendation to
proceed to a specific next stage beyond what ChatGPT/Techlead directs) -
the structural facts above are handed off as-is for that review.
