# G-OPS Stage 3B — Legacy Current Stock / Logical Qty Definitive Audit (Audit Only)

Scope: trace the actual Legacy G-SYS (`phasep-gulliver`) Java source to
determine, with source-level evidence (not inference), the real Current
Stock / Logical Qty / Recommended Qty calculation logic, then validate it
against the 2026-09-16 Production Snapshot's aggregate data. **No
Application/Test/DB change. No implementation.**

## 1. Executive Summary

Legacy source (`MsStkRepositoryImpl.getBaseStockList()`,
`jp.ne.glv.repository.impl`) conclusively defines three distinct,
named quantities - `SELLABLE_QTY`, `PHISICAL_QTY` (Legacy's own spelling),
and `LOGICAL_QTY` - each a `SUM` over an **explicit, named set of
`WH_CD` values**, joined via 12 individually-aliased `LEFT JOIN`s
(`WH1`..`WH12`), never a single hardcoded warehouse row. Cross-referencing
the real Warehouse Master (`ms_comm` `CATE_ID='MS_WH'`, read via
`gops_snapshot_audit`) resolves every `WH_CD` to its real Japanese name and
confirms the exclusion rule G-OPS's own SQL comment had already guessed
correctly in spirit (Damaged/Private Auction/Disposal) is exactly right,
down to the specific codes. G-OPS's `current_stock` (`WH_CD='01'`, which
does not exist in real data) should be replaced with Legacy's own
`PHISICAL_QTY` definition: `SUM(STK_QTY)` over `WH_CD IN
('4','5','6','7','8','10','11','12','15')`. `LOGICAL_QTY` = that same
physical sum **plus** the full `PO_QTY_1..20` and `SHIP_QTY_1..10` sums -
resolving the original implementation team's own "NOT FULLY RESOLVED"
note with source-level certainty.

## 2. Legacy Source Trace

| Class | Role | Finding location |
|---|---|---|
| `jp.ne.glv.repository.impl.MsStkRepositoryImpl` | Builds the Stock List SQL (`getBaseStockList()`, `getStockListOrder()`) and maps its `ResultSet` into `physicalQty`/`logicalQty`/`sellableQty`/`soldQty`/`stkStandard` | Lines ~1795-1994 (SQL construction), ~2555-2614 (result mapping + calc wiring) |
| `jp.ne.glv.utilities.StockCalculationHelper` | Orchestrates `calc1`-`calc4` (Recommended Qty chain) from `logicalQty`+`stkStandard`+`poQtyList`+`arrQtyList`+`MS_FORMULA` strings | Whole file (81 lines) - no stock-quantity computation of its own, pure orchestration |
| `jp.ne.glv.utilities.OrderQuantityCalculator` | Evaluates the actual `calc1`-`calc4` formula expressions | Not itself a stock-source - consumes `logicalQty` as already-computed input |
| `jp.ne.glv.Const` | `MS_STK_WH_CD_PRI = "XX"` (the aggregate/primary row code) | Line 228 |
| Warehouse Master (`ms_comm`, `CATE_ID='MS_WH'`) | Real names for `WH_CD` 4-15 | Queried via `gops_snapshot_audit` (SELECT-only), §3 below |

No Batch/Excel-export class needed to be traced further once
`getBaseStockList()`'s own SQL (the method
`RecommendedQtyReadQuery.sql` itself already cites as its extraction
source) was found to directly and completely answer §4-§7 below with
concrete, unambiguous SQL text - not a downstream consumer's
re-derivation.

## 3. WH_CD Semantics

Resolved via two independent, converging sources: (a) Legacy source code's
own JOIN conditions and inline comments (`MsStkRepositoryImpl.java`
~1832-1993), and (b) the real Warehouse Master data itself
(`ms_comm` `CATE_ID='MS_WH'`, `CODE_NAME`, read this Stage via
`gops_snapshot_audit`, SELECT-only - confirmed not personal/sensitive data,
just facility names).

| `WH_CD` | Legacy alias | Real name (`ms_comm.CODE_NAME`) | English | In `PHISICAL_QTY`? | In `SELLABLE_QTY`? |
|---|---|---|---|---|---|
| 4 | WH1 | 4:A棟 | Building A | Yes | Yes |
| 5 | WH2 | 5:B棟1F | Building B, 1F | Yes | Yes |
| 6 | WH3 | 6:B棟2F | Building B, 2F | Yes | Yes |
| 7 | WH4 | 7:緑町 | Midoricho (site name) | Yes | Yes |
| 8 | WH5 | 8:商品取置 | Item Hold/Reserved | Yes | No |
| 9 | WH6 | 9:不良品F | Defective Item Floor | **No** (Legacy comment: "Damaged") | No |
| 10 | WH7 | 10:倉庫 | Warehouse (generic) | Yes | No |
| 11 | WH8 | 11:オークション | Auction | Yes | Yes |
| 12 | WH9 | 12:販売中止倉庫 | Sales-Discontinued Warehouse | Yes | No |
| 13 | WH10 | 13:個人オークション | Personal/Private Auction | **No** (Legacy comment: "Private Auction") | No |
| 14 | WH11 | 14:廃棄倉庫 | Disposal Warehouse | **No** (Legacy comment: "EXCLUDE DISPOSAL INVENTORY") | No |
| 15 | WH12 | 15:仕入処理倉庫 | Purchasing/Procurement Processing | Yes | No |
| XX | `Const.MS_STK_WH_CD_PRI` | (not a physical warehouse) | Aggregate/primary row - holds `STK_STANDARD`, `SOLD_QTY`, and all `PO_QTY_*`/`ARR_QTY_*`/`SHIP_QTY_*` | N/A | N/A |

The two sources **independently agree exactly**: Legacy's own inline
comments name the 3 exclusions only vaguely by role ("Damaged"/"Private
Auction"/"Disposal"); the real Master data's `CODE_NAME` values confirm
the precise codes those roles refer to (9/13/14) word-for-word
(不良品="defective goods", 個人オークション="personal/private auction",
廃棄="disposal"). This is not a guess - both independent sources converge
on the identical 3-code exclusion set.

## 4. Current Stock Formula

Legacy source, `MsStkRepositoryImpl.getBaseStockList()` (~line 1830-1844),
verbatim structure (aliases resolved per §3):

```
PHISICAL_QTY =
    IFNULL(WH_CD=4.STK_QTY, 0) + IFNULL(WH_CD=5.STK_QTY, 0) + IFNULL(WH_CD=6.STK_QTY, 0)
  + IFNULL(WH_CD=7.STK_QTY, 0) + IFNULL(WH_CD=8.STK_QTY, 0)
  + IFNULL(WH_CD=10.STK_QTY, 0) + IFNULL(WH_CD=11.STK_QTY, 0) + IFNULL(WH_CD=12.STK_QTY, 0)
  + IFNULL(WH_CD=15.STK_QTY, 0)
```
(`WH_CD` 9/13/14 lines are literally commented out in Legacy's own source -
not omitted by oversight.)

A **narrower**, separately-named `SELLABLE_QTY` also exists in the same
method (~line 1823-1830), summing only 5 of those 9 codes:
```
SELLABLE_QTY = IFNULL(WH_CD=4.STK_QTY,0) + IFNULL(WH_CD=5.STK_QTY,0)
             + IFNULL(WH_CD=6.STK_QTY,0) + IFNULL(WH_CD=7.STK_QTY,0)
             + IFNULL(WH_CD=11.STK_QTY,0)
```

**Which one is "Current Stock"?** `physicalQty` is the field name Legacy's
own result-mapping code uses (`MsStkRepositoryImpl.java:2566-2567`,
`Integer physicalQty = ...; jsonObjectStockList.put("physicalQty",
physicalQty)`) and is also the value fed into `formulaObject.getStkRate(
stkStandard, physicalQty)` (line 2605) - Legacy's own "stock rate"
calculation, a general-purpose current-stock-vs-standard comparison.
`sellableQty` is stored (`jsonObjectStockList.put("sellableQty", ...)`,
line 2563) but was not found feeding any further calculation in this same
method - it appears to be a narrower, display/other-purpose-specific
figure. **G-OPS's `current_stock` should map to Legacy's `PHISICAL_QTY`**,
not `SELLABLE_QTY` - this also matches G-OPS's own pre-existing SQL
comment's stated intent ("Legacy's physical stock sum spans
WH1..WH5,WH7..WH9,WH12... excluding WH6=Damaged, WH10=Private Auction,
WH11=Disposal" - confirmed, source-verified, exactly correct in §3).

## 5. Logical Qty Formula

Immediately following `PHISICAL_QTY` in the same `SELECT` (~line
1845-1888), `LOGICAL_QTY` is:

```
LOGICAL_QTY =
    SUM(PO_QTY_1..PO_QTY_20)              -- all 20 slots, unconditionally
  + SUM(SHIP_QTY_1..SHIP_QTY_10)          -- all 10 slots, unconditionally
  + [ the SAME 9-warehouse STK_QTY sum as PHISICAL_QTY, WH_CD 9/13/14 excluded ]
```

I.e., **`LOGICAL_QTY = PHISICAL_QTY + total open PO qty + total open
Shipment qty`** - a standard "available to promise" style figure (what is
physically on hand, plus what is already committed to arrive). This is
the exact quantity `RecommendedQtyReadQuery.sql`'s own comment flagged as
"an additional WH-quantity term... NOT FULLY RESOLVED... deliberately NOT
reproduced here rather than guessed at." **This Stage resolves that gap
with source-level certainty**: it was never a bug or a double-count to
avoid - it is `LOGICAL_QTY`'s own, real, by-design second summand,
confirmed by the same SQL block that computes `PHISICAL_QTY` immediately
above it.

## 6. Recommended Qty Input Mapping

| G-OPS field (`RecommendedQtyReadQuery.sql`) | Legacy equivalent | Judgment |
|---|---|---|
| `current_stock` | `PHISICAL_QTY` (§4) | **INCORRECT** - currently `WH_CD='01'` (0 rows in real data); should be the 9-warehouse sum |
| `sold_qty` (→ `monthly_sales`) | `ms_stk.SOLD_QTY` on `WH_CD='XX'` | **CORRECT** - matches Legacy's own `STK.WH_CD='XX'` source alias (`STK` = the aggregate-row alias in Legacy's own query, same convention G-OPS's `agg` join already uses) |
| `stk_standard` | `ms_stk.STK_STANDARD` on `WH_CD='XX'` | **CORRECT** - same `STK`/`agg` (`WH_CD='XX'`) source in both |
| `open_po` (→ `PO_QTY_1..20` sum) | Same 20-slot sum, sourced from `WH_CD='XX'` in Legacy | **CORRECT** - re-verified this Stage: 100% of non-null `PO_QTY_1` values are on `WH_CD='XX'`, 0% on any numbered code |
| `open_arrival` (→ `ARR_QTY_1..10` sum) | Same, `WH_CD='XX'` | **CORRECT** - same verification as `open_po` |
| `open_ship` (→ `SHIP_QTY_1..10` sum) | Same, `WH_CD='XX'`, and **directly a summand of `LOGICAL_QTY` itself** (§5) | **CORRECT** as a value; not yet wired into `logicalQty`'s own G-OPS-side equivalent (out of this audit's scope to redesign - see §9) |
| **`logical_qty` (Recommended Qty's real primary input)** | **Not present in G-OPS at all today** | **UNKNOWN → now RESOLVED** (§5) - G-OPS's `RecommendedQtyCalculator` should receive `PHISICAL_QTY + open_po + open_ship` (per §5's formula), not `current_stock` alone, if it is meant to reproduce Legacy's own `calc1` input exactly |

## 7. Snapshot Validation

Validated the confirmed formula's warehouse set against the real Snapshot
(`gops_snapshot_audit`, SELECT-only, aggregate counts only - no individual
SKU/product name in this document):

| Check | Result |
|---|---|
| Distinct SKUs in `ms_stk` | 116,835 |
| SKUs with ≥1 non-null `STK_QTY` among `PHISICAL_QTY`'s 9-warehouse set (4,5,6,7,8,10,11,12,15) | 9,731 (8.3%) |
| SKUs with ≥1 non-null `STK_QTY` among `SELLABLE_QTY`'s 5-warehouse set (4,5,6,7,11) | 9,456 (8.1%) |
| Excluded `WH_CD='9'` (Defective) - any populated rows in this Snapshot? | 0 populated (all NULL) - no defective stock currently on hand at this Instance |
| Excluded `WH_CD='13'` (Private Auction) - populated rows / total qty | 159 rows / 240 units total - real quantity that the formula correctly keeps out of `PHISICAL_QTY` |
| Excluded `WH_CD='14'` (Disposal) - populated rows / total qty | 44 rows / 102 units total - same conclusion |

**Formula applicability**: the formula itself is well-defined and directly
computable against this Snapshot's real column/table shape - no
unexpected NULL-handling exception, no missing warehouse code, no
structural surprise. The low overall population rate (~8%) reflects
Stage 2's own already-documented finding that most `ms_stk` rows simply
have no recorded quantity (§8/§15 of the Stage 2 report) - not a defect in
this formula. The formula's own exclusion set demonstrably matters in
practice: 342 real units (240+102) currently sit in the two
excluded-for-good-reason warehouses (Private Auction, Disposal) and would
have been wrongly counted as available stock had those codes not been
excluded.

## 8. Correct G-OPS Replacement (not implemented)

Two equivalent, source-faithful candidate replacements for the current
`LEFT JOIN ms_stk phys ON phys.item_cd = i.item_cd AND phys.wh_cd = '01'`
/ `COALESCE(phys.stk_qty, 0) AS current_stock` in
`RecommendedQtyReadQuery.sql`, **neither implemented this Stage**:

**Option A (scalar subquery, closest to Legacy's own per-item semantics
without repeating 9 `LEFT JOIN`s):**
```sql
COALESCE((
  SELECT SUM(s.stk_qty) FROM ms_stk s
  WHERE s.item_cd = i.item_cd
    AND s.wh_cd IN ('4','5','6','7','8','10','11','12','15')
), 0) AS current_stock
```

**Option B (9 explicit `LEFT JOIN`s, mirroring Legacy's own `WH1`..`WH12`
structure exactly, at the cost of a wider query):**
```sql
LEFT JOIN ms_stk wh4  ON wh4.item_cd  = i.item_cd AND wh4.wh_cd  = '4'
LEFT JOIN ms_stk wh5  ON wh5.item_cd  = i.item_cd AND wh5.wh_cd  = '5'
... (wh6, wh7, wh8, wh10, wh11, wh12, wh15) ...
-- current_stock = COALESCE(wh4.stk_qty,0) + COALESCE(wh5.stk_qty,0) + ... + COALESCE(wh15.stk_qty,0)
```

Both preserve the existing query's "one row per item" output shape (no
`GROUP BY` needed, no risk of the row-multiplication B5 already ruled out
in Stage 3) - only the `phys`/`WH_CD='01'` piece would change; every other
join (`agg`/`WH_CD='XX'`, `ms_formula`, `ms_comm` brand/supplier lookups,
`latest_po`) is untouched.

**Source confidence: HIGH.** Both the `WH_CD` set and the exclusion
rationale are confirmed by Legacy's own source code (inline comments +
explicit commented-out lines, not inferred) and independently
cross-verified against the real Warehouse Master's own names (§3) - no
part of this recommendation rests on assumption.

## 9. Remaining Unknowns

- `LOGICAL_QTY`'s exact intended use in a Recommended-Qty-input sense for
  G-OPS is now formula-confirmed (§5/§6), but **whether G-OPS should wire
  `RecommendedQtyCalculator` to receive this exact
  `PHISICAL_QTY+open_po+open_ship` composite (matching Legacy's `calc1`
  input precisely) is a design/remediation decision, not resolved by this
  audit** - out of this Stage's own scope (audit only).
- `SELLABLE_QTY`'s own intended consumer/screen in Legacy was not traced
  further this Stage (not needed to resolve `current_stock`/`LOGICAL_QTY`,
  the Stage's own stated objective) - noted only for completeness, not a
  blocker to anything above.

## 10. Gulliver Questions

(Only items Source could not resolve - none of §3-§8's own conclusions are
included here again, since they are already fully sourced.)

1. Should G-OPS's own Recommended Qty calculation adopt Legacy's exact
   `LOGICAL_QTY` composite (`PHISICAL_QTY + open_po + open_ship`) as its
   input, or is a Prototype-specific simplification acceptable/intended -
   a product decision, not a Source-traceable fact.
2. `SELLABLE_QTY`'s own real-world purpose/consumer in Legacy (confirmed
   to exist and be computed, but its downstream use was not traced this
   Stage) - relevant only if a future Stage decides G-OPS needs an
   equivalent of it specifically, not for `current_stock`/`LOGICAL_QTY`
   itself.

## 11. Stage 4 Recommendation

Not decided here, per instruction. Carried forward from Stage 3 §13/§14
(Candidate pagination, Price Change pagination, Mixed Supplier UX, Mixed
Brand rule, DISCON display) remain untouched, unimplemented candidates.
This Stage's own finding (§8's two candidate replacements for
`current_stock`) is handed off as an additional, now fully-source-verified
candidate for the same future remediation round.
