# G-OPS Post-Freeze Business Refinement 2 — Manufacturer Stockout Information Management

Extends Implementation 1's "再入荷予定" (Expected Restock Date) foundation into full **Manufacturer Stockout Information Management** - the real Gulliver business process where メーカー/Supplier convey stockout status through phone, email, an Order response, or other ad-hoc contact, at unpredictable timing relative to when an Order is placed.

**Baseline**: `f80ea216d68eda343cd7d39e47d12244e03bf19f` (Implementation 1 commit), doc update `960cad0`.

Same absolute constraints as every prior round: 0 Legacy Source changes, 0 Legacy DB writes, 0 Production/UAT connection, 0 Production deploy, 0 SafetyGuard changes, no Supplier/Brand relationship change, no Official PO Short Code Master redesign, no Domestic Recommended Qty Formula change. Portal PostgreSQL only.

## 1. System Information vs. Manufacturer Information (§3)

Kept as two explicitly distinct data sources, never merged at the Data Source level (only combined visually on screen):

- **System Information** (Legacy G-SYS, READ ONLY): Current Stock, Open PO, Expected Arrival (ETA/ETA_WH), Current-month Sales, Lead Time - unchanged, same repositories as every prior round.
- **Manufacturer Stockout Information** (G-OPS-owned, Portal PostgreSQL only): Stockout Status, Expected Restock Date, Unknown flag, Shortage Qty, Information Received Date, Contact Method, Memo, Updated/Created By/At, History.

## 2. Discontinued / 廃番 — Source Audit (§5, required before any Status decision)

**Finding**: Legacy G-SYS already has a fully-formed discontinuation concept - **not a gap, nothing added here**.

- `MS_ITEM.ITEM_STATUS` (VARCHAR) - `NEW` / `RETURNIDw/opage` (掲載保留) / `DISCON` (廃番) / `DISCON_STK` (廃番だが在庫あり). Defined in Legacy's own `Const.java` (`MS_ITEM_ITEM_STATUS_*`), used across Legacy's own batches (arrival mail, page creation, stock/price exports).
- `MS_ITEM.DISCON` (Boolean) - a **separate** discontinuation flag on the same table, used in Legacy exports purely for gray-cell highlighting.
- G-OPS already reads **both**: `item_status` is fully wired end-to-end (`LegacyStockReadRepository`/`LegacyPriceReadRepository` → `itemStatus` field on every SKU-bearing response → `ItemStatusChip` on the frontend, i18n labels 通常/廃番/廃番在庫あり/発注停止/掲載保留 already in `status.json`). The `discon` boolean is fetched into `LegacyStockRow`/`LegacyPriceRow` but **never surfaced** in any response DTO today (confirmed via Source - dead field, not a bug, just unused).

**Decision, per the explicit instruction not to independently add a DISCONTINUED value**: Manufacturer Stockout Status (`STOCKOUT`/`LONG_TERM_STOCKOUT`/`RESOLVED`) stays completely separate from and unaware of `itemStatus`/`discon`. A SKU can independently be `itemStatus=DISCON` (Legacy's catalog lifecycle judgement) and have no Manufacturer Stockout record at all, or vice versa - these are orthogonal facts, both already visible on-screen via their own existing/new Chips (`ItemStatusChip` vs. the new `StockoutStatusChip`), never combined into one Chip.

## 3. Data Model (§6/§24)

Extends the **existing** `sku_expected_restock` table in place (V32 is untouched; a new migration `V33__sku_manufacturer_stockout_info.sql` adds columns) - still exactly one row per SKU, same update-in-place identity Implementation 1 established:

```sql
ALTER TABLE sku_expected_restock ADD COLUMN stockout_status VARCHAR(30);        -- STOCKOUT / LONG_TERM_STOCKOUT / RESOLVED, or NULL
ALTER TABLE sku_expected_restock ADD COLUMN shortage_qty INTEGER;               -- nullable, never defaulted to 0
ALTER TABLE sku_expected_restock ADD COLUMN information_received_date DATE;      -- when the Manufacturer told us
ALTER TABLE sku_expected_restock ADD COLUMN contact_method VARCHAR(20);          -- PHONE / EMAIL / ORDER_RESPONSE / OTHER
```

Plus a new, separate **Business History** table (append-only, one row per change, always a full snapshot - never a diff):

```sql
CREATE TABLE sku_manufacturer_stockout_history (
  id BIGSERIAL PRIMARY KEY, sku_code VARCHAR(50) NOT NULL,
  stockout_status VARCHAR(30), expected_restock_date DATE, is_unknown BOOLEAN NOT NULL DEFAULT false,
  shortage_qty INTEGER, information_received_date DATE, contact_method VARCHAR(20), memo VARCHAR(500),
  recorded_by VARCHAR(50) NOT NULL, recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

`is_unknown`/`expected_restock_date` remain mutually exclusive (same CHECK constraint pattern as Implementation 1, now also enforced on the History table). Existing Implementation 1 rows are preserved as-is by the `ALTER TABLE` (new columns nullable, no backfill needed - a pre-Implementation-2 record simply reads as "not yet classified").

**Why extend rather than a new Current-state table**: the explicit instruction was "既存を拡張、乱立させない" - the record is still conceptually "this SKU's one current Manufacturer-facing record", just with a wider field set. A **separate** table was justified only for History, which is structurally different (append-only, many rows per SKU) from the Current-state row.

### 3.1 Stockout Status

`STOCKOUT` / `LONG_TERM_STOCKOUT` / `RESOLVED`, or `NULL` (not yet classified). Deliberately **not** auto-derived from `currentStock == 0` or `openPo == 0` - those remain purely System-computed signals (`computeStockJudgement`, unchanged, still drives the existing 在庫判定 Badge / Dashboard 欠品・長期欠品 KPIs, §19 - untouched this round). Manufacturer Stockout Status is only ever set by an explicit user action confirming what the Manufacturer actually said.

### 3.2 Shortage Qty

Nullable, never defaulted to 0 - "confirmed short by 10 of 20" and "told over the phone it's long-term stockout, no quantity ever discussed" are different states (§7).

### 3.3 Information Received Date vs. Updated At

`informationReceivedDate` (when the Manufacturer told us) is kept distinct from the pre-existing `updatedAt` (when G-OPS was edited) - e.g. "9/20 phone call, 9/21 entered into G-OPS" is representable exactly (§9).

### 3.4 Contact Method

`PHONE` / `EMAIL` / `ORDER_RESPONSE` / `OTHER` - internal codes only, resolved to Japanese (電話/メール/発注回答/その他) via i18n, never shown raw in the UI (§10).

## 4. API (§25 - Backward Compatibility)

`GET`/`PUT /api/items/{sku}/restock-expectation` (unchanged path, matching the existing convention) - **not** a breaking change:

- `SkuExpectedRestockRequest` gained 4 new, all-optional trailing fields (`stockoutStatus`/`shortageQty`/`informationReceivedDate`/`contactMethod`). A caller sending only the original 3 fields as JSON (field names, not position) still works exactly as before, landing `null` in the new ones.
- `SkuRestockExpectationResponse` kept `source`/`date` **exactly as before** (still the simple Legacy-wins merge, for any existing caller) and additionally, always, exposes the raw Manufacturer fields plus a new `legacyDate` (Legacy's own value, explicit) and `hasConflict` (see §6 below).
- New endpoint: `GET /api/items/{sku}/restock-expectation/history` - the Business History timeline (§21), oldest first.

## 5. Permission (§26)

No new Role. Reuses the exact same "no `@PreAuthorize` gate, any authenticated user" precedent Implementation 1 already established for this same endpoint (matching the Follow-up Case creation precedent) - Approval permission (OPERATOR submit / ADMIN approve) is completely untouched and separate.

## 6. Legacy Expected Arrival vs. Manufacturer Expected Restock (§23)

**Reconsidered from Implementation 1's strict "Legacy always wins, Manual is hidden" rule.** The merged `source`/`date` fields are kept for backward compatibility, but every screen updated this round renders the **raw** Legacy line and the **raw** Manufacturer line **separately and simultaneously** whenever each exists - neither is ever fully hidden by the other:

```
hasConflict = (Legacy has an open Expected Arrival) AND (Manufacturer status is STOCKOUT or LONG_TERM_STOCKOUT)
```

When `hasConflict` is true, a Warning marker (⚠, `RestockConflictWarning`) appears next to the Legacy line, on every screen that shows restock info (Candidate List, SKU Detail, Approval Detail). The disagreement is surfaced, never silently resolved in either direction - matching the example in the requirements doc precisely (Legacy 9/28 incoming vs. Manufacturer "still long-term stockout, 10/15" both shown together).

## 7. Screens (§16-18)

- **SKU Detail** (§12/§17): the "再入荷予定" card is now "メーカー欠品情報" - shows the Legacy line (if any, READ ONLY note) + the Manufacturer line (status Chip, restock date/未定, shortage qty, confirmation caption) + a Conflict Warning when applicable, an always-available Edit form (Status/Date/Unknown/Shortage Qty/Information Received Date/Contact Method/Memo), and a "履歴を見る" button opening the Business History Dialog (Table on Desktop, Card list on Mobile). Never shows "Legacy"/"Portal"/"TR_ARR" wording.
- **Supplier Response** (§13/§14): a new "欠品情報として登録" button per line opens a Dialog prefilled with Shortage Qty = Ordered − Confirmed (only when Confirmed is a real, smaller number) and Contact Method = 発注回答 (fixed) - Status must be chosen explicitly, nothing is ever auto-submitted from a mere quantity difference (§13's "自動的に欠品確定Recordを作らない", verified by Scenario D). Writes through the exact same `PUT /api/items/{sku}/restock-expectation` SKU Detail uses - a completely separate call from Supplier Response's own Save/Confirm API, which (along with its own pre-existing, unrelated `supplyStatus` per-line/per-Revision snapshot field) is entirely untouched.
- **Candidate List / Approval Detail (Order History Detail) / Stock-Sales** (§15/§16/§18): each row/line now shows the Stockout Status Chip + the merged restock label + a Conflict Warning when applicable + the "メーカー確認 {date}（{method}）" caption, appended after the existing columns per this codebase's established "append, never insert" convention (keeps existing E2E cell-position assumptions stable). Desktop and Mobile Card layouts both updated; no horizontal overflow introduced (verified at 375/390/430px).
- **Dashboard / Brand Selection** (§19): **unchanged**. Existing KPI Counts (発注候補/欠品/長期欠品, System-computed) keep their existing meaning; no Manufacturer-confirmed count was mixed in this round, per the explicit instruction to preserve existing KPI semantics.
- **Search/Filter** (§20): **not added this round.** Considered per the instructions ("検討"), but a dedicated Manufacturer Stockout Status filter was judged unnecessary for this round given the Chip is already visible at a glance on every List row without scrolling or opening a Detail screen - adding a new Filter control would have added UI complexity the instructions explicitly cautioned against ("画面を過度に複雑化しない") without a concrete visibility gap it would solve. Flagged as a Future Phase candidate if a real business need surfaces.

## 8. History (§11/§21/§22)

- Every `PUT` appends one row to `sku_manufacturer_stockout_history` (full snapshot, oldest-first retrieval) **and** still writes one `audit_event` row (existing `SKU_EXPECTED_RESTOCK_CHANGED` type, reused - no new Technical Audit event type needed since it's the same table/aggregate root). The two serve different audiences by design (§27): History is the Business-facing "what did the Manufacturer say, and when" timeline (`ManufacturerStockoutHistoryDialog`, Table on Desktop / Card list on Mobile); `audit_event` remains the Technical Before/After text record, unchanged in shape.
- **RESOLVED never deletes anything** (§22) - marking a SKU RESOLVED is just another `PUT` (another History row, another Audit row); the full 長期欠品 → 再入荷予定 → 解消 timeline stays inspectable via "履歴を見る" indefinitely.

## 9. Business Scenarios A-I (§28) — Verified

All run against the live local dev stack (0 Production/UAT/Legacy-write), via the new `frontend/e2e/manufacturer-stockout-information.spec.ts` (7 tests, all passing) plus the Backend's own `SkuRestockExpectationServiceIntegrationTest` (9 new tests this round, on top of Implementation 1's 6):

| # | Scenario | Verified via |
|---|---|---|
| A | Pre-order phone call → Long-term Stockout + Unknown restock → reflected on Candidate List | E2E + Backend `manufacturerStockoutFieldsRoundTrip_scenarioC` |
| B | Later email → a real restock date replaces Unknown, History keeps the earlier entry | E2E + Backend `restockDateChangeIsHistoryTracked_scenarioE` |
| C | Requested 20 / Confirmed 10 → Difference 10 → registered from Supplier Response, Shortage Qty prefilled 10, Contact Method = 発注回答 | E2E (full flow: Draft → Approve → Demo Send → Supplier Response → register) + Backend `shortageQtyFromSupplierResponseDifference_scenarioD` |
| D | Confirmed < Requested alone never auto-creates a Stockout record (Save-only, no registration click) | E2E (before/after equality check across the Response Save step) |
| E | Restock date revised (11/15 → 11/20), History keeps both entries in order | E2E + Backend (same test as B) |
| F | 長期欠品 → 解消 (RESOLVED), History preserved, no deletion | E2E + Backend `resolvedTransitionKeepsHistory_scenarioF` |
| G | Legacy Expected Arrival + an active Manufacturer Stockout status both shown, flagged as a conflict | E2E (SKU Detail + Candidate List) + Backend `legacyAndManufacturerConflictIsFlagged_scenarioG` |
| H | Mobile Approval shows Manufacturer Stockout info without overflow, Approve still works | E2E (390px, full Draft→Approve flow) |
| I | Mobile Candidate List shows Status/Restock/Confirmation date without overflow | E2E (390px) |

Invalid-input validation also covered: `invalidStockoutStatusIsRejected`, `invalidContactMethodIsRejected`, `negativeShortageQtyIsRejected` (all → `INVALID_SKU_EXPECTED_RESTOCK` 400, same exception class Implementation 1 established, extended with a message constructor).

## 10. Regression (§31)

Full existing E2E suite (Implementation 1's 218 tests + this round's 13 new tests) re-run in full - see the Test Results section of the accompanying commit / final report for the exact pass count. Implementation 1's own `post-freeze-business-refinement.spec.ts` needed 3 small, expected updates this round (not regressions in the app): the SKU Detail success Toast text changed (再入荷予定を更新しました → メーカー欠品情報を更新しました, reflecting the section's intentionally broadened scope), and one locator was narrowed to the Legacy-sourced `restock-label` specifically now that the same section can render a second, Manufacturer-sourced line alongside it (§6 above) - both are direct, intended consequences of this round's own design decisions, not incidental breakage.

## 11. Known Limitations / Remaining Gaps

- No dedicated Filter for Manufacturer Stockout Status this round (§20 - a deliberate scope decision, see §7 above).
- Discontinued/廃番 remains fully separate from Manufacturer Stockout Status, exactly as instructed (§2) - if a future round decides these should visually combine, that is an explicit new decision to make then, not an oversight now.
- The `discon` boolean column (Legacy `MS_ITEM.DISCON`) remains unused/unsurfaced in G-OPS (pre-existing, not part of this round's scope - flagged in §2 for visibility only).
- UAT-dependent items unchanged from the 9/17 Re-Audit and Implementation 1 - none blocked this round.

## 12. Absolute Constraints — Re-confirmed

Legacy Source changes: **0**. Legacy DB writes: **0**. Production connection: **0**. UAT connection: **0**. Production deploy: **0**. SafetyGuard changes: **0**. Supplier/Brand relationship changes: **0**. Official PO Short Code Master redesign: **0**. Domestic Recommended Qty Formula changes: **0**.
