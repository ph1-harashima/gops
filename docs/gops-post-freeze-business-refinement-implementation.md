# G-OPS Post-Freeze Business Refinement / Implementation 1

Mobile Approval UX improvement + BR-08 manual PO number UI cleanup + Stockout/Long-term Stockout Expected Arrival・Restock Date, implemented against `docs/gops-20260917-business-requirements-re-audit.md` (commit `04b0452`) as Source of Truth.

This is a **Business Refinement round after the Phase1 Freeze**, not a reopening of Phase1 scope and not a sign the Freeze failed - see `docs/gops-phase1-freeze.md` §16 for the one-line framing kept there. Everything below stays inside the same absolute constraints every prior round observed: 0 Legacy Source changes, 0 Legacy DB writes, 0 Production/UAT connection, 0 Production deploy, 0 SafetyGuard changes, no Supplier/Brand Data Model redesign, no Official PO Short Code Master redesign, no Domestic Recommended Qty Formula change.

## 1. Baseline / Source of Truth

- Source of Truth: `docs/gops-20260917-business-requirements-re-audit.md` (commit `04b0452 9/17 Business Requirements re-audit + Mobile Approval / Stockout Date design`)
- Implementation started from that same commit; no other doc took priority over it.

## 2. Scope (3 items, per the Source of Truth)

**A. Mobile Approval UX improvement** - fix the Order Detail header (title/back-link wrapping, status chip overflow) that broke at 375-430px; add a Mobile Card layout to the Approval Waiting Order List (previously a raw Desktop Table); keep the existing SKU Card layout and Sticky Action Bar (already working per the Re-Audit) unchanged; no Workflow/Permission change.

**B. BR-08 Formal PO Number cleanup** - remove the old manual PO number input/confirm UI residue.

**C. Stockout / Long-term Stockout Expected Arrival/Restock Date** - distinguish Type A "Legacy Expected Arrival" (READ ONLY, from Legacy `TR_ARR.ETA`/`ETA_WH`, shown as "入荷予定日") from Type C "Manual Expected Restock" (Portal-only, SKU-keyed, editable, with an explicit "未定"/Unknown state distinct from not-yet-set, shown as "再入荷予定日"). Legacy always wins display priority; Manual data is never overwritten. Surfaced on Candidate List, SKU Detail (with edit), Approval Detail, and Stock/Sales.

## 3. Part A: Mobile Approval UX

### 3.1 Order Detail Header (`frontend/src/features/history/OrderHistoryDetailPage.tsx`)

The single wrapping `Stack direction="row"` (Back button / title+number / Portal管理番号 Chip / Status Chip / Channel Chips / Attention Chips all on one row) overflowed at 375-430px because the title's embedded PO number and the Status Chip had nowhere to wrap to without clipping (Re-Audit §3.2/§4.1).

Fixed with a new `isMobileHeader` flag (`useMediaQuery(theme.breakpoints.down('sm'))`, i.e. below 600px - deliberately narrower than the page's own `isCardLayout` flag which uses `md`, since the header itself is fine at tablet widths and only needed restructuring on true phone widths) that renders a **3-row Mobile Header** instead, matching the Re-Audit's own §4.1 design:

1. Back link (`発注一覧へ戻る`) alone on its own row.
2. A static title (`発注詳細`, no embedded number) alone on its own row.
3. The Portal管理番号 Chip + the actual number (`prototypePoNo`/`draftNo`, `wordBreak: 'break-all'`) + the Status Chip together, wrapping freely.
4. A 4th row for the remaining Channel/Region/Attention Chips (only rendered when at least one exists).

The Desktop single-row layout is completely unchanged (same JSX, same Chip nodes - extracted into shared variables so both branches render identical Chips rather than duplicating JSX). `data-testid="order-detail-header-mobile"` marks the new Mobile branch for tests. No overflow at 375/390/430px (verified - §8.1).

### 3.2 Order History / Approval Waiting List → Mobile Card layout (`frontend/src/features/history/OrderHistoryListPage.tsx`)

Added the same `isCardLayout` (`useMediaQuery(theme.breakpoints.down('sm'))`) pattern already established by `OrderCandidateBrandListPage`. Below `sm`, the 12-column Desktop Table is replaced by a `Stack` of `Card`s (`data-testid="order-history-cards"`, each row `data-testid="order-history-row-{id}"`), one per Order, showing: Portal管理番号 + Status Chip (header row), then 正式PO番号, Revision, メーカー, ブランド, 発注日, SKU数, 発注数量合計, 発注金額, 更新日時, and 確認事項 (only when present) as label/value rows. Tapping a Card navigates to Order Detail, identical to a Desktop row click. The Desktop Table branch is completely unchanged (same JSX, same column set).

**A genuine layout bug found and fixed while building this**: the page's pre-existing outer container used a rigid `height: '100%'` + nested `flex:1/minHeight:0/overflow:auto` region (the same pattern the Desktop Table needs so its `stickyHeader` has a bounded scrolling ancestor to stick within - Phase 7-F). That pattern assumes the page never needs to grow taller than the viewport; it was never previously exercised at 375×667 with an interactive click into a row, because before this round the Order History List was *always* a Desktop Table regardless of viewport, and the one existing Mobile E2E scenario touching it (`mobile-responsive.spec.ts` M2) ran at 390×844 (tall enough that the issue didn't surface). This page's own Filter Stack (7 fields, each wrapping to full width at 375-430px) is tall enough by itself to fill an entire 667px-tall phone viewport, so the bounded-region pattern squeezed the results area (Table **or** Card, either would have been affected) to a **literal 0px height, completely unreachable by scroll** - confirmed via `getBoundingClientRect()` while building the Mobile Card layout (see the debug trail in this round's own git history if needed; not preserved as a separate doc). Fixed by making the outer container's height clamp and the results wrapper's flex/overflow region **conditional on `!isCardLayout`** - on Mobile, the page now flows normally and is scrolled by the shared App shell's own page-level `<Box sx={{flex:1,overflow:'auto'}}>` (`frontend/src/app/App.tsx`), exactly like every other non-List page already is. The Desktop branch's sticky-header scrolling region is untouched.

This fix, plus the new Card layout, required updating two pre-existing E2E assertions that hard-coded `tbody tr` locators for the Mobile viewport (`e2e/mobile-responsive.spec.ts` M2, `e2e/order-history-number-model.spec.ts` OH-2 Mobile) to use the new Card locators instead - both now additionally verify Revision is visible on the Card (added a `order-history-revision`/`order-history-official-po-no` testid to the Card, since OH-2 Mobile already required Revision to be checkable at Mobile width from an earlier Phase, and the initial Card field list per this round's own request list didn't happen to include it).

### 3.3 SKU Card layout / Sticky Action Bar - unchanged

Not touched this round; confirmed still passing via the full Mobile Scenario (M1/M3/M4/M5/M6) suite (§8.1). No Workflow/Permission change anywhere in Part A.

## 4. Part B: BR-08 Formal PO Number cleanup

Investigated before touching anything: `ConfirmOfficialPoNumberRequest` already carries **no** `officialPoNo` field (confirmed by reading the DTO and its own Javadoc, which states the Official PO No. is auto-numbered at G-SYS連携準備 request time per BR-08 and is no longer handled here). The manual PO number input/confirm UI itself was **already functionally retired** before this round - the only real residue was 2 dead frontend error-handling branches that could never fire:

- `frontend/src/features/history/OrderHistoryDetailPage.tsx`: removed the `INVALID_OFFICIAL_PO_NUMBER` / `DUPLICATE_OFFICIAL_PO_NUMBER` ternary branches from the `confirmPoNumberMutation` error Toast, with a comment citing BR-08/Phase 9-A as the reason no such error can occur anymore.
- `frontend/src/shared/i18n/locales/{ja,en}/history.json`: removed the now-unreachable `errorInvalidNumber`/`errorDuplicateNumber` keys.

No Official PO Short Code Master redesign, no Supplier/Brand Data Model change. Auto-numbering itself (Supplier Short Code 3 + Brand Short Code 3 + 3-digit Serial) is unchanged and re-verified end-to-end this round (§8.4, screenshot `09-formal-po-auto-number.png`).

## 5. Part C: Stockout / Restock Date

### 5.1 Data Model

New table `sku_expected_restock` (`backend/src/main/resources/db/migration/V32__sku_expected_restock.sql`), Portal-only, independent of the Supplier/Brand Master:

```sql
sku_expected_restock (
  id BIGSERIAL PK,
  sku_code VARCHAR(50) NOT NULL,        -- unique index
  expected_restock_date DATE,           -- nullable
  is_unknown BOOLEAN NOT NULL DEFAULT false,
  memo VARCHAR(500),                    -- nullable
  created_by, created_at, updated_by, updated_at
)
CONSTRAINT ck_sku_expected_restock_unknown_xor_date
  CHECK (NOT (is_unknown = true AND expected_restock_date IS NOT NULL))
```

`is_unknown` is an explicit boolean, distinct from a merely-null date - "not yet set" and "affirmatively unknown" are two different states, per the requirement.

`audit_event` extended (same migration): `sku_code` column added, `ck_audit_event_aggregate_root` widened to a 3-way XOR (`portal_order_id` / `price_change_set_id` / `sku_code`), `ck_audit_event_type` widened with `SKU_EXPECTED_RESTOCK_CHANGED` - reusing the existing Audit Trail infrastructure rather than building a parallel mechanism, following the same pattern already established for `price_change_set_id`.

### 5.2 Legacy Expected Arrival (Type A, READ ONLY)

`backend/src/main/resources/legacy/ArrivalExpectedBySkuQuery.sql`:

```sql
SELECT p.item_cd, MIN(COALESCE(a.eta_wh, a.eta)) AS expected_arrival_date
FROM tr_po_dtl p JOIN tr_arr a ON a.po_no = p.po_no
WHERE (p.del_flg IS NULL OR p.del_flg = 0) AND (a.del_flg IS NULL OR a.del_flg = 0)
  AND a.stk_in_date IS NULL AND COALESCE(a.eta_wh, a.eta) IS NOT NULL
  AND p.item_cd IN (:skuCodes)
GROUP BY p.item_cd
```

"Open" means `stk_in_date IS NULL` (not yet stocked in) with a real `eta`/`eta_wh` - an Arrival already stocked in does not count, even if it exists. Bulk-lookup only (`ArrivalReadRepository.findExpectedArrivalBySkus`, one query for however many SKUs a List page needs, never per-row), through the existing 3-layer Legacy READ ONLY enforcement (DB-user SELECT-only grant, HikariCP `readOnly=true`, `@Transactional(readOnly=true, transactionManager="legacyTransactionManager")`).

### 5.3 Merge / Display Priority (`backend/src/main/java/com/glv/gsysportal/service/SkuRestockExpectationService.java`)

```
if Legacy Expected Arrival exists  → source=LEGACY_EXPECTED_ARRIVAL, date=Legacy's date   (READ ONLY, never overridden)
else if Manual date is set         → source=PORTAL_MANUAL,          date=Manual's date
else if Manual marked Unknown      → source=PORTAL_MANUAL_UNKNOWN,  date=null
else                                → source=NONE,                  date=null
```

Portal Manual data is **never cleared** by Legacy appearing/disappearing - the underlying `sku_expected_restock` row is only ever written by an explicit user Save. To make this concretely testable and safe to edit from, the response DTO (`SkuRestockExpectationResponse`) exposes **two separate pairs**: the merged `source`/`date` a List/Detail screen renders, and a raw `manualDate`/`manualUnknown` pair that always reflects the real underlying Manual record **even while Legacy is winning display priority** - without this second pair, the SKU Detail Edit form would have had to prefill from the merged `date` (Legacy's own value whenever Legacy wins), and saving without touching the date field would have silently overwritten the real Manual value with Legacy's date. This was caught and fixed while wiring the Edit form (§5.5), before it could ship as a bug - covered by a dedicated assertion in `SkuRestockExpectationServiceIntegrationTest.legacyExpectedArrivalWinsOverManualData_scenarioC`.

### 5.4 API

`GET`/`PUT /api/items/{sku}/restock-expectation` (`SkuDetailController`) - matches the existing `/api/items/{sku}/ordering-context` convention rather than a `/api/skus/...` shape, per this app's established API Design Conventions. No `@PreAuthorize` gate (reuses the existing Stock/SKU operational edit permission - any authenticated user, same precedent as the Follow-up Case feature; no new Role). `PUT` validates `unknown=true` + a date together and rejects with `InvalidSkuExpectedRestockException` → 400 `INVALID_SKU_EXPECTED_RESTOCK`, and writes one `audit_event` row per change (Before/After summary text, SKU/performedBy/performedAt) via `AuditEvent.forSku`.

### 5.5 Frontend

- `frontend/src/shared/components/RestockLabel.tsx` - the single place every screen renders a SKU's restock info from, so the terminology (`入荷予定` for Legacy, `再入荷予定` for Manual, `再入荷予定：未定` for Unknown - never a mixed English/Japanese phrase) stays consistent by construction. `data-testid="restock-label"` + `data-restock-source` for tests.
- **Candidate List** (`CandidateListPage.tsx`): new column, appended after the existing Stock Judgement column.
- **Order History Detail / Approval Detail** (`OrderHistoryDetailPage.tsx`): restock shown on both the Mobile Card (its own row, only when a restock value exists) and the Desktop Table (new column, appended after `leadTime`/`openArrival`, same "append after existing columns, never insert" rule the pre-existing Gap Analysis B-1 columns already follow, to keep existing E2E cell-position assumptions stable).
- **SKU Detail** (`SkuDetailPage.tsx`): new `RestockExpectationEditSection` - shows the current merged display (`RestockLabel`) plus, only when `source === 'LEGACY_EXPECTED_ARRIVAL'`, a `legacyReadOnlyNote` Alert explaining the date below can't change what's currently displayed. Below that, an always-editable form (date picker, "未定" checkbox - checking it clears the date input client-side too, memo, Save) that prefills from `manualDate`/`manualUnknown` (never the merged `date`/`source` - see §5.3), plus a "who/when last updated" caption when a Manual record exists. Deliberately never shows "Legacy"/"Portal"/"TR_ARR" wording - only the same ja/en business terms `RestockLabel` uses everywhere else.
- **Stock/Sales** (`StockSalesListPage.tsx`): restock column added to the Desktop Table (appended after `updatedAt`) and a labeled row added to the Detail Drawer - the existing screen structure permitted this without any layout rework, so it was included per the "無理がなければ" instruction.

## 6. Business Scenarios Verified

All scenarios below were run against the live local dev stack (Backend `local` profile + Frontend `vite dev`, same Portal PostgreSQL / Legacy Demo MySQL every prior round used - 0 Production, 0 UAT, 0 Legacy write).

| # | Scenario | Result |
|---|---|---|
| A | Mobile: Order List (Card layout, 390px) → Order Detail (3-row Mobile Header) → Approve, no horizontal overflow | ✅ `mobile-responsive.spec.ts` M1, `post-freeze-business-refinement.spec.ts` (both 390px and the 375px floor) |
| B | Mobile: Order History List search → Order Detail → PO/Revision confirmation, Card layout | ✅ `mobile-responsive.spec.ts` M2 (updated for the new Card layout), `order-history-number-model.spec.ts` OH-2 (Mobile) |
| C | Legacy Expected Arrival displays READ ONLY on Candidate List / SKU Detail / Approval Detail, wins over any Manual data underneath it | ✅ `post-freeze-business-refinement.spec.ts` (3 tests) + `SkuRestockExpectationServiceIntegrationTest.legacyExpectedArrivalWinsOverManualData_scenarioC` |
| D | Manual Expected Restock date entry reflected on SKU Detail and Candidate List | ✅ `post-freeze-business-refinement.spec.ts` + `SkuRestockExpectationServiceIntegrationTest.manualDateIsUsedWhenLegacyHasNoOpenArrival_scenarioD` |
| E | "未定" (Unknown) entry reflected on SKU Detail and Candidate List | ✅ `post-freeze-business-refinement.spec.ts` + `SkuRestockExpectationServiceIntegrationTest.unknownIsRecordedExplicitly_scenarioE` |
| F | Audit Trail records SKU/Before/After/Updated By/Updated At for a Manual Expected Restock change | ✅ `SkuRestockExpectationServiceIntegrationTest.changeIsAudited_scenarioF` (Backend-level; not duplicated at E2E since no dedicated Audit Trail UI exists for this aggregate root yet) |
| G | Formal PO No. is auto-numbered only - no manual number input step anywhere in the flow | ✅ Already covered by every `official-po-integration.spec.ts` scenario (`official-po-no` asserted to auto-populate in `[A-Z]{3}[A-Z]{3}\d{3}` form immediately after G-SYS連携準備, no `.fill()` on a PO number field anywhere in that flow) - re-confirmed, not duplicated |
| H | Reissue keeps the same PO number, old Revision superseded | ✅ Already covered by `official-po-integration.spec.ts` Scenario K - re-confirmed, not duplicated |

New E2E spec: `frontend/e2e/post-freeze-business-refinement.spec.ts` (6 tests, all passing) covers A/B/C/D/E end-to-end across Candidate List, SKU Detail, and Order Detail using real Demo-seeded SKUs (`OD-TENT-002` - has an open Legacy Arrival; `OD-TENT-001` - already stocked in, so no Legacy Arrival, used for Manual entry so it's never overridden).

## 7. Test Results

- **Backend** (`mvn test`, JDK 23, `local`/`test` profile against the local Portal PostgreSQL + Legacy Demo MySQL): **600/600 passing**, 0 failures, 0 errors. Includes 6 new tests in `SkuRestockExpectationServiceIntegrationTest` and a hard-coded migration-version-list assertion in `PrototypeFlywayMigrationTest` updated for the new `V32` migration (32 migrations now applied, up from 31).
- **Frontend TypeScript** (`tsc -b`): 0 errors.
- **Frontend Vitest** (`vitest run`): 53/53 passing (3 files - i18n parity and other unit checks).
- **Frontend lint** (`oxlint`, full project): only pre-existing `react(set-state-in-effect)` warnings already present before this round (2 new instances follow the exact same established pattern), 0 new categories of warning.
- **Frontend build** (`vite build`): succeeds.
- **E2E** (`playwright test`, full suite, 218 tests across every spec file): **218/218 passing** (see §8.1 for the exact run).
- **Mobile Responsive E2E** (`mobile-responsive.spec.ts`): all 11 tests passing, including the Overflow Audit across 375/390/430/768/1440px on every stable route (`/orders/history` included) and all 6 Mobile Scenarios (M1-M6, M2 updated for the new Card layout).
- **i18n key parity** (ja/en): verified by the existing Vitest parity check plus manual JSON validation (`node -e "JSON.parse(...)"`) on every locale file touched this round (`candidates.json`, `history.json`, `stockSales.json`, `restockExpectation.json`).

### 7.1 Full E2E run

218 tests, 1 worker (per `playwright.config.ts`), full suite including every pre-existing spec file plus the new `post-freeze-business-refinement.spec.ts` - **all 218 passing**, 0 failures.

## 8. Screenshots

`docs/gops-post-freeze-business-refinement-implementation-screenshots/`:

- `01-mobile-approval-list-390.png` - Order History List, Mobile Card layout, 390px
- `02-mobile-approval-detail-390.png` - Order Detail, 3-row Mobile Header, 390px
- `03-mobile-approval-actions-390.png` - Sticky Action Bar (承認/差し戻し/修正) in viewport, 390px
- `04-candidate-restock-date-desktop.png` - Candidate List restock column, Desktop (1440px)
- `05-candidate-restock-date-mobile.png` - Candidate List restock column, Mobile (390px)
- `06-sku-restock-edit.png` - SKU Detail Manual Expected Restock Edit section
- `07-approval-restock-info.png` - Approval Detail SKU line showing restock info, Desktop
- `08-stock-sales-restock-info.png` - Stock/Sales Detail Drawer restock row
- `09-formal-po-auto-number.png` - Official PO No. auto-numbered immediately after G-SYS連携準備, no manual input step

## 9. Known Limitations / Remaining Gaps

- No dedicated Audit Trail UI exists yet for the `sku_code` aggregate root (Scenario F is Backend-verified only, same as every other aggregate root's audit correctness - there is no Order-Detail-Timeline-equivalent screen for SKU-level changes). Out of this round's scope; would be a Future Phase item if a business need for it is confirmed.
- Manual Expected Restock has no notion of expiry/staleness (a Manual date in the past is still displayed as-is, same as Legacy's own behavior) - matches the requirement as given, flagged here only for visibility.
- Stock/Sales' restock column is Desktop-only for the List Table (no separate Mobile Card layout was requested or added for this screen this round - Part A's Mobile scope was explicitly the Approval flow, not Stock/Sales); the Detail Drawer itself is already responsive and unaffected.
- The Legacy Expected Arrival query's "open" definition (`stk_in_date IS NULL`) is a re-application of the same definition already used elsewhere in this codebase (Fulfillment/Arrival Visibility), not a new interpretation - flagged here for traceability, not as a gap.
- UAT-dependent items unchanged from the 9/17 Re-Audit (`docs/gops-20260917-business-requirements-re-audit.md` §13) - none of them blocked this round's implementation.

## 10. Absolute Constraints - Re-confirmed

- Legacy Source changes: **0**
- Legacy DB writes: **0** (3-layer READ ONLY enforcement unchanged and re-verified)
- Production connection: **0**
- UAT connection: **0**
- Production deploy: **0**
- SafetyGuard changes: **0**
- Supplier/Brand Data Model changes: **0**
- Official PO Short Code Master redesign: **0**
- Domestic Recommended Qty Formula changes: **0**

## 11. Follow-on: Post-Freeze Business Refinement 2

This Implementation's `sku_expected_restock` foundation (§5) was extended, not replaced, by **Implementation 2 - Manufacturer Stockout Information Management** (`docs/gops-manufacturer-stockout-information-management.md`), which broadens "再入荷予定" into a full Stockout Status/Shortage Qty/Contact Method/History model reflecting how Gulliver's actual Manufacturer communication works (phone/email/Order response, at unpredictable timing). This round's own API/data model/screens described above remain backward compatible - see that document for the full change list.
