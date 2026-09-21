# G-OPS Stage 5C — Controlled Production Snapshot UI Validation (Resume)

Scope: resume Stage 5's real-data UI validation using the Production-like
Snapshot environment and Safety Gate built in Stage 5B. **VALIDATION
ONLY** — no Application code was changed this Stage (`git status`
confirms `backend/`/`frontend/` clean throughout). Findings below are
recorded, not fixed, per instruction.

## 1. Environment / Safety Verification

- Restarted `gsys-prod-snapshot-20260916-ci` (port 33200,
  `lower_case_table_names=1`); re-verified `SHOW GRANTS FOR
  'gops_snapshot_audit'@'%'` = SELECT-only on `goo_prod_snapshot_20260916`
  before any other action.
- Started G-OPS with `snapshot-validation` + `local` profiles and the
  exact-match env var. Confirmed the `SNAPSHOT VALIDATION MODE ACTIVE`
  WARN log present on every startup this Stage.
- Confirmed via successful, working queries throughout this session that
  Legacy traffic reached the Snapshot (33200) and Portal traffic stayed on
  local PostgreSQL (54321) — consistent with Stage 5B's own live-connection
  verification; Flyway log confirmed its target was Portal only.
- Both Snapshot containers stopped at the end of this Stage (data/volumes
  preserved). Dev servers (backend, frontend) stopped at the end.

## 2. Brand-First Validation

Dashboard → Brand List → Brand → Candidate SKU was attempted as specified.
**Both Dashboard and the Brand List page (`/candidates` with no Brand
selected) are broken at Production scale** — see §13 Finding 1/2. Once a
Brand is selected via direct URL (the only reachable path), Candidate List
itself works correctly (§6). Brand-first navigation via the UI's own
entry points (Dashboard tile, "発注候補" nav, Brand List page) could not be
completed end-to-end this Stage because the Brand List step itself never
finished loading within any reasonable wait.

## 3. Brand List

Not validatable — the Brand List page itself shares Finding 1/2's root
cause (§13) and never rendered during this Stage's testing (repeated
attempts, up to several minutes each). Active-filtering / soft-deleted
exclusion at the Brand List level could not be checked.

## 4-6. Candidate Pagination (by Brand size)

All four size buckets were reached directly (via `?brandCode=`, bypassing
the broken Brand List) and validated:

| Bucket | Active SKUs | Result | First-page timing |
|---|---|---|---|
| Small | 20 | Correct, full render, `20件` matches exactly | Fast (<1s observed) |
| Medium | 198 | Correct, `1–20` shown | ~2.4s |
| Large | 1,188 | Correct, `1–20` shown | ~5.7s |
| Very Large | 3,880 | Correct — **`1–20 of 3,880`, confirmed NOT a full browser load** | ~17.2s |

Very Large Brand page 2 (offset 20): also ~17.1s — **query cost does not
decrease with offset**, indicating the underlying correlated `current_stock`
subquery is evaluated across the full Brand-filtered row set before the
outer `LIMIT`/`OFFSET` is applied, not truly bounded by pagination (§13
Finding 6). Keyword search (a highly selective `item_cd LIKE`) was
separately fast in isolation (~4s) but slowed considerably under
concurrent load from other in-flight slow requests (§13 Finding 7).
Pagination correctness (distinct, non-overlapping rows across pages) was
directly confirmed for the Very Large bucket.

## 7. Performance (Measured)

| Operation | Time | Judgement |
|---|---|---|
| Dashboard (`GET /api/dashboard`) | 351–407 seconds (3 measurements) | **重大 (Critical)** |
| Brand List page | Same backing query — effectively identical | **重大 (Critical)** |
| Candidate List, Small Brand | <1s | 快適 (Comfortable) |
| Candidate List, Medium Brand | ~2.4s | 許容 (Acceptable) |
| Candidate List, Large Brand | ~5.7s | 要改善 (Needs improvement) |
| Candidate List, Very Large Brand (any page) | ~17s | **要改善〜重大** |
| Candidate keyword search (isolated) | ~4s | 許容 |
| Candidate keyword search (under concurrent load) | ~35s | 要改善 (reveals pool contention, §13 Finding 7) |
| Price Change candidate search | ~112ms | 快適 |
| Stock/Sales list (Brand-scoped, 52 SKUs) | ~1.8s | 許容 |
| Arrival List (full, unfiltered, 17,722 rows) | ~521ms | **快適** |
| SKU Detail (`ordering-context`, single SKU) | 174.5 seconds, ends in error | **重大 (Critical)** |

The contrast is informative: screens whose queries never join `ms_item`'s
`current_stock` correlated subquery (Price Change, Arrival) perform
excellently even at full real scale; screens that do (Dashboard,
unfiltered Brand List, Candidate List at large Brand sizes, SKU Detail)
degrade severely or fail outright.

## 8. Multi-Supplier Brand

A real Brand with 13 distinct active suppliers across 52 SKUs was used
(identified via read-only aggregate SQL, not named in this document).
Confirmed, in the live UI, all of:
- Supplier diversity genuinely visible on a single page (8 distinct
  supplier codes among the first 20 rows).
- Selecting the first SKU correctly shows a Supplier Lock Chip naming the
  locked supplier code.
- Attempting to select a different-supplier row was correctly **blocked**
  (selection count stayed at 1, confirmed via the "選択した1件で..." button
  label, not merely visual inspection).
- Selecting a same-supplier row was correctly **allowed** (count became
  2).

Backend's `MIXED_SUPPLIER_NOT_ALLOWED` guard was not separately
re-exercised this Stage (no Draft was created, per instruction — see §9
below); the UI-level block already prevents the scenario that guard
exists to catch. No Production-derived Draft was created or persisted, in
compliance with the write restriction.

## 9. Current Stock

- **Multi-warehouse case**: a real item's `current_stock` was
  independently pre-computed via read-only SQL (`PHISICAL_QTY` formula,
  summed across 2 populated included warehouses) before viewing the UI.
  **UI value matched the SQL-computed value exactly.**
- **Zero-stock and single-warehouse cases**: identified via SQL for
  completeness (present and well-formed in the real Snapshot), but not
  independently re-confirmed against the live UI this Stage due to time/
  connection-pool constraints encountered later in the session — not a
  contradiction, simply not separately re-verified beyond the multi-
  warehouse case above.
- No individual SKU value is recorded in this document beyond the
  qualitative "matched exactly" result.

## 10. Recommended Qty / ARR Exclusion

A real item with both open PO qty > 0 **and** open Arrival qty > 0
simultaneously was used (identified via read-only SQL). Findings:

- **Current Stock**: UI value matched the independently pre-computed SQL
  `PHISICAL_QTY` value exactly.
- **Core Recommended Qty calculation**: unaffected by this Stage's
  Finding 5 below — `OverseasRecommendedQtyStrategy` (Stage 4) takes the
  raw Legacy row directly and correctly excludes `openArrival` from its
  `logicalQty` input, confirmed by source (unchanged since Stage 4, not
  re-derived by hand this Stage since that would require reimplementing
  the formula parser).
- **New finding**: the UI's **displayed "発注残" (Open PO) column is
  incorrect** — see §13 Finding 6. It is not the raw open-PO quantity;
  it silently adds open-Arrival quantity on top, confirmed with this
  exact real item (SQL-computed raw open PO vs. the value actually
  rendered differed by exactly the item's open-Arrival quantity). This is
  a display-field bug, separate from and not affecting the core
  Recommended Qty number itself.

## 11. DISCON

A real `DISCON=1`, `ITEM_STATUS=NULL` item was searched. **UI correctly
displayed the red "廃番" (discontinued) chip**, confirming DISCON is
checked ahead of (and independent of) the unreliable `ITEM_STATUS`
string, exactly as designed in Stage 4. Comparison against non-DISCON
items (seen throughout this Stage's other checks — Small/Medium/Large/
Very-Large Brand browsing) confirmed normal/欠品/長期欠品 chips render
correctly and "廃番" never appears for non-DISCON items.

## 12. Price Change

Brand-scoped search (52-item real Brand) confirmed: correct count,
correct `1–20 of 52` pagination, **fast (~112ms)** even though scoped to
the same Brand that took 17s in Candidate List — confirming Price
Change's simpler query (no `current_stock` correlated subquery) does not
share Candidate List's performance problem. Keyword/Item-Group-filter
combinations were not separately exercised this Stage (time constraint);
base search and pagination are confirmed correct and fast. **No write
operation was performed** — no new Change Set was created (only an
existing draft's read-only search UI was used, per the explicit
restriction); one existing draft (`#9`) failed to render within a
reasonable time for reasons not diagnosed (a different draft, `#1`,
rendered correctly and was used for the rest of this section) — noted as
an unexplained, low-confidence observation, not further investigated per
this Stage's own "do not fix, do not deep-dive beyond validation" scope.

## 13. Stock / Sales

Brand-scoped (52-item real Brand) list confirmed correct: exactly 52 rows
for 52 SKUs (**no 13x multiplication** from `ms_stk`'s per-warehouse row
structure — the specific risk this section was checking), all listed
columns (Current Stock, Sold Qty, Open PO, Open Arrival dates) populated
with plausible real values, ~1.8s response time.

## 14. SKU Detail

**Broken at Production scale** — see §13 Finding 4. Attempted for the
same multi-warehouse item already validated in §10; the page never
rendered (stuck on a loading indicator) because its backing
`GET /api/items/{sku}/ordering-context` call took 174.5 seconds and then
failed with an unhandled exception (the same connection-abort pattern as
the Dashboard finding). Warehouse breakdown, Expected Arrival,
Manufacturer Stockout, and PO History sections could not be visually
confirmed this Stage as a result.

## 15. Arrival

**Fully validated, no issues.** Full, unfiltered list (17,722 rows —
exact match to Stage 1C's documented `tr_arr` count) loaded correctly
paginated (`1–20 of 17,722`) in ~521ms. Real Brand/Supplier/PO/Invoice
data rendered correctly. This query deliberately never joins
`ms_item`/`ms_stk` (confirmed via its own source comment, Stage 5A §3) —
its excellent performance here is a direct, positive confirmation of that
design choice's value at real Production scale.

## 16. Official PO Preflight

The specific concern this section exists to check — whether the
case-insensitive Snapshot environment (Stage 5B) actually resolves
`OfficialPoPreflightReadRepository.findExistingItemCodes()`'s lowercase
`ms_item` query correctly — was confirmed directly: `SELECT item_cd FROM
ms_item WHERE item_cd IN (...)` run against the Snapshot correctly
returned exactly the real SKUs from a mixed real/fake list and excluded
the fake one. The full Official PO UI flow (beyond this specific SQL
pattern) was not separately exercised this Stage due to time constraints;
the narrow, Stage-5A-motivated question this section targeted is
resolved.

## 17. Desktop Visual Review

One clean screenshot was reviewed (Stock/Sales, real Brand data, 1568px
width): no column overflow, appropriate column widths, good data
density, comfortable loading state. Other screens (Candidate List, Price
Change, Arrival) were confirmed structurally correct via text-content
inspection (not full screenshot review) due to a Chrome extension/CDP
screenshot-capture instability that emerged partway through this Stage's
testing (repeated `Page.captureScreenshot` timeouts, unrelated to G-OPS
itself — confirmed by the same instability appearing on freshly-opened
tabs with no prior heavy activity). Dashboard, Brand List, and SKU Detail
could not be visually reviewed at all, since none of them rendered
(§2/§3/§14).

## 18. Mobile Visual Review

**Not completed.** The same CDP screenshot instability (§17) prevented
reliable mobile-viewport (390×844) screenshot capture this Stage. This is
recorded honestly as incomplete, not as "no issues found" — mobile
responsiveness for Candidate List, Supplier lock, Stock/Sales, SKU
Detail, and Price Change remains unverified against real Production data
and should be attempted again in a future session.

## 19. Screenshots

**No screenshots were saved or committed.** Every screen exercised this
Stage displays real Production SKU codes, Brand codes, Supplier codes,
and/or item descriptions, none of which can be safely masked without
substantial editing effort; per instruction, the safe default when
masking isn't practical is to save no screenshot at all, which was
applied uniformly here.

## 20. Findings (DO NOT FIX — Classification Only)

| # | Finding | Severity | Category |
|---|---|---|---|
| 1 | Dashboard (`GET /api/dashboard`) takes 351–407 seconds against real Production scale and frequently ends in a client-disconnect error (`AsyncRequestNotUsableException`) rather than a clean response. Uses the unpaginated `findOrderCandidates` path. | **P0** | **PERFORMANCE / REAL-DATA BUG** |
| 2 | The Brand List page (`/candidates` with no Brand selected — the primary Brand-first entry point) shares Finding 1's exact backing query and is equally broken; Brand-first navigation cannot complete via the UI's own entry points at Production scale. | **P0** | **REAL-DATA BLOCKER** |
| 3 | Every `CandidateListPage` visit (any Brand, any entry point) triggers the same catastrophic `/api/dashboard` fetch in the background (via `useDashboard()`, used only for Filter Chip brand-name resolution) — even though the visible paginated table itself is fast and correct, the background fetch eventually freezes the browser tab. | **P0** | **REAL-DATA BUG** |
| 4 | SKU Detail's `GET /api/items/{sku}/ordering-context` takes 174.5 seconds and fails, even for a single, maximally-selective SKU lookup — SKU Detail is effectively unusable against real Production data. | **P0** | **PERFORMANCE / REAL-DATA BUG** |
| 5 | `FormulaParser` fails to parse ~4.6% (2,593/56,428) of real, populated `FORMULA_11` values that use a different cell-reference/multiplier pattern than the one pattern Demo data and prior testing ever exercised — confirmed via repeated real "Expected 2-3 multipliers... found 0" errors in the application log during this Stage's own testing. | **P1** | **REAL-DATA BUG** |
| 6 | Candidate List's displayed "発注残" (Open PO) column silently adds open-Arrival quantity to open-PO quantity (`sum(row.openPo(), row.openArrival())` in `OrderCandidateService.toResponse`) — confirmed with a real item where the two differ by exactly the Arrival amount. Does not affect the core Recommended Qty calculation, which correctly uses only the raw PO value. | **P1** | **REAL-DATA BUG** |
| 7 | Candidate List/Stock-Sales query cost does not decrease with pagination offset and scales with total Brand size (~17s at both page 1 and page 2 of a 3,880-SKU Brand) — the `current_stock` correlated subquery appears to run across the full Brand-filtered set before `LIMIT`/`OFFSET` narrows it, not bounded by the page size actually returned. | P1 | PERFORMANCE |
| 8 | The Portal connection pool (`prototype-readwrite-pool`, max 5) was observed exhausted under this Stage's own concurrent multi-tab testing combined with slow Legacy-side queries, causing unrelated, otherwise-fast requests to fail with 30-second timeouts — a realistic risk under genuine concurrent multi-user load given Findings 1/4's query latencies. | P1 | PERFORMANCE |
| 9 | Current Stock (multi-warehouse case), the core Recommended Qty ARR-exclusion logic, DISCON-first display, Multi-Supplier Selection UX, Price Change pagination, Stock/Sales pagination (no 13x multiplication), and Arrival List (full real scale) all validated correctly against real Production data with no issues found. | — | NO ISSUE |

## 21. Mixed Brand

Not separately re-observed this Stage (time did not permit reaching a
same-Supplier/multi-Brand real scenario in the live UI). Stage 5A's own
real-data evidence (24.8% of Suppliers span multiple Brands, up to 103)
stands unchanged and is not re-derived or contradicted here. No
enforcement logic was added or considered; "1 PO = 1 Brand" remains an
open, undecided question, per instruction.

## 22. Regression

Not applicable — no Application code was changed this Stage (confirmed
via `git status`), so no regression suite run was required or performed,
per instruction.
