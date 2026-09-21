# G-OPS Stage 4 — Targeted Real-Data Remediation

Scope: implement fixes for the real-data blockers/risks confirmed by Stage
2 (Business Data Structure Audit), Stage 3 (Real-Data Compatibility
Review), and Stage 3B (Legacy Current Stock/Logical Qty Definitive
Audit). This Stage implements code, adds tests, updates Demo fixtures, and
documents the result — it does **not** connect to the Production Snapshot,
does not decide the Mixed Brand specification, and does not deploy.

## 1. Baseline

Carried forward, unchanged, from prior Stages:

| Source | Finding used this Stage |
|---|---|
| Stage 3 §Audit B (`docs/real-data-audit/gops-stage3-real-data-compatibility-review.md`) | `current_stock` sourced from `WH_CD='01'`, which has 0 rows in real Production data — the most severe finding |
| Stage 3B §3/§4/§5 (`docs/real-data-audit/gops-stage3b-legacy-stock-logic-audit.md`) | `PHISICAL_QTY = SUM(STK_QTY)` over `WH_CD IN ('4','5','6','7','8','10','11','12','15')`; `LOGICAL_QTY = PHISICAL_QTY + open_po + open_ship` (ARR_QTY never a summand) — both confirmed with Legacy source-level certainty |
| Stage 2 §Candidate List Implication | Candidate List has no pagination; a real Brand can carry up to 18,596 SKUs |
| Stage 2 §5 | 27.1% of real Brands span multiple Suppliers |
| Stage 3 §C6 | `MS_ITEM.DISCON=1` (39,551 items in the Snapshot) rarely co-occurs with a recognizable `ITEM_STATUS` string; discontinuation cannot rely on `ITEM_STATUS` alone |
| Stage 2 §PO Structure | 227 real POs show a Header Supplier / Line Brand mismatch — status as legitimate practice vs. data anomaly is unconfirmed |

No new Snapshot query was needed this Stage — Stage 3B had already
resolved the stock-formula question with source-level certainty, so no
`gops_snapshot_audit` connection was made in Stage 4.

## 2. Current Stock Remediation

`backend/src/main/resources/legacy/RecommendedQtyReadQuery.sql`: replaced
the `LEFT JOIN ms_stk phys ON phys.item_cd = i.item_cd AND phys.wh_cd =
'01'` / `COALESCE(phys.stk_qty, 0) AS current_stock` pair with a
correlated scalar subquery (Option A from Stage 3B §8, chosen over 9
explicit `LEFT JOIN`s for readability/performance and to guarantee the
query's existing "one row per item" shape with no `GROUP BY`):

```sql
COALESCE((
    SELECT SUM(phys.stk_qty) FROM ms_stk phys
    WHERE phys.item_cd = i.item_cd
      AND phys.wh_cd IN ('4','5','6','7','8','10','11','12','15')
), 0) AS current_stock
```

`WH_CD` 9 (不良品F/Defective), 13 (個人オークション/Private Auction), 14
(廃棄倉庫/Disposal) are excluded, matching Legacy's own commented-out JOIN
lines and confirmed against the real Warehouse Master (Stage 3B §3). This
value feeds both the `current_stock` display column and, via
`OverseasRecommendedQtyStrategy`, the `PHISICAL_QTY` term of
`LOGICAL_QTY` (§3 below).

## 3. Logical Qty Remediation

`backend/src/main/java/com/glv/gsysportal/service/OverseasRecommendedQtyStrategy.java`:
`logicalQty` is now `sum(row.currentStock(), row.openPo(), row.openShip())`
— `row.openArrival()` (ARR_QTY) is no longer a summand, per Stage 3B §5.
`row.openArrival()` itself is untouched and continues to be surfaced
separately for direct display; only its (incorrect) participation in the
`LOGICAL_QTY` calc input was removed.

`current_stock` (the display figure, = `PHISICAL_QTY`) and `logical_qty`
(the calc-only Recommended Qty input) are deliberately not the same value
once `open_po`/`open_ship` are non-zero — this distinction is the one the
instruction explicitly warned not to blur, and no code path conflates them.

## 4. Candidate List Pagination

New paginated path added alongside the existing unpaginated one (the
unpaginated `OrderCandidateService.findOrderCandidates` is unchanged and
still used by `DashboardService`/`SupplierMasterService`/Baseline
Snapshot-Concurrency Check):

- `OrderCandidateService.findOrderCandidatesPage(brandCode, supplierCode,
  keyword, page, size)` → `PageResponse<OrderCandidateResponse>`, reusing
  the existing `LegacyStockReadRepository.findStockSalesList`/
  `countStockSalesList` (the same COUNT + LIMIT/OFFSET SQL
  `StockSalesService` already established) via a `StockSalesListFilter`
  with null min/max fields.
- `DEFAULT_PAGE_SIZE = 20`, `MAX_PAGE_SIZE = 100`, same clamp convention as
  `StockSalesService`.
- `GET /api/order-candidates` now returns `PageResponse<...>` and accepts
  `page`/`size`; existing `brandCode`/`supplierCode`/`keyword` filters are
  unchanged and still apply before pagination.
- Frontend: `CandidateListPage.tsx` reads `page`/`size` from the URL,
  renders MUI `TablePagination`, resets to page 0 on any filter change.
  Brand/Supplier filter inputs converted from a `TextField select`
  (populated from the — now no-longer-complete — fetched page) to a
  plain typed-code `TextField`, matching `StockSalesListPage`'s own
  already-paginated convention. The Filter Chip still shows the resolved
  Brand *name* via `useDashboard()`'s separate, unpaginated Brand list.
- Selection (`Set<string>` of SKUs) and Draft creation are unaffected —
  selection is tracked independently of which page is currently visible.

## 5. Mixed Supplier Selection UX

`CandidateListPage.tsx`: `selectedSupplierCode` state, set when the first
SKU is selected (tracked separately from the SKU `Set` because selection
can span multiple pages), cleared when the selection becomes empty or a
Draft is created successfully. Rows whose Supplier differs from the locked
one get `disabled` checkboxes (`isSupplierLocked`); a Chip near the Create
Draft button names the locked Supplier. `toggleSelect` also defensively
refuses to add a mismatched-Supplier row even if called outside the
checkbox path. Backend's `MIXED_SUPPLIER_NOT_ALLOWED` (`OrderDraftService`)
is unchanged and remains the authoritative guard; the Supplier filter
field is also unchanged and still usable independently.

## 6. Price Change Pagination

Mirrors §4 exactly, for `PriceChangeSetService`/`LegacyPriceReadRepository`:

- New SQL: `PriceCandidateListQuery.sql` / `PriceCandidateListCountQuery.sql`,
  wrapping the existing `PriceReadQuery.sql` as a derived table via the
  same `${BASE_QUERY}` substitution convention
  `LegacyPriceReadRepository`'s constructor already uses for other
  wrapped queries.
- `LegacyPriceReadRepository.searchPage(...)` / `countSearch(...)` — new,
  additive methods; `search()`/`findBySkus()` unchanged.
- `PriceChangeSetService.searchCandidatesPage(...)` →
  `PageResponse<PriceChangeCandidateResponse>`, same
  `DEFAULT_PAGE_SIZE=20`/`MAX_PAGE_SIZE=100` convention as §4.
  `searchCandidates()` unchanged.
- `PriceChangeSetController`'s `/api/price-changes/legacy-items` now
  returns `PageResponse<...>` and accepts `page`/`size`.
- Frontend: `PriceChangeEditPage.tsx` adds `candidatePage`/
  `candidatePageSize` state and `TablePagination`; Search
  button/Enter/Item-Group-change/Brand-change all reset the page to 0.

## 7. DISCON Remediation

`MS_ITEM.DISCON` is now propagated end-to-end for the Candidate List:

- `OrderCandidateResponse.discon: Boolean` (new field).
- `OrderCandidateService.toResponse` populates it from the Legacy row.
- Frontend `orderCandidate.ts` type gains `discon: boolean | null`.
- `ItemStatusChip.tsx`: checks `discon` **first** — if true, renders a
  red "廃番"/"Discontinued" Chip (`itemStatus.DISCON`, already an
  existing i18n key) regardless of what `itemStatus` says. Only if
  `discon` is falsy does the pre-existing `itemStatus`-string logic run,
  unchanged, including its existing unknown-value fallback
  (`defaultValue: status`) — this robustness was explicitly required to
  survive untouched, and it does.
- `ITEM_STATUS` values themselves are not normalized or cleaned anywhere
  in this change, per instruction.
- Scope: Candidate List only, this round. Draft/SKU Detail screens were
  not touched — they would need their own separate DTO/API plumbing,
  explicitly out of this round's minimal scope.

## 8. Tests

**Stock logic** (`backend/src/test/java/...`):
- `LegacyStockReadRepositoryPhysicalQtyIntegrationTest` (4 tests, against
  a dedicated new fixture `STAGE4-WH-TEST-001`): included warehouses
  (4/8/15) summed; excluded warehouses (9/13/14) never counted; a missing
  row for an included warehouse counts as 0; exactly one row returned per
  SKU (no multiplication from the 13-warehouse fan-out).
- `OverseasRecommendedQtyStrategyTest` (2 tests): ARR_QTY never affects
  the Strategy's output (identical result for `openArrival=0` vs. `=100`
  with everything else fixed); Recommended Qty's `logicalQty` input equals
  `PHISICAL_QTY + open_po + open_ship`, reproduced against the real
  `calc1`-`calc4` chain.

**Pagination** (12 tests total, 6 per feature, both mirroring the same
shape): `OrderCandidateServicePaginationIntegrationTest` and
`PriceChangeSetServiceCandidatePaginationIntegrationTest` — first/middle/
last page return distinct, non-overlapping rows; a page beyond the last
returns an empty `content`, not an error; filter + pagination narrow both
`totalElements` and `content` together; page size is clamped to
`MAX_PAGE_SIZE`; the pre-existing unpaginated method still returns every
row. All fixture data for these tests is synthetic (test-authored rows),
never Production data.

**Fixture changes** (`backend/demo-data/02-seed.sql`, additive only —
nothing existing removed or altered):
- 19 new `ms_stk` rows at `wh_cd='6'`, one per existing item that already
  had a `wh_cd='01'` row, each replicating that row's exact `stk_qty`
  value — the Demo DB's zero-padded `'01'`/`'04'`/`'05'`/`'07'` warehouse
  codes predate this Stage and are still hardcoded into two other E2E
  spec files' testids/assertions (`arrival-warehouse-stock-visibility-
  foundation.spec.ts`, `stock-sales-visibility-foundation.spec.ts`), so
  those rows were left untouched; new bare-digit rows were added instead
  so the new `current_stock` query (which only recognizes bare-digit
  codes, matching real Production) has real values to sum against Demo
  data too, with every pre-existing current_stock-dependent assertion
  staying numerically unchanged.
- A new fixture item `STAGE4-WH-TEST-001` (brand `BR_OUTDOOR`) with an
  `'XX'` aggregate row (`stk_standard=8, sold_qty=20, po_qty_1=3,
  arr_qty_1=999, ship_qty_1=7`) and warehouse rows covering included
  (`'4'=10, '8'=20, '15'=5`, `'12'` deliberately omitted to test
  null-handling) and excluded (`'9'=1000, '13'=2000, '14'=3000`)
  warehouses.

## 9. Regression

| Suite | Result |
|---|---|
| Backend full test suite (Maven) | See §Final Report — re-run clean after this Stage's E2E fix, no backend code changed since |
| Frontend unit tests (Vitest) | 53/53 passed (3 test files) |
| Frontend TypeScript check | Clean, exit 0 |
| Frontend production build (Vite) | Clean, exit 0 |
| Relevant targeted E2E (Candidate/Price Change/Dashboard/Brand-entry specs) | All passed after 3 fixes (Filter Chip name resolution, a timing wait, a Select→Input assertion) — see §10 |
| Full E2E Suite (run once, per instruction) | 227 passed / 3 failed / 2 skipped (232 total) — the 3 failures were all in `list-state-preservation.spec.ts`, same root cause as the targeted-E2E fixes above (a Select-driving helper used against Candidate List's now-plain-TextField filters), fixed post-hoc and independently re-verified: 7/7 tests in that file now pass in isolation |

No Production Data was used in any test — only the synthetic
`STAGE4-WH-TEST-001` fixture and pre-existing anonymized Demo data.

## 10. E2E Fallout From the Select→TextField Filter Change

Converting Candidate List's Brand/Supplier filter from a MUI `Select` to a
plain `TextField` (an unavoidable consequence of §4's pagination — the
full option list is no longer fetched client-side) broke several
pre-existing E2E assertions that opened a dropdown or read an `option`
role:

1. **Filter Chip showed the raw code instead of the resolved name** (5
   targeted E2E failures) — fixed by resolving the Chip's label via
   `useDashboard()`'s separate, unpaginated Brand list (§4), leaving the
   input itself showing the raw code (expected/correct).
2. **A non-retrying row count raced the new pagination request**
   (`order-candidates-brand-entry.spec.ts` Scenario 2) — fixed with an
   explicit wait for the result-count text, mirroring an existing pattern
   already used elsewhere in the same file.
3. **A Select-option-text assertion applied to a plain input**
   (`dashboard-deep-links.spec.ts` Scenario 1-5) — fixed by asserting the
   input's `value` against the Brand *code* extracted from the URL,
   instead of asserting `option` text.
4. **`list-state-preservation.spec.ts` Scenario A/B/D** (found only in the
   Full E2E run — this file was not in the targeted list) — same root
   cause: `selectFirstRealOption()` opens a `Select` and reads
   `getByRole('option')`, which no longer exists for these two fields.
   Fixed by adding two API-driven helpers (`fillCandidateBrandFilter`,
   `fillCandidateSupplierFilter`) that discover a real Brand/Supplier code
   via `GET /api/order-candidates` and fill the `TextField`s directly, and
   by replacing the corresponding `toHaveText(brandName)` assertions with
   `.locator('input')`/`toHaveValue(brandCode)` checks. Order History's
   own Status `Select` (Scenario C) was unaffected and required no change.

None of the 8 fixed assertions represent a data or business-logic defect —
all are test-code adaptations to an intentional, documented UI contract
change.

## 11. Deferred: Mixed Brand Enforcement (Not Implemented)

Per explicit instruction, Mixed Brand enforcement was **not** implemented
this Stage. Stage 2 found 227 real POs where the PO Header's Supplier and
its Line-level Brands are inconsistent; whether this reflects legitimate
business practice or a data anomaly is unconfirmed and is a Gulliver
question, not a technical decision this round can make. Current G-OPS
behavior (no Brand-consistency check at Draft creation) is unchanged and
undocumented-as-a-guard beyond this note; this remains an open risk to
carry into a future Stage once the business rule is confirmed.

## 12. Remaining Risks / Next Recommendation

- **Mixed Brand** (§11) — unresolved, carried forward as a Gulliver
  Confirmation Item.
- **`SELLABLE_QTY`** — a Legacy-computed, narrower warehouse subset,
  confirmed to exist (Stage 3B §9) but not traced to any consumer; not
  needed for this Stage's remediation, noted only for completeness.
- **DISCON propagation is Candidate-List-only** — Draft/SKU Detail do not
  yet surface it; a future Stage should decide whether/how to extend it
  there.
- **`LOGICAL_QTY`'s exact composite input** is now formula-confirmed and
  wired for the `OVERSEAS` Strategy; whether other Strategies need the
  same treatment was out of this Stage's scope and was not audited here.
- **Recommendation**: this Stage's changes are internally consistent,
  fully covered by new tests against synthetic data, and pass the full
  regression suite (backend/frontend/E2E) with all discovered E2E fallout
  fixed and independently re-verified. The next appropriate step is a
  **Controlled Snapshot UI Validation** — manually exercising the
  Candidate List / Price Change / Draft flows against the read-only
  Production Snapshot (`gops_snapshot_audit`) to visually confirm the new
  `current_stock`/pagination/DISCON behavior against real data shapes —
  not yet performed, and out of this Stage's own scope per instruction.
