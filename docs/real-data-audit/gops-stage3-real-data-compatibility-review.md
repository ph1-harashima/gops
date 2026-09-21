# G-OPS Stage 3 — Real-Data Compatibility Review (Code Audit Only)

Scope: read-only source code audit of the current G-OPS implementation
(backend Java + frontend TypeScript, current `main`), cross-referenced
against Stage 2's confirmed real-Production-data facts
(`gops-prod-snapshot-20260916-stage2-business-data-audit.md`, commit
`9f72695`). Any additional Snapshot query needed for this audit used
`gops_snapshot_audit` (SELECT-only) exclusively. **No Application, Test, or
DB change was made. G-OPS was never connected to the Snapshot.**

## 1. Executive Summary

The single most severe finding is in the Legacy Stock Adapter (§4): the
Order Candidate List / Stock-Sales List's `current_stock` figure is sourced
from a hardcoded `ms_stk.wh_cd = '01'` join - and **`WH_CD='01'` does not
exist anywhere in the real Production snapshot** (confirmed: 0 rows). This
would make `current_stock` silently resolve to `0` for every single item
if this code path were ever pointed at real Production data - a genuine
**REAL-DATA BLOCKER**, not merely a risk. It is explicitly a documented
simplification ("this Demo Instance seeds only ONE physical warehouse row"),
not an oversight, but it has not been re-validated against real warehouse
codes. Two further significant findings: Draft creation validates
single-Supplier but has **no equivalent single-Brand validation** at all
(directly relevant given Stage 2's confirmed N:M Supplier↔Brand data and
the 227 real POs with a Brand mismatch), and the **Order Candidate List and
Price Change candidate list have no pagination at all** (neither
server-side nor client-side), while Stage 2 confirmed a real Brand with
18,596 SKUs. `ITEM_STATUS` handling itself is robust (no crash risk) but
the frontend's color-coded DISCON/ON_HOLD states never actually occur in
real data, while genuinely-discontinued items (`DISCON=1`) usually carry
an `ITEM_STATUS` of `NULL`, blank, or even `"NEW"` - the intended visual
warning would rarely appear. Manufacturer terminology is correctly unified
with Supplier identity at the data-model level (no issue).

## 2. Stage 2 Facts (baseline, not re-derived)

Supplier 602, Brand 1,504, SKU 116,842, `ms_stk` 1,518,842; Supplier↔Brand
genuine N:M (27.1% of Brands have >1 Supplier, max 13; Supplier→Brand max
102); Brand→SKU max 18,596, Supplier→SKU max 7,279; `ms_stk` ≈13 rows/SKU
via `WH_CD`, with `SOLD_QTY`/`STK_STANDARD` concentrated on `WH_CD='XX'`;
PO Header = single Supplier column; 227 POs (2.7%) have a line-item Brand
mismatch; `ITEM_STATUS` has 135 distinct values including numeric-looking
and garbled entries; Manufacturer = Supplier (no separate entity); Legacy
PO number scheme is independent from G-OPS's own.

## 3. Candidate Flow Review (Audit A)

Traced `CandidateListPage.tsx` (frontend) and `OrderCandidateController` /
`OrderCandidateService` / `OrderDraftService` (backend):

- **A1 (Supplier shown?)**: **Yes.** Both the Desktop table
  (`CandidateListPage.tsx:557`) and the Mobile card
  (`CandidateListPage.tsx:447`) render `row.supplierName ?? row.supplierCode`
  for every row.
- **A2 (can the user recognize a Supplier difference before selecting?)**:
  Partially - Supplier is visible per-row (A1) and a Supplier *filter*
  exists (`filter.supplierCode`), but there is no grouping, sorting, or
  visual separation by Supplier within a Brand-filtered list, and no
  warning appears as the user checks boxes across different Suppliers.
- **A3 (when does `MIXED_SUPPLIER_NOT_ALLOWED` actually fire?)**: Only
  after the user clicks "Create Draft" - `handleCreateDraft()` calls
  `createDraftMutation.mutate(...)`, and the error surfaces from that HTTP
  response (`OrderDraftService.java:69-74`, `Set<String> supplierCodes...
  if (supplierCodes.size() > 1) throw new MixedSupplierException(...)`).
- **A4 ("error only after selection"?)**: **Confirmed, this is exactly
  the current behavior.** `toggleSelect(sku)`
  (`CandidateListPage.tsx:189-196`) has no Supplier-awareness at all - any
  SKU can be freely checked regardless of what else is already selected.
- **A5 (Supplier filter/grouping)**: A Supplier **filter** exists (narrows
  the list to one Supplier); there is no **grouping** within an unfiltered
  or Brand-filtered view.
- **A6/A7 (pagination for an 18,596-SKU Brand)**: **No server-side
  pagination exists at all.** `GET /api/order-candidates`
  (`OrderCandidateController.java:24-30`) calls
  `findOrderCandidates(brandCode, supplierCode, keyword)` with no
  page/size parameter, backed by `RecommendedQtyReadQuery.sql`, which has
  no `LIMIT`/`OFFSET` clause. The full matching result set (up to the
  real 18,596-row Brand, per Stage 2) is returned and rendered in one
  response - confirmed **client-side full-load, not paginated**, the
  opposite of what A6 asks for.

## 4. Legacy Stock Adapter Review (Audit B) — Most Important

Every backend class/SQL file reading `ms_stk` was enumerated:
`LegacyStockReadRepository.java` (`RecommendedQtyReadQuery.sql`,
`StockSalesListQuery.sql`/`...CountQuery.sql`) and
`WarehouseStockReadRepository.java` (`WarehouseStockListQuery.sql`,
`WarehouseStockBySkuQuery.sql`, `...CountQuery.sql`). `ArrivalListQuery.sql`
and `PriceReadQuery.sql` deliberately never join `ms_stk` at all (by
documented design constraint) - not applicable to this Audit.

| Query | B1 `WH_CD` condition | B2 `SOLD_QTY` source | B3 `STK_STANDARD` source | B4 `STK_QTY` aggregation | B5 13x multiplication? | B6 GROUP BY/SUM duplication? | Classification |
|---|---|---|---|---|---|---|---|
| `RecommendedQtyReadQuery.sql` (Candidate List, Stock/Sales List, `findBySkus`) | Yes - two explicit joins: `agg.wh_cd='XX'`, `phys.wh_cd='01'` | `agg.sold_qty` (WH_CD='XX') - **correct per Stage 2** | `agg.stk_standard` (WH_CD='XX') - **correct per Stage 2** | `COALESCE(phys.stk_qty, 0)`, single row at `WH_CD='01'` - **`WH_CD='01'` has 0 rows in real Production data (verified this Stage)** | No (both joins are 1:1 via explicit `wh_cd` filter) | No (no GROUP BY across `ms_stk` rows) | **INCORRECT** (current_stock only) |
| `WarehouseStockListQuery.sql` / `...BySkuQuery.sql` / `...CountQuery.sql` | Yes - `wh_cd <> 'XX'` (shows every genuine per-warehouse row) | N/A (not selected) | N/A (not selected) | Not aggregated - lists raw per-warehouse `stk_qty` rows, 1 row per (SKU, WH_CD) by design | N/A - a list view is *supposed* to show all warehouse rows | N/A | **SAFE** |

**B4/B7 detail (the core finding)**: `RecommendedQtyReadQuery.sql`'s own
comment already documents this as a "reduced... for this Demo Instance"
simplification ("seeds only ONE physical warehouse row (WH_CD='01') per
item") and separately flags that Legacy's real `LOGICAL_QTY` calculation
apparently sums `STK_QTY_4..15` - which was "NOT FULLY RESOLVED" during the
original Phase 0 source audit and deliberately not reproduced rather than
guessed at. This Stage's own Snapshot check (SELECT-only,
`gops_snapshot_audit`) confirms both halves of that gap concretely:
- `SELECT COUNT(*) FROM ms_stk WHERE WH_CD='01'` → **0** (of 1,518,842
  rows) - the join this code relies on can never match real data.
- Real physical stock (`STK_QTY` non-null) is spread thinly across
  `WH_CD` values `4` through `15` (12,110 rows total, per Stage 2 §8) -
  exactly the range the original team's own unresolved comment names.

**B7 (Recommended Qty inputs)**: `current_stock=0` (always, per B4)
propagates directly into `RecommendedQtyCalculator`'s inputs for every
real-data item - the single highest-impact real-data compatibility issue
found in this review.

`open_po`/`open_arrival`/`open_ship` (from `agg.po_qty_*`/`arr_qty_*`/
`ship_qty_*`, all `WH_CD='XX'`) were separately re-verified this Stage
against the Snapshot: **100% of `PO_QTY_1`/`ARR_QTY_1`/`SHIP_QTY_1` non-null
values live on `WH_CD='XX'`, 0% on any numbered code** - these three fields
are correctly sourced already, unlike `current_stock`.

## 5. ITEM_STATUS Review (Audit C)

`ItemStatusChip.tsx` (the sole rendering path - used by Candidate List,
Draft, and SKU Detail):

```tsx
if (!status) return null
const label = t(`itemStatus.${status}`, { defaultValue: status })
const color = COLOR_BY_STATUS[status] ?? 'default'
```

- **C1/C5 (unknown value behavior)**: renders the raw string as-is via
  i18next's `defaultValue` fallback - no blank/broken label, no unsafe
  rendering (plain React text, not `dangerouslySetInnerHTML`).
- **C2 (enum cast exception)**: none possible - `itemStatus` is typed
  `string | null` end-to-end in both the backend DTOs (`OrderCandidateResponse`
  et al.) and the frontend types (`orderCandidate.ts`, `orderDraft.ts`,
  `skuDetail.ts`, `stockSales.ts`, `priceChange.ts`) - never a Java or
  TypeScript enum.
- **C3 (API serialization failure)**: none possible - plain String
  throughout the stack.
- **C4 (filter assuming known enum)**: no `ITEM_STATUS`-based filter
  exists anywhere in `OrderCandidateService` (confirmed by source read) -
  nothing to silently exclude an unknown value.
- **C6 (relationship with `DISCON`)**: **the frontend's own
  `COLOR_BY_STATUS` map anticipates `DISCON`/`DISCON_STK`/`ON_HOLD` as
  possible `ITEM_STATUS` string values (red/orange coloring) - re-verified
  this Stage against the Snapshot: none of the three ever occur in real
  data (0 rows each).** Meanwhile, of the 39,551 real items where the
  separate boolean `MS_ITEM.DISCON=1`, the actual `ITEM_STATUS` text is
  `NULL` (16,653), blank (16,329), or even `"NEW"` (6,560) - the two
  fields disagree in the overwhelming majority of genuinely-discontinued
  items. The Chip's color logic never reads `DISCON` at all, only
  `ITEM_STATUS` - so a real discontinued item's Chip would show default
  (or no chip, if `ITEM_STATUS` is blank/null) styling, not the intended
  red warning, in the large majority of real cases.

**Classification: ROBUST** (no crash/serialization/enum risk) **with one
real-data-driven UX gap** (the color-coding logic's intended signal rarely
fires in practice - §C6).

## 6. Supplier/Brand N:M Review (Audit D)

| Feature | D-check | N:M compatibility |
|---|---|---|
| Supplier Master / Brand UI (`master-maintenance-hub`) | Neither screen assumes a 1:1 link between the two - each is browsed/edited independently, keyed by its own code | **Compatible** |
| Official PO short-code (`OfficialPoShortCodeService`/`Repository`) | Keyed by `(codeType, businessCode)` where `codeType` is `SUPPLIER` or `BRAND` independently (per Stage 1/1B/1C source review context) - not a combined Supplier+Brand composite requiring 1:1 | **Compatible** (independent codes, not a joint assumption) |
| Candidate List | See §3 - Supplier is visible and filterable per row; no explicit 1 Brand=1 Supplier assumption in the query or UI, **but** no grouping/warning either | **Compatible with the risk already noted in §3/§7** |
| Draft (`OrderDraftService`/`OrderDraftPersistenceService`) | **See §7 below - a real gap found (no Brand consistency check).** | **Gap found (§7)** |
| Supplier Response | Operates on an already-created Order (single Supplier/Brand by the time it exists) - no independent N:M assumption of its own found | **Compatible** |
| Official PO generation | Reads `order.brandCode`/`order.supplierCode` (already fixed at Draft-creation time, per §7) - inherits Draft's own gap, does not introduce a new one | **Inherits §7's gap** |

## 7. PO Model Review (Audit E)

Traced `OrderDraftService.create()` and `OrderDraftPersistenceService.create()`:

- **E1 (1 Supplier guaranteed)**: **Yes.**
  `OrderDraftService.java:69-74` - `Set<String> supplierCodes = ...; if
  (supplierCodes.size() > 1) throw new MixedSupplierException(...)`.
- **E2 (1 Brand guaranteed)**: **No equivalent check exists.** No
  `brandCodes` set is ever collected or size-checked anywhere in either
  class.
- **E3 (can multiple-Brand SKUs enter one Draft)**: **Yes, confirmed
  possible** - nothing in the Candidate List (§3), `OrderDraftService`, or
  `OrderDraftPersistenceService` prevents it, as long as all selected SKUs
  share one Supplier (which Stage 2 confirms is common: up to 102 Brands
  under one real Supplier).
- **E4 (where the Formal PO's Brand comes from)**:
  `OrderDraftPersistenceService.java:65` -
  `order.setBrandCode(first.brandCd())` - the **first** selected SKU's own
  Brand, silently, with no check that any other selected SKU shares it.
  This is the same `order.brandCode` that later feeds Official PO short-
  code/numbering.
- **E5 (behavior when Detail Brand differs from the Order's own Brand)**:
  **Silent - no error, no warning.** The Order-level `brandCode` simply
  does not reflect the true Brand of every line item; nothing surfaces
  this to the user or blocks the Draft.

**Gulliver-confirmation vs. G-OPS-technical separation**: Stage 2's 227
real POs with a Brand mismatch (§9 of that document) shows this is not a
theoretical edge case - real Legacy operational history already contains
this exact pattern. Whether that pattern is an intentional Legacy business
practice or itself a data-entry anomaly is a Gulliver question (Stage 2
§19); **independent of that answer, G-OPS's own Draft has no mechanism to
even detect the situation today**, which is squarely a G-OPS technical gap,
not something Gulliver confirmation would resolve on its own.

## 8. Scale/Pagination Review (Audit F)

| Screen | Pagination | Search | Default/Max page size | Classification |
|---|---|---|---|---|
| Candidate List | **None** - `GET /api/order-candidates` has no page/size param; `RecommendedQtyReadQuery.sql` has no `LIMIT` | Keyword/Brand/Supplier filters exist, but narrow the *unpaginated* full result | N/A (returns everything matching) | **NOT SCALE SAFE** (confirmed real Brand with 18,596 SKUs, §3 A6/A7) |
| Stock/Sales List | Backend, `LIMIT :limit OFFSET :offset` + a companion `COUNT(*)` query (`StockSalesListQuery.sql`/`...CountQuery.sql`) | Keyword/Brand/Supplier + numeric Stock/Sales range filters | Caller-supplied (not hardcoded in the SQL layer) | **SAFE** |
| Order History | Backend-paginated (`page`/`size` request params confirmed in `OrderHistoryController.java`) | Multiple filters including keyword/status/date | Not re-derived this Stage (out of the Legacy-scale focus) | **SAFE** |
| Supplier Response | Operates on one Order at a time, not a scrollable list of many rows | N/A | N/A | **SAFE (not applicable)** |
| Arrival | Backend, `LIMIT :limit OFFSET :offset` + `ArrivalListCountQuery.sql` | Supplier/Brand/PO/Invoice/SKU/date-range filters | Caller-supplied | **SAFE** |
| Price Change (candidate search) | **None** - `searchCandidates` returns a plain `List<...>`, backed by `PriceReadQuery.sql` (no `LIMIT`) | Item-group/Brand/keyword filters, same unpaginated-result caveat as Candidate List | N/A | **NOT SCALE SAFE** (same root cause/table shape as Candidate List - a Brand with 18,596 SKUs would return in full) |
| Warehouse Stock | Backend, `LIMIT :limit OFFSET :offset` + `WarehouseStockListCountQuery.sql` | SKU keyword/Brand/`WH_CD`/quantity-range filters | Caller-supplied | **SAFE** |

## 9. Search Review (Audit G)

Brand/Supplier/SKU keyword filters exist and are exercised on essentially
every screen reviewed in §8 (Candidate List, Stock/Sales, Warehouse Stock,
Price Change) - consistent with Stage 2's own conclusion that these axes
are real-data-justified (§14 of that document). PO Number search exists on
Order History (G-OPS's own numbering) and Arrival (`poNumber`/`invoiceNumber`
filters in `ArrivalListQuery.sql`). No additional strong candidate search
axis emerged from this code-level pass beyond what Stage 2 already
recorded - no implementation change made, per instruction.

## 10. Manufacturer Terminology Review (Audit H)

`ManufacturerChannel.java`'s own Javadoc states explicitly: `supplierCode`
"reference[s] Legacy Master data" - this Portal-only table (Legacy has no
such Master, per its own comment) is keyed by Supplier identity, not an
independent Manufacturer code. The same pattern was found consistent
across every "Manufacturer"-labeled UI/business concept encountered in
this review (Manufacturer Channel, "メーカーへの発注" Send flow, "メーカー
欠品情報" Stockout info) - all ultimately resolve through `supplierCode`.

**Conclusion: no issue.** G-OPS's Backend identity model correctly treats
"Manufacturer" as UI/business terminology only, backed by the same
Supplier identity Stage 2 confirmed is the only real entity in Legacy -
no separate Manufacturer Master or code column exists anywhere in G-OPS's
own domain model that could create a data-model-level split.

## 11. Findings Matrix

| # | Finding | Area | Severity | Classification |
|---|---|---|---|---|
| 1 | `current_stock` sourced from `ms_stk.wh_cd='01'`, which has 0 rows in real Production data - always resolves to 0 | Legacy Stock Adapter (§4) | **P1** | **REAL-DATA BLOCKER** |
| 2 | Draft/Order has no Brand-consistency validation (only Supplier) - real Legacy data already shows this exact gap's consequence (227 mismatched POs) | Draft / PO Model (§7) | **P1** | **REAL-DATA RISK** |
| 3 | Order Candidate List has no pagination (server or client) - confirmed real Brand with 18,596 SKUs | Candidate List (§3, §8) | **P1** | **REAL-DATA RISK** |
| 4 | Price Change candidate search has the same no-pagination shape as #3 | Price Change (§8) | **P2** | **REAL-DATA RISK** |
| 5 | `MIXED_SUPPLIER_NOT_ALLOWED` only surfaces after Draft-creation submit, not during selection | Candidate List (§3) | **P2** | **UX IMPROVEMENT** |
| 6 | `ItemStatusChip`'s DISCON/ON_HOLD color logic almost never fires against real data; real discontinued items mostly show default styling since `ITEM_STATUS` (not `DISCON`) drives the color | ITEM_STATUS (§5) | **P2** | **REAL-DATA RISK** |
| 7 | `MS_ITEM.ITEM_STATUS` itself contains non-enum values (Legacy Production data characteristic) | ITEM_STATUS (§5, Stage 2) | **P2** | **GULLIVER CONFIRMATION** (not a G-OPS defect) |
| 8 | Whether the 227 real mixed-Brand POs are intentional Legacy practice or a data anomaly | PO Model (§7, Stage 2) | **P2** | **GULLIVER CONFIRMATION** |
| 9 | No Supplier grouping/visual separation within a Brand-filtered Candidate List | Candidate List (§3) | **P3** | **UX IMPROVEMENT** |
| 10 | Manufacturer/Supplier identity model | Terminology (§10) | — | **NO ISSUE** |

## 12. Gulliver Confirmation Items

(Carried forward/expanded from Stage 2 §19, plus this Stage's own finding
#8 above)

1. Is `DEL_FLG` genuinely "`NULL`=active / `1`=deleted" throughout, or does
   a real `0` ever get written elsewhere?
2. What is the intended source/semantics of `MS_ITEM.ITEM_STATUS`'s
   non-enum values?
3. Does "Logical Qty" correspond to an application-computed value derived
   from `ms_stk` (e.g. summing `STK_QTY_4`..`STK_QTY_15`, per this Stage's
   §4 finding), confirming/replacing the original team's own "NOT FULLY
   RESOLVED" note?
4. Are the 227 real mixed-Brand POs an intentional Legacy pattern
   (e.g. a Supplier fulfilling one PO across several of their own Brands)
   or a data anomaly?
5. What are the real, full set of physical-warehouse `WH_CD` values and
   their individual meaning/exclusion rules (Damaged/Private Auction/
   Disposal etc.) - needed to correctly replace the current `WH_CD='01'`
   assumption (Finding #1) with the real aggregation logic.

## 13. Recommended Remediation

**Not implemented this Stage, per instruction - listed only as candidates
for a future, explicitly-authorized remediation round:**

- Finding #1: replace the `phys.wh_cd='01'` join with an aggregation over
  the real physical `WH_CD` set (pending Gulliver confirmation item #5's
  exact exclusion rule).
- Finding #2: add a Brand-consistency check to `OrderDraftService`,
  mirroring the existing `MixedSupplierException` pattern (a
  `MixedBrandException`, or a documented decision that mixed-Brand
  same-Supplier Drafts are intentionally allowed with the Order's Brand
  field explicitly treated as "primary Brand only").
- Findings #3/#4: add server-side pagination to `GET /api/order-candidates`
  and the Price Change candidate search, matching the `StockSalesListQuery.sql`
  pattern already proven elsewhere in this same codebase.
- Finding #5: surface `MIXED_SUPPLIER_NOT_ALLOWED` (and any future
  mixed-Brand check) proactively during selection, not only after
  Draft-creation submit.
- Finding #6: have `ItemStatusChip` (or its caller) also consider
  `DISCON` directly, not only the `ITEM_STATUS` string, so a genuinely
  discontinued real item reliably shows the intended warning.

## 14. Recommended Stage 4

Not decided here, per instruction - these findings are handed off for
ChatGPT/Techlead review to determine whether/which items proceed to a
targeted remediation Stage.
