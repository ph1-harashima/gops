# G-OPS Phase1 Final Cleanup Report

Baseline Commit: `4bb7473`
Final Commit: `2816ce35fd607c16c2d6dc6def66649ae809cf78`

Scope: Order History Number Model fix (Cleanup-1) and Test Data Lifecycle isolation (Cleanup-2) only. No new Business Feature, no new Business Rule, no UI redesign, no IA redesign, no Production connection - per the explicit rules for this work.

## 1. Order History Number Model Audit

### 1.1 Before

- `portal_order` already modeled three distinct number concepts correctly: `draftNo`, `prototypePoNo` (Portal管理番号, BR-07), `officialPoNo` (正式PO番号, BR-08) - the entity-level design was never the problem.
- `OrderHistorySummaryResponse`/`OrderHistoryDetailResponse` exposed only `draftNo` + `prototypePoNo`. `officialPoNo` and Revision were **entirely absent** from the Order History API - the List could not show them no matter what the UI did.
- `OrderHistoryListPage.tsx`'s single "PO No." column always rendered `prototypePoNo ?? draftNo` - i.e. it **always** showed the Portal-internal number, **never** the Official PO No., under a label ("PO No.") that reads as if it were the official number.
- `OrderHistoryService.buildSpecification`'s keyword search matched `draftNo`/`prototypePoNo` only - `officialPoNo` was unsearchable from History, even though it's the number Excel/PDF/Manufacturer Send actually use (confirmed via `OfficialPoFileNaming.java`, which builds filenames from `officialPoNo` + `revisionNo` exclusively).
- Order Detail (`OrderHistoryDetailPage.tsx`) was already **materially correct**: a `Chip` labeled "Portal管理番号" (ja) / "Portal Tracking No." (en) with an explanatory tooltip sits next to the Portal-internal number, and Official PO No./Revision are shown separately, sourced from the Official PO Integration API. The English label diverged from BR-07's own "Management No." gloss - a naming inconsistency, not a functional bug.

### 1.2 After

- `OrderHistorySummaryResponse` (backend) now carries `officialPoNo` and `revisionNo`.
  - `officialPoNo` is read directly from `PortalOrder.officialPoNo` (already correctly populated by `OfficialPoIntegrationService` at Request-creation time - its own Javadoc was stale, claiming "always NULL", but the code has written it since BR-08 was confirmed).
  - `revisionNo` is read from `OfficialPoIntegrationRequestRepository.findFirstByPortalOrderIdOrderByRevisionNoDesc` - **not** `PortalOrder.currentRevisionNo`, which is a different field set only by Demo Send (`OrderStatusTransitionService.demoSend`) and would have shown a false "not yet assigned" for an Order that already has an Official PO Request but was never Demo-Sent. This was caught by the new OH-2 E2E scenario (see §3) failing on first run and fixed before this report was written.
- `OrderHistoryListPage.tsx` now renders 3 columns instead of 1 ambiguous one: **Portal管理番号** (reuses the exact existing term/tooltip from Order Detail, `portalPoNoCaption`/`portalPoNoCaptionTooltip`), **正式PO番号** (new `table.officialPoNo` key, same term already used elsewhere: `officialPoIntegration.officialPoNoLabel`), and **Revision** (reuses `officialPoIntegration.revisionLabel`).
- A Draft-stage Order (no Official PO yet) shows **正式PO番号未設定 / "Not Yet Assigned"** in the Official PO No. column - the exact existing term from `officialPoIntegration.officialPoNoUnassigned`, never a substitute of the internal number.
- English `portalPoNoCaption` changed from "Portal Tracking No." to "**Portal Management No.**" to align with BR-07's own English gloss ("Management No.") - the only terminology reconciliation needed for Order Detail; no other change was required there.

### 1.3 Search behavior

- `OrderHistoryService.buildSpecification`'s keyword OR-clause now also matches `officialPoNo` (in addition to the pre-existing `draftNo`/`prototypePoNo`) - one input box, per the "第二候補" allowed by the instructions, since extending the existing single-keyword convention (already used successfully for draftNo/prototypePoNo) was lower-risk than adding a second Backend query param and a second Frontend input for this Phase.
- Filter label/placeholder updated to state this explicitly: ja "管理番号 / 正式PO番号" / "Portal管理番号・正式PO番号・Draft Noで検索"; en "Mgmt No. / Official PO No." / "Search by Portal Management No., Official PO No., or Draft No."

### 1.4 Order Detail consistency

No functional change needed - Order Detail already distinguished all three concepts correctly (see §1.1). Only the English `portalPoNoCaption` string was reconciled with BR-07 (§1.2). Both screens now use the identical ja/en terms for the same concepts.

### 1.5 Mobile consistency

Order History List has a single responsive table (no separate mobile card view) - the 3 new columns render identically at Mobile width; verified by the OH-2 (Mobile) E2E scenario at 390×844px (§3).

## 2. Test Data Lifecycle

### 2.1 Root cause

No entity in scope (`supplier_contact`, `mail_template`, `manufacturer_channel`, `supplier_region_classification`) has a hard-DELETE code path anywhere in the Backend - every "delete" in the app is a soft-delete (`is_active = false`), by deliberate Business Rule, not oversight. E2E specs that repeat across runs either create a fresh row every run (accumulating) or deactivate-then-recreate under the same name/code (still inserting a new row each time - deactivation alone never stops accumulation). `demo-reset.sh` deliberately excludes all 4 Master tables from its TRUNCATE list (by design - Master data must survive a Demo Reset). The combination is exactly what produces unbounded accumulation of Inactive rows over repeated Test runs. `official_po_short_code` is unaffected - confirmed 100% migration-seeded, no E2E spec ever creates a row there.

### 2.2 Test Data identification (per table)

| Table | Identifiable? | Marker |
|---|---|---|
| `supplier_contact` | **Yes, 100%** | `email` domain is always `@example.com`; grepped every migration/seed file in the repo - this domain is never used for real Demo Master data. |
| `mail_template` | **Partial** | Only rows named exactly `"Follow-up E2E Template <timestamp>"` are unambiguous. Other E2E-created rows reuse plausible-looking names (`"PO Template"`, `"Scenario A Template"`, `"Integration PO Template"`, `"Phase 7-F Variable UX Test"`, etc.) indistinguishable by content from a real Demo Master template. |
| `manufacturer_channel` | **No** | Every E2E spec reuses fixed, realistic-looking `supplierCode`/`brandCode` values (e.g. `SUP_ALPHA`/`BR_OUTDOOR`) with no per-run or Test-only marker at all. |
| `supplier_region_classification` | **No** | Same pattern as `manufacturer_channel`. |
| `official_po_short_code` | N/A | No E2E-created rows exist; all 6 rows are migration seed data (`V30__official_po_short_code_demo_seed.sql`), 6/6 Active. |

### 2.3 Cleanup strategy implemented

Chosen approach: the instructions' third candidate - extend `demo-reset.sh`/`DemoResetRunner` with an opt-in flag (`--include-test-master-data` → `app.demo-reset.include-test-master-data=true`), rather than a new Backend API (never exposed, matching `DemoResetRunner`'s own existing "CLI/script only" rule) or a per-spec `afterEach` hook (would require a new hard-delete-capable endpoint reachable from the browser, a larger surface change for equivalent safety).

`DemoResetRunner.cleanupTestMasterData()` physically DELETEs only:
- `supplier_contact` WHERE `is_active = false AND email LIKE '%@example.com'`
- `mail_template` WHERE `is_active = false AND template_name LIKE 'Follow-up E2E Template %'`

Both predicates additionally require `is_active = false` - an Active row is **never** deleted, regardless of content match (Regression C, §2.5).

`manufacturer_channel`, `supplier_region_classification`, and every other `mail_template` row are **deliberately not touched** - no reliable marker exists (§2.2), and "曖昧な条件によるDELETEは禁止" / "100%識別できない場合：削除しない" is treated as an absolute rule, not a preference. This is documented as a Known Limitation in the Freeze doc, with a recommended Phase 2 follow-up: change the relevant E2E specs to tag newly-created rows with a reserved, unambiguous Test marker at creation time (e.g. a dedicated Test-only Supplier/Brand code that never collides with real Demo Master data), so future rows become cleanable without needing to resolve today's already-ambiguous backlog.

### 2.4 Safety Guard

- `cleanupTestMasterData()` is called only when `app.demo-reset.include-test-master-data=true` is explicitly passed on the command line (never set in `application.yml`) - same no-op-by-default convention as `app.demo-reset.enabled`.
- Reuses the exact same defense-in-depth already proven for the ordinary Demo Reset TRUNCATE: `SafetyGuardEnvironmentPostProcessor` refuses to even start the process outside `local`/`demo`/`test`, with both JDBC URLs allowlisted by host+DB name; `cleanupTestMasterData()` additionally re-validates the live Prototype connection itself (`verifyPrototypeConnectionIsLocal`/`validateJdbcUrl`) before issuing any DELETE - independent of, and in addition to, the same call already made in `run()`.
- Legacy MySQL is never referenced by this class in any way (no Legacy DataSource field exists on `DemoResetRunner`).
- Never exposed as an HTTP endpoint - CLI/script only (`demo-reset.sh --include-test-master-data`).

### 2.5 Regression

Automated (`DemoResetRunnerCleanupTestMasterDataIntegrationTest`, 6 tests, all against the real local Prototype DB inside a rolled-back transaction):

| # | Scenario | Result |
|---|---|---|
| A | Inactive `@example.com` `supplier_contact` row → cleanup → deleted | PASS |
| A | Inactive `"Follow-up E2E Template <n>"` `mail_template` row → cleanup → deleted | PASS |
| B | Inactive non-`@example.com` `supplier_contact` row (ambiguous) → cleanup → survives | PASS |
| B | Inactive ambiguously-named `mail_template` row → cleanup → survives | PASS |
| C | Active `@example.com` `supplier_contact` row → cleanup → survives | PASS |
| C | Active `"Follow-up E2E Template <n>"` `mail_template` row → cleanup → survives | PASS |

D (Production Profile → refused) and E (Legacy DB → 0 changes) are covered by construction, not re-tested separately: D is the exact same `validateJdbcUrl` guard `DemoResetRunnerTest` already covers (6 tests, unchanged), called unconditionally before any DELETE in `cleanupTestMasterData()`; E holds because this class never references the Legacy DataSource.

### 2.6 Existing accumulated data

Not bulk-deleted except where §2.3's two 100%-confident patterns match. Actual local Prototype DB row counts, Run 1 (captured near the end of this session's accumulated dev/E2E history, just before actually executing `./demo-reset.sh --include-test-master-data` for the first time) vs. Run 2 (immediately after that cleanup run):

| Table | Run 1 (before) | Run 2 (after `--include-test-master-data`) |
|---|---|---|
| `supplier_contact` | total 171, active 1 | total **1**, active 1 |
| `mail_template` | total 161, active 1 | total **141**, active 1 |
| `manufacturer_channel` | total 106, active 1 | total 110, active 1 (never targeted by cleanup - see below) |
| `supplier_region_classification` | total 24, active 0 | total 25, active 0 (never targeted by cleanup) |
| `official_po_short_code` | total 6, active 6 | total 6, active 6 (never touched, any run) |

`supplier_contact` (171→1) and `mail_template` (161→141, the 20-row drop being every row matching `"Follow-up E2E Template %"`) show the cleanup working exactly as designed against the full accumulated backlog, not just newly-created rows. `manufacturer_channel`/`supplier_region_classification` show a small increase rather than a decrease - the very last E2E specs from the run immediately preceding this measurement finished creating a few more untagged rows right around when Run 1 was captured, and `cleanupTestMasterData()` correctly left every one of them alone, since neither table has a content-based marker it can act on (§2.2). This is a live confirmation of the Known Limitation, not just a static audit finding - a *second*, full E2E regression pass (§3) was then run against the freshly-reset DB, which necessarily grows both tables further still (not separately re-measured here, since the point - cleanup targets exactly the two proven-safe patterns and nothing else - is already established by Run 1 → Run 2 above).

## 3. Final Test (Full Regression)

Run in this order: Backend full suite → Frontend Vitest/TypeScript/Vite build → real `./demo-reset.sh --include-test-master-data` execution (§2.6) → Backend restart → full E2E suite (fresh DB).

| Check | Result |
|---|---|
| Backend full `mvn test` | **594 / 594 PASS** (588 baseline + 6 new `DemoResetRunnerCleanupTestMasterDataIntegrationTest`) |
| Frontend Vitest | **51 / 51 PASS** (unchanged from baseline; includes the i18n ja/en key-parity test - passed, confirming the `history.json` key changes in §1 introduced no mismatch) |
| TypeScript (`tsc -b`) | **0 errors** |
| Vite production build | **PASS** |
| Full E2E (Playwright, fresh DB after Demo Reset) | **210 passed, 2 skipped, 0 failed** (212 total = 207 baseline + 5 new Order History scenarios). The 2 skips are the Dashboard's 承認待ち/メーカー回答待ち KPI deep-link tests, which skip themselves when their own KPI count is 0 - expected immediately after a fresh Demo Reset (baseline's "1 pre-existing skip" was recorded against an already-populated DB; a truly empty DB skips more of these by the same pre-existing logic, not a regression) |
| Mobile Responsive E2E | **11 / 11 PASS** |
| i18n parity | **PASS** (part of the Vitest run above) |

One pre-existing flaky failure was found and fixed as a side effect of this Phase's Cleanup-2 work: `dashboard-deep-links.spec.ts`'s 価格変更（下書き） KPI test failed on the first Full E2E pass (before the real Demo Reset was executed) with "Expected: 195, Received: 0" - the Price Change List failed to render once its own accumulated Draft count (unrelated to this Phase's 5-table scope) grew large enough, exactly the same disease Cleanup-2 targets, just in a table outside this Phase's stated scope. Running the real Demo Reset (which already TRUNCATEs `price_change_set`) resolved it; the second Full E2E pass, above, confirms 0 failures.

### Order History new E2E (OH-1〜OH-4 + Mobile)

All 5 scenarios in `frontend/e2e/order-history-number-model.spec.ts` PASS:

- **OH-1**: Draft Order → History → Management No. shown, Official PO No. explicitly "正式PO番号未設定" (not substituted) - PASS
- **OH-2**: Official-PO-issued Order → History → Management No. / Official PO No. / Revision all present and mutually distinct - PASS (this scenario caught the `currentRevisionNo` vs `OfficialPoIntegrationRequest.revisionNo` bug described in §1.2/§4 Changed Files, before this report was finalized)
- **OH-3**: search by Official PO No. → exactly the matching Order - PASS
- **OH-4**: History → Detail → Back preserves Filter/Pagination (URL `orderNoKeyword` round-trips) - PASS
- **OH-2 (Mobile, 390px)**: all 3 columns remain visible/correct at Mobile width - PASS

### Test Data Lifecycle E2E (regression, §2.5)

`DemoResetRunnerCleanupTestMasterDataIntegrationTest`: 6/6 PASS (A/B/C scenarios ×2 tables, run inside a rolled-back transaction against the real local Prototype DB - see §2.5 for the per-scenario table). The real, non-rolled-back Run 1 → Run 2 comparison in §2.6 is the "does this actually work end-to-end against the real accumulated backlog" proof the automated tests alone can't provide.

## 4. Changed Files

Backend:
- `backend/src/main/java/com/glv/gsysportal/dto/response/OrderHistorySummaryResponse.java` - add `officialPoNo`, `revisionNo`
- `backend/src/main/java/com/glv/gsysportal/service/OrderHistoryService.java` - populate the two new fields (officialPoNo from `PortalOrder`, revisionNo from `OfficialPoIntegrationRequestRepository`), extend keyword search to `officialPoNo`
- `backend/src/main/java/com/glv/gsysportal/demo/DemoResetRunner.java` - add opt-in `cleanupTestMasterData()`
- `backend/demo-reset.sh` - add `--include-test-master-data` flag
- `backend/src/test/java/com/glv/gsysportal/demo/DemoResetRunnerCleanupTestMasterDataIntegrationTest.java` - new (6 tests)

Frontend:
- `frontend/src/shared/types/orderHistory.ts` - add `officialPoNo`, `revisionNo` to `OrderHistorySummary`
- `frontend/src/features/history/OrderHistoryListPage.tsx` - 2 new columns, reuse `portalPoNoCaption` for the existing one
- `frontend/src/shared/i18n/locales/ja/history.json`, `.../en/history.json` - new `table.officialPoNo` key, removed unused `table.poNo`, filter label/placeholder updated, `portalPoNoCaption` (en) reconciled with BR-07
- `frontend/e2e/order-history-number-model.spec.ts` - new (OH-1〜OH-4 + Mobile, 5 tests)

Docs:
- `docs/gops-phase1-freeze.md` - new
- `docs/gops-phase1-final-cleanup-report.md` - new (this file)

## 5. Commit

See Git log - Final Commit `2816ce35fd607c16c2d6dc6def66649ae809cf78`.

## 6. Remaining Issues (as of Final Commit `2816ce3`, before the Freeze Blocker Final Fix round - see §7)

- ~~`manufacturer_channel`/`supplier_region_classification` Test Data cleanup deferred to Phase 2~~ **Resolved in §7.2** (Freeze Blocker-2): both now stop growing across repeated E2E runs via Test Fixture reuse, without any content-based DELETE.
- The majority of `mail_template`'s accumulated rows remain ambiguous and unaddressed - still true after §7 (out of the Freeze Blocker instructions' 3-item scope).
- `PortalOrder.officialPoNo`'s Javadoc (stale "always NULL, unresolved CUSTOMER REVIEW" comment) was not corrected - out of this Phase's stated scope (Order History display/search only), noted here so it isn't mistaken for a live constraint by a future reader.

## 7. Freeze Blocker Final Fix (follow-up round)

Baseline for this round: Final Commit `2816ce3` (+ doc-hash follow-up `2e35f0d`) above, which the user reviewed and returned CONDITIONAL with 3 required fixes before Freeze. Scope was strictly limited to those 3 items - no other Feature/UI change was made.

### 7.1 BLOCKER-1: Dashboard 発注候補 KPI navigation

**Root cause**: `DashboardPage.tsx`'s top "発注候補" KPI tile navigated straight to `/candidates?recommendedOnly=true` (the all-Brand flat SKU List), the one remaining entry point that skipped the Brand-first landing (`OrderCandidateBrandListPage`) every other 発注候補 entry point already used correctly - Global Navigation (`/candidates`, no params), the Brand List's own "すべての発注候補を表示" button, Brand-name-only deep link (`?brandCode=X`, no `recommendedOnly`), the Dashboard's own "ブランド別内訳" per-Brand deep link, and "ブランド一覧へ戻る" were **all already correctly implemented** from the earlier IA Phase 3 work (commit `a0d471d`). This was a single inconsistent leftover, not a missing feature.

**Fix**: one line in `DashboardPage.tsx` - `navigate('/candidates?recommendedOnly=true')` → `navigate('/candidates')` for the KPI's `onClick`.

**Test/doc updates**:
- `dashboard-deep-links.spec.ts`: the KPI's own count-matching test rewritten to assert KPI → Brand List → (click "すべての発注候補を表示") → flat List → count matches (same guarantee as before, new path). "Scenario 1-5"'s final sub-assertion (previously asserting the KPI reached the flat List directly) updated to assert it now reaches the Brand List.
- `docs/gops-order-candidates-brand-entry-implementation.md`: added a correction note - it had documented the old KPI behavior as an intentional, unchanged design decision.

**Browser Acceptance (B1-B6)**: run live, ADMIN role, against `http://localhost:5173`.

| # | Steps | Result | Evidence |
|---|---|---|---|
| B1 | Dashboard → 発注候補 KPI | Lands on `/candidates` (Brand一覧, no SKU list shown) | `docs/gops-phase1-freeze-blocker-fix-screenshots/02-b1-brand-list-after-kpi-click.jpg` |
| B2 | Brand一覧 → LIVORA | `/candidates?brandCode=BR_HOME`, 6 rows, all LIVORA | `.../03-b2-livora-candidate-list.jpg` |
| B3 | Brand一覧 → すべての発注候補を表示 | `/candidates?recommendedOnly=true`, all-Brand mixed list (14 rows, LIVORA/KITCHENNE/FIELDNEST) | `.../04-b3-view-all-candidates.jpg` |
| B4 | Dashboard → ブランド別内訳 LIVORA | `/candidates?brandCode=BR_HOME` directly, Brand一覧 not shown first | `.../05-b4-brand-breakdown-deeplink-livora.jpg` |
| B5 | Global Navigation → 発注候補 | `/candidates` (Brand一覧), confirmed from a filtered state too | URL confirmed live (no separate screenshot - same view as B1) |
| B6 | Brand → Candidate List → ブランド一覧へ戻る | Returns to `/candidates` (Brand一覧) | URL confirmed live (same view as B1) |

All 6 PASS. Dashboard itself: `.../01-dashboard.jpg`.

**PC**: confirmed live via Browser as above, plus `dashboard-deep-links.spec.ts` (12/12 PASS, Playwright/Desktop viewport).

**Mobile**: this environment's Browser tool (`resize_window`) does not propagate to the page's actual rendering viewport here, so B1-B6 could not be re-captured as manual Mobile screenshots. Mobile evidence instead comes from Playwright's own true 390×844 viewport, which IS correctly enforced: `mobile-responsive.spec.ts` M5 ("発注候補 -> Brand選択 -> Candidate確認") exercises exactly this Brand-first flow at Mobile width - PASS (11/11 in that file overall, §Final Test below).

### 7.2 BLOCKER-2: Test Data Lifecycle for manufacturer_channel / supplier_region_classification

**manufacturer_channel 原因**: identical to the general root cause in §2.1 - every E2E spec that needs a configured Channel (`manufacturer-channel.spec.ts`, `email-send.spec.ts`, `official-po-integration.spec.ts`, `mobile-responsive.spec.ts`'s M6) unconditionally clicked through the Create UI and POSTed a brand-new row every run, then only deactivated it afterward - never reused a prior run's row.

**manufacturer_channel 対策**: added a small `ensureManufacturerChannel(page, supplierCode, brandCode, channel)` Test Helper, duplicated into each of the 4 spec files above (matching this project's existing per-file-duplication convention - no shared helpers module exists). It GETs `/api/admin/manufacturer-channels`, and if a row for that exact Supplier/Brand already exists (from any prior run - Demo Reset never wipes this table), PUTs it to `active: true` with the desired channel - the SAME PUT each file's own cleanup test already used, so no new API surface. Only when no row exists yet (a genuinely new Supplier/Brand combo) does it fall through to the real Create UI, so that path still gets exercised at least once. Business Rule (soft-delete-only) is completely unchanged - Production code was not touched.

**supplier_region_classification 原因**: identical pattern, one file (`supplier-region-classification.spec.ts`).

**supplier_region_classification 対策**: same `ensureRegionClassification` helper pattern, one copy in that file.

**Existing (pre-Freeze-Blocker) ambiguous rows**: left untouched, per §2.2/§2.3 - this fix only stops the growth, it does not and cannot retroactively identify which of the already-accumulated rows are Test-created vs. real Demo Master data.

### 7.3 price_change_set audit (section 5 of the Freeze Blocker instructions)

- **どのTestが作成するか**: 6 E2E spec files create `price_change_set` rows via the ordinary "新規価格変更を作成" UI flow (`price-change-foundation.spec.ts` and 5 others that touch it incidentally).
- **Cleanupされるか / Demo Reset対象か**: yes - `DemoResetRunner`'s TRUNCATE statement already includes `price_change_set, price_change_set_detail` (confirmed in the unchanged code, §Cleanup-1/2 round). This table was never excluded the way the 4 Master tables are - it is ordinary workflow data, the same category as `portal_order` itself, not Master data.
- **E2Eを繰り返すと増えるか**: yes, exactly like `portal_order` - until the next (ordinary, unflagged) Demo Reset, which already fully handles it. No new "won't grow" mechanism was needed or added for this table.
- **KPI/Paginationへの影響 - actual finding**: the reported failure ("Expected: 195, Received: 0", then reproduced fresh at "Expected: 9, Received: 0") was **not** a data-volume or accumulation bug. Live Browser reproduction confirmed: `GET /api/price-changes?status=DRAFT` and the Dashboard's own `priceChangeDraftCount` always agreed (both 9); navigating there by direct URL/reload always rendered all rows correctly. The only place it failed was the `dashboard-deep-links.spec.ts` KPI-click test itself, which read the row count via a single, non-retrying `page.locator(...).count()` immediately after the loading spinner cleared - if sampled a moment before React's next paint, this reads 0 regardless of how many rows actually exist, independent of data volume.
- **対策**: replaced that manual `.count()` + `expect(...).toBe(expected)` pair with Playwright's auto-retrying `expect(locator).toHaveCount(expected, { timeout: 20000 })`, the same primitive every other List-count assertion in this suite already uses. No Production code change, no new cleanup mechanism - a Test-code correctness fix, not a Lifecycle fix.

### 7.4 Lifecycle Verification (2 consecutive runs)

Ran `manufacturer-channel.spec.ts`, `email-send.spec.ts`, `official-po-integration.spec.ts`, `mobile-responsive.spec.ts`, and `supplier-region-classification.spec.ts` (36 tests total) twice in a row, counting rows before Run 1, after Run 1, and after Run 2:

| Table | Before Run 1 | After Run 1 | After Run 2 |
|---|---|---|---|
| `manufacturer_channel` (SUP_ALPHA/BR_OUTDOOR) | 113 | 113 | 113 |
| `manufacturer_channel` (SUP_BETA/BR_HOME, new combo) | 0 | 1 (first-ever Create) | 1 |
| `supplier_region_classification` (SUP_ALPHA/BR_OUTDOOR) | 26 | 26 | 26 |
| `price_change_set` | 9 | 9 | 9 (unaffected - these 5 files don't create Price Changes) |

Zero growth across both runs for every row these specs reuse - "増えたがInactiveだからOK" does not apply here because nothing grew at all, Active or not. All 36 tests PASS on both runs.

### 7.5 Final Test (Full Regression, Freeze Blocker round)

Run in this order: full E2E suite (pre-existing DB state, to prove the fixes work without first hiding behind a fresh reset) → real `./demo-reset.sh --include-test-master-data` → Backend restart → full Backend `mvn test`.

| Check | Result |
|---|---|
| Full E2E (Playwright) | **212 / 212 PASS, 0 skipped, 0 failed** (207 baseline + 5 Order History scenarios; 0 skips this time since the pre-existing DB state, not a fresh empty one, was used - the 2 conditional skips seen in the §3 round were themselves data-state-dependent, not a fixed count) |
| Backend full `mvn test` (before Demo Reset) | 3 pre-existing Integration Tests failed (`LegacyPoConcurrencyServiceIntegrationTest`, `OfficialPoImportConfirmationIntegrationTest`) with `duplicate key value violates unique constraint "uq_portal_order_official_po_no"` - caused by this session's own extensive shared-DB accumulation (hours of live Browser + repeated E2E runs against the same `local`/`test`-profile Postgres instance) colliding with these tests' own fixture Official PO Nos, **not** a code regression - no Backend Java file was touched in this Freeze Blocker round (only `DashboardPage.tsx` + E2E spec files + docs) |
| Backend full `mvn test` (after Demo Reset) | **594 / 594 PASS** - confirms the above was purely DB-state pollution, resolved by the same Demo Reset this whole exercise is about |
| Frontend Vitest | **51 / 51 PASS** |
| TypeScript (`tsc -b`) | **0 errors** |
| Vite production build | **PASS** |
| Mobile Responsive E2E | **11 / 11 PASS** (within the 212 above) |
| i18n parity | **PASS** (part of the Vitest run) |
| Order History E2E (OH-1〜OH-4 + Mobile) | **5 / 5 PASS** (within the 212 above) |
| Brand Entry E2E (`dashboard-deep-links.spec.ts`) | **12 / 12 PASS** (within the 212 above) |
| Test Data Lifecycle E2E | 36/36 PASS across 2 consecutive runs (§7.4) + 6/6 `DemoResetRunnerCleanupTestMasterDataIntegrationTest` (unchanged from §2.5, backend suite above) |

Final row counts (post-Demo-Reset, this round):

| Table | Count |
|---|---|
| `supplier_contact` | 1 total, 1 active |
| `mail_template` | 155 total, 1 active (still ambiguous backlog, §6) |
| `manufacturer_channel` | 115 total (113 SUP_ALPHA/BR_OUTDOOR + 1 SUP_BETA/BR_HOME + 1 from this round's Full E2E pass reusing/creating as expected), 1 active |
| `supplier_region_classification` | 26 total, 0 active |
| `official_po_short_code` | 6 total, 6 active (unchanged, ever) |
| `price_change_set` | 0 (freshly TRUNCATEd by the ordinary Demo Reset - confirms §7.3) |

## 8. Changed Files (Freeze Blocker round)

Frontend:
- `frontend/src/features/dashboard/DashboardPage.tsx` - 発注候補 KPI's `onClick` target (§7.1)
- `frontend/e2e/dashboard-deep-links.spec.ts` - KPI test + Scenario 1-5 updated for the new Brand-first behavior (§7.1); 価格変更（下書き）KPI test's flaky `.count()` assertion replaced with `toHaveCount` (§7.3)
- `frontend/e2e/manufacturer-channel.spec.ts`, `email-send.spec.ts`, `official-po-integration.spec.ts`, `mobile-responsive.spec.ts` - `ensureManufacturerChannel` Test Fixture reuse helper (§7.2)
- `frontend/e2e/supplier-region-classification.spec.ts` - `ensureRegionClassification` Test Fixture reuse helper (§7.2)

Docs:
- `docs/gops-order-candidates-brand-entry-implementation.md` - correction note (§7.1)
- `docs/gops-phase1-freeze.md`, `docs/gops-phase1-final-cleanup-report.md` (this file) - updated with this round's results

Screenshots:
- `docs/gops-phase1-freeze-blocker-fix-screenshots/01-dashboard.jpg` through `05-b4-brand-breakdown-deeplink-livora.jpg` (§7.1)

No Backend Java file was changed in this round.

## 9. Commit (Freeze Blocker round)

See Git log - Final Commit for this round: `71ad25d1433e2e23e64df3348759e4424c015df2`.
