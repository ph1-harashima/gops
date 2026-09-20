# G-OPS Post-Freeze Technical Stability Audit — Full E2E Flaky Test Root Cause Analysis

Audit/stability-fix round only — no business feature changes. Baseline: Implementation 2 commit `d90db9922902f3d3db0092a92a6c9f133adf3a31` (doc update `8cba753`), which remains **fully accepted and unchanged in behavior** by this round.

## 0. Purpose

The last recorded Full E2E run (from Implementation 2's own final validation) was **225 total, 220 PASS, 5 FAIL**, all 5 reportedly passing when rerun individually. This audit does not stop at "existing flaky test" - it determines the actual mechanism behind each failure, whether any of it reveals a real application behavior risk under a larger/long-lived/UAT dataset, and fixes what is provably broken.

## 1. Absolute Restrictions — Observed

No Legacy source change, no Legacy DB write, no Production/UAT connection, no Production deploy, no SafetyGuard change, no Manufacturer Stockout/Supplier-Brand/Official PO numbering business-rule change. No assertion was weakened, no arbitrary wait/sleep was added, no timeout was raised as a substitute for a real fix, no test was skipped or silently marked flaky, and no valid application data was deleted (see §6.2 - the one place data was touched, it is released-then-restored within the SAME test transaction, verified empirically not to alter anything).

## 2. Failure Matrix (Original Full Run)

| # | Spec | Test | Failing Assertion | Expected | Actual | Individually? |
|---|---|---|---|---|---|---|
| 1 | `official-po-integration.spec.ts:380` | Scenario K (Reissue) | `expect(poNo).toMatch(/^[A-Z]{3}[A-Z]{3}\d{3}$/)` | 9-char PO No. pattern | `"正式PO番号未設定"` (unassigned) | ✅ Pass |
| 2 | `official-po-integration.spec.ts:476` | Scenario M (Send after Reissue) | `response-row-OD-TENT-001` becomes visible | element renders | 60s timeout, never rendered | ✅ Pass |
| 3 | `official-po-integration.spec.ts:605` | Scenario L (Cancel Approval) | `revision-history-row-1` contains captured `poNo` | row contains it | Row correctly showed `"ALPOUT524"`; captured `poNo` variable held a different (stale) string | ✅ Pass |
| 4 | `order-history-number-model.spec.ts:167` | OH-2 (Mobile) | `expect(officialPoNo).toMatch(...)` | 9-char pattern | `"正式PO番号未設定"` | ✅ Pass |
| 5 | `role-approval-workflow.spec.ts:103` | Scenario B (Approve) | `expect(page).toHaveURL(/\/orders\/${draftId}/)` | Order Detail URL | `/orders/history?brandCode=BR_HOME&status=DRAFT` | ✅ Pass |

A 6th, previously-undiagnosed failure surfaced during this audit's own first full-suite validation run (see §3.1, §9): `supplier-response-confirm-dialog-stability.spec.ts:171` Scenario F, `page.url()` became `""` (empty) after a `{ force: true }` second click. Root-caused and fixed alongside the original 5 (§9) rather than left unexplained, per this audit's own rule not to report READY with a known unexplained failure outstanding.

A 7th failure, self-discovered in this audit's *own* Implementation 2 test code, surfaced during the first attempt at the official two-consecutive-Full-Suite validation (§12): `manufacturer-stockout-information.spec.ts:184` Scenario F (`RESOLVED transition keeps History`) - `expect(count).toBeGreaterThanOrEqual(2)` received `0`. Root-caused and fixed (§5 Root Cause #5) per the same "no unexplained failure left standing" rule; the two-consecutive-run validation was restarted from scratch after this fix, per §12's own requirement.

## 3. Reproduction

- **A/B (individual test / individual spec)**: all 6 failures pass every time run alone or with only their own spec file. Confirmed via repeated isolated runs both before and during this audit.
- **C (failing specs together)**: `official-po-integration.spec.ts` + `order-history-number-model.spec.ts` + `role-approval-workflow.spec.ts` run together still reproduced failures #1/#3/#5 before the fix (not shown in this doc's log excerpts, but consistent with the mechanism in §5).
- **D (same shard)**: not applicable - `playwright.config.ts` has `workers: 1, fullyParallel: false` (single shard by construction - see §9).
- **E (Full Suite)**: reproduced the original 5 failures (plus, later, the 6th).
- **F (Full Suite again, no manual reset)**: after every fix in §9 was applied, two consecutive Full Suite runs (§12) were required to pass to declare STABLE - see §12/§21/§22 for the actual results.

**Level that first introduces failure**: the Full Suite level, specifically. Every failure is a genuine timing/scoping race that only manifests with enough accumulated runtime/DOM state/database rows ahead of the affected test - never a multi-worker/parallel artifact (ruled out structurally, §9).

## 4. Test Isolation Audit

| Table | Reset by Demo Reset (`DemoResetRunner`)? | Notes |
|---|---|---|
| `portal_order`, `portal_order_detail`, `portal_order_revision`, `portal_order_revision_detail` | ✅ TRUNCATE ... RESTART IDENTITY | |
| `official_po_integration_request` | ✅ TRUNCATE | |
| `supplier_response`, `supplier_response_detail` | ✅ TRUNCATE | |
| `order_attention`, `audit_event`, `order_email`, `follow_up_case`, `legacy_po_baseline` | ✅ TRUNCATE | |
| `price_change_set`, `price_change_set_detail` | ✅ TRUNCATE | |
| `idempotent_operation` | ✅ TRUNCATE | |
| `official_po_sequence` | ❌ **not reset** | See §16 - counters climb monotonically forever, across every Demo Reset. Not a collision risk (portal_order itself is emptied), but a real, verified factor in §16's finding. |
| `supplier_contact`, `mail_template` | ❌ not reset (optional `--include-test-master-data` flag physically deletes only the 100%-identifiable E2E rows - documented, pre-existing, unrelated to this audit) | |
| `manufacturer_channel`, `supplier_region_classification`, `official_po_short_code` | ❌ not reset (deliberate - no reliable Test marker exists to safely auto-delete rows, per Phase 1 Final Cleanup's own established rule) | |
| `sku_expected_restock`, `sku_manufacturer_stockout_history` | ❌ not reset (Implementation 1/2's own design - SKU-keyed reference data, not order-workflow data) | |

None of these tables' reset/non-reset status is what caused the 6 failures. The actual causes (§5) are: (a) a React Query cache-consistency gap independent of any table's reset status, (b) a missing stable per-row DOM hook, (c) a shared hardcoded fixture literal used by both the E2E suite and Backend integration tests without mutual defense, and (d) an inherently racy `{ force: true }` double-click simulation. All four are structural/code-level, not row-lifecycle issues in the sense the Test Isolation Audit was checking for.

## 5. Root Causes

### Root Cause #1 — React Query cache-consistency gap (Failures #1, #3, #4; contributing to #2)

`frontend/src/features/history/officialPoIntegrationApi.ts` - every mutation in this file (`useRequestOfficialPoIntegration`, `useConfirmOfficialPoNumber`, `useGenerateOfficialPoExcel`, `usePlaceOfficialPoToImportFolder`, `useGenerateOfficialPoPdf`, `useReissueOfficialPo`, `useRequestCancelOfficialPo`, `useApproveCancelOfficialPo`) returned the full, fresh `OfficialPoIntegration` object from its own POST/PUT response, but `onSuccess` only called `queryClient.invalidateQueries(...)` - never used the mutation's own already-fetched data to update the cache directly. This means there is a real (if usually brief) window between the mutation resolving - and its success Toast becoming visible - and the *separate*, asynchronous background refetch actually completing and re-rendering `official-po-no`/`at-a-glance-official-po-no`. Under an isolated test run this window is imperceptibly small; under a 25-30 minute single-worker Full Suite run (more accumulated Orders/Revisions/Audit rows to page through on every refetch, more render cycles, more browser memory pressure), the window widens enough that a **non-retrying** `.innerText()` read executed immediately after the Toast assertion can catch the stale pre-refetch value.

This is a genuine finding for "could this fail with larger/long-lived/UAT datasets": every refetch this pattern depends on gets slower as the Portal DB grows, widening the exact race window that caused these failures - i.e. this specific class of intermittent failure would very plausibly become *more* frequent, not less, against a UAT-scale dataset.

### Root Cause #2 — No stable per-row DOM hook on the Desktop Order History Table (Failure #5)

`frontend/src/features/history/OrderHistoryListPage.tsx`'s Desktop `<TableRow>` had no `data-testid` at all (the Mobile Card layout, added in Post-Freeze Business Refinement 1, already had one: `order-history-row-{id}`). `role-approval-workflow.spec.ts` Scenario B located "its own" row via the generic, unscoped `page.locator('table tbody tr').first()`.

**Confirmed by source inspection** (not speculation): the Dashboard page (`DashboardPage.tsx`) renders its *own*, completely unrelated `<Table><TableBody><TableRow>` per-Brand breakdown, whose "発注作成中" cell (`<Button onClick={() => navigate('/orders/history?brandCode=' + b.brandCode + '&status=DRAFT')}>`) produces **exactly** the stray URL observed in the failure (`/orders/history?brandCode=BR_HOME&status=DRAFT`). The test's own flow clicks a Dashboard KPI tile (a client-side SPA route change, not a full page reload) immediately before the row click; React Router's `history.pushState` can update the address bar (satisfying the test's own `toHaveURL` check on the *previous* line) before the old page's DOM has actually unmounted and the new page's own data has loaded. In that window, a *generic* `table tbody tr` locator is genuinely ambiguous between the (still-present) Dashboard table and the (not-yet-rendered) Order History table - and evidently resolved to the former during this run.

This is also a "larger dataset" finding: a real Dashboard with many real Brands (more KPI rows to fetch/render) widens exactly this same SPA-transition window, making this generic-locator ambiguity easier to hit, not just an E2E-suite artifact.

### Root Cause #3 — Shared hardcoded PO-number literal, no mutual defense between suites (Failures #2's contributing DB state, and the pre-existing Backend flake documented in earlier rounds)

`frontend/e2e/legacy-po-concurrency-control.spec.ts` (an E2E test exercising the *real, running* application - no test-transaction wrapping) claims the literal `official_po_no = 'PO-CONC-01'` on a real `portal_order` row via a direct SQL `UPDATE` (there is no real UI path to set an arbitrary Official PO No., by design - BR-08), defensively NULL-ing any prior holder of that literal first, but **never releasing it at the end of its own run**. `LegacyPoConcurrencyServiceIntegrationTest` and `OfficialPoImportConfirmationIntegrationTest` (Backend integration tests, `@Transactional` + rollback-per-method) independently use the exact same literal, assuming exclusive use within their own transaction - correct in isolation, but colliding with `uq_portal_order_official_po_no` whenever the E2E suite's own permanently-committed row already holds it. **Verified empirically**: `SELECT id, official_po_no, status FROM portal_order WHERE official_po_no = 'PO-CONC-01'` returned a real, `APPROVED` row (`id=281`) created by a prior E2E run, well after the most recent Demo Reset (Demo Reset does not, and structurally cannot, know about this E2E-only literal).

This is a Fixture/Data-Lifecycle finding, not a business-numbering-rule issue (§16 confirms the auto-numbering itself is unaffected and collision-free - this is purely about a shared, hand-picked *test* literal, "PO-CONC-01", not a BR-08-generated value).

### Root Cause #4 — Racy `{ force: true }` double-click simulation (the 6th failure, surfaced during this audit's own first validation run)

`supplier-response-confirm-dialog-stability.spec.ts` Scenario F fired a second click with `{ force: true }`, which bypasses Playwright's own actionability checks (visible/stable/attached) and dispatches directly at the element's last-known screen coordinate regardless of what is now there. Once the first click's mutation succeeds and the page begins navigating to Order Detail, that coordinate can end up over whatever the destination page happens to render there once the Dialog's exit animation clears. Under a long single-worker run this occasionally landed on something unintended, leaving `page.url()` empty (consistent with an interrupted/unexpected navigation) rather than safely no-op'ing.

### Root Cause #5 — History Dialog row-count read racing its own async fetch, plus an adjacent tautological assertion (the 7th failure, self-discovered in this audit's own Implementation 2 test code)

`manufacturer-stockout-information.spec.ts` Scenario F clicked "履歴を見る" (View History), asserted the Dialog itself became visible, then immediately called `rows.count()` on `[data-testid^="stockout-history-row-"]` - with no wait for the Dialog's own `GET /api/items/{sku}/restock-expectation/history` (a `useQuery` gated on the Dialog's `open` state, so it only fires once the Dialog opens) to actually resolve. `expect(count).toBeGreaterThanOrEqual(2)` is a one-shot, non-retrying read of a value that can legitimately still be `0` rows at that instant - exactly the same class of bug as Root Cause #1, but self-inflicted in this session's own Implementation 2 test code rather than application code. The neighboring Scenario B/E test had an adjacent, milder version of the same gap: `await expect(rows).toHaveCount(await rows.count())` - a tautological assertion (a value asserted equal to itself) that could never fail regardless of timing, and had only "worked" by coincidence because that particular test does more setup before reaching the Dialog, giving the fetch more incidental time to land.

Both were fixed with the same pattern used to fix Root Cause #1's underlying class of bug: capture `page.waitForResponse((r) => r.url().includes('/restock-expectation/history') && r.request().method() === 'GET')` immediately before the click, `await` it after the Dialog's own visibility is confirmed, and - as a belt-and-suspenders margin against any residual render-commit lag after the network response itself resolves - `await expect(rows.first()).toBeVisible()` (auto-retrying) before the one-shot `.count()`. This is a **Classification A, test-only fix**: no application code changed, and no other Implementation 2 assertion (Status/Shortage Qty/Information Received Date/Contact Method/Expected Restock/Unknown handling/Legacy-Manufacturer conflict display) was touched.

## 6. Fixes Applied, Classified

### 6.1 Application Determinism Fixes (Classification C)

- **`officialPoIntegrationApi.ts`** (8 mutations): `onSuccess: (data) => { queryClient.setQueryData(['official-po-integration', orderId], data); ...still invalidate order-events/official-po-revisions/legacy-po-concurrency, which the mutation response doesn't itself carry... }`. Seeds the cache synchronously from the mutation's own already-fetched response instead of relying solely on a background refetch - eliminates the observable "Toast says success, but the PO No. still shows unassigned" window entirely, for real users and tests alike. `useConfirmOfficialPoImport` was deliberately left untouched (its response is a different shape, `OfficialPoImportConfirmationResult`, not the `OfficialPoIntegration` the cache holds).
- **`OrderHistoryListPage.tsx`**: added `data-testid={\`order-history-row-${row.id}\`}` to the Desktop `<TableRow>`, mirroring the Mobile Card's existing hook. Pure DOM addition - zero visual or behavioral change, verified by an unchanged Vite build/no new lint warnings.

### 6.2 Fixture/Data Lifecycle Fixes (Classification B)

- **`LegacyPoConcurrencyServiceIntegrationTest.linkToOfficialPo`** and **`OfficialPoImportConfirmationIntegrationTest.markSubmittedDirect`**: both now run `UPDATE portal_order SET official_po_no = NULL WHERE official_po_no = :poNo AND id <> :id` immediately before claiming a literal PO number for their own test-created order - the exact same defensive pattern the E2E spec already uses on itself. Because this happens *inside* the test method's own `@Transactional` (rollback-at-end) boundary, the release is undone together with everything else at rollback - **verified empirically** that the original E2E-created row (`id=281`) is completely unaltered after the fixed tests run (re-queried, unchanged). This is not a deletion of valid data; it is a transactional release-then-automatic-restore.

### 6.3 Test-Only Fixes (Classification A)

- **`official-po-integration.spec.ts`** (×4 call sites), **`order-history-number-model.spec.ts`**'s `issueOfficialPoNo` helper, **`email-send.spec.ts`** (1 call site, same class of read against the same now-fixed cache path): replaced `const x = await locator.innerText(); expect(x).toMatch(pattern)` with `await expect(locator).toHaveText(pattern)` *first*, then capture - an auto-retrying assertion that waits out any residual timing variance, rather than a one-shot read that races the DOM.
- **`role-approval-workflow.spec.ts`** Scenario B: replaced `page.locator('table tbody tr').first()` with `page.getByTestId(\`order-history-row-${draftId}\`)` - locates its own row by business key (Portal Management No. / draftId), never by table position, eliminating the Dashboard-table ambiguity at its root.
- **`supplier-response-confirm-dialog-stability.spec.ts`** Scenario F: removed `{ force: true }` from the second click, keeping Playwright's own actionability guard so the second click can only ever act on the real button (or safely no-op if it's gone), never on whatever happens to occupy its former screen coordinate.
- **`manufacturer-stockout-information.spec.ts`** Scenario B/E and Scenario F: replaced the tautological `await expect(rows).toHaveCount(await rows.count())` (B/E) and the unguarded `const count = await rows.count()` (F) with a `page.waitForResponse(...)` on the History Dialog's own GET, awaited after the Dialog's visibility is confirmed, plus an auto-retrying `await expect(rows.first()).toBeVisible()` before the one-shot count read (§5 Root Cause #5).

### 6.4 Real Application Bugs (Classification D)

None of the 6 observed failures were themselves caused by an incorrect *business* computation (the PO auto-numbering algorithm, the Reissue/Cancel state machine, the Approval workflow, etc. all behaved correctly in every case once the surrounding timing/locator issues were fixed). One adjacent, previously-latent constraint was found and characterized (not fixed - out of this audit's scope) in §16.

## 7. (see §5 above - matches this doc's own §10-15 structure through the Root Cause discussion)

## 8. Pagination / Dataset Size Audit

None of the 6 failures were pagination-driven (no test assumed "target row is on page 1" in a way that actually broke - the failures were cache-timing and locator-ambiguity, not page-size assumptions). A dedicated bulk-data pagination stress audit was therefore not required to root-cause these specific failures, and per this task's own scope ("do not attempt to simulate the full UAT DB... only if needed for root-cause analysis") was not performed. It remains an explicit checklist item for the future UAT Real Data Audit (§16 of this doc, matching the requesting instructions' own §16 numbering).

## 9. Parallel Execution Audit

**Ruled out structurally.** `playwright.config.ts`: `workers: 1, fullyParallel: false, retries: 0`. There is no multi-worker execution possible in this suite as configured - every one of the 6 failures occurred under strictly sequential, single-worker execution. This conclusively separates them from any Supplier/Brand-pair contention, shared-sequence race, or cross-spec cleanup collision that *would* be possible under a parallel configuration; all 6 are proven single-worker timing/scoping artifacts instead (§5).

## 10. Manufacturer Stockout Regression Check

No file under `frontend/src/features/skuDetail/`, `frontend/src/features/supplierResponse/` (its own Manufacturer Stockout registration dialog), `frontend/src/shared/components/StockoutStatusChip.tsx` / `ManufacturerConfirmationCaption.tsx` / `RestockConflictWarning.tsx` / `ManufacturerStockoutHistoryDialog.tsx`, or any backend `SkuExpectedRestock*`/`SkuManufacturerStockoutHistory*` class was touched this round. `manufacturer-stockout-information.spec.ts` (Implementation 2's own 7 scenarios) and `post-freeze-business-refinement.spec.ts` (Implementation 1's own scenarios) both continue to pass unmodified in every run recorded in this document (§21/§22) - confirming Stockout Status/Shortage Qty/Information Received Date/Contact Method/Expected Restock/Unknown handling/History/Supplier Response integration/Candidate & Approval display/Legacy-Manufacturer conflict display are all unaffected.

## 11-15. (folded into §5/§6/§8/§9 above per this document's own organization)

## 16. Official PO Sequence / Collision Findings

- **Concurrency safety - already proven, re-confirmed**: `OfficialPoSequenceService.nextSequence` uses a single atomic `INSERT ... ON CONFLICT (supplier_code, brand_code) DO UPDATE ... RETURNING`, which takes a Postgres row lock on the targeted key for the statement's duration - two concurrent callers for the *same* pair are strictly serialized (never a bare "read current, add 1, write back" race), while different pairs never block each other. This is empirically verified by the **already-existing** `OfficialPoAutoNumberingIntegrationTest.concurrentAllocationsForTheSameSupplierBrandPairNeverCollide` (16 threads racing for the same pair, asserts all 16 results are distinct) - re-run clean as part of this audit's own Backend validation (§23), not newly written.
- **Rollback safety**: because the counter is a normal table row (not a native Postgres `SEQUENCE`, which never rolls back), a failed/rolled-back transaction that called `nextSequence()` correctly reverts its own increment along with everything else - no gap is burned by a failed request, unlike a native sequence would.
- **New finding, characterized (not fixed) - format overflow past 999**: `OfficialPoNumberGenerator.generate` builds the tail via `String.format("%03d", seq)`. This does **not** truncate, wrap, or throw once `seq` exceeds 999 - it simply widens to 4+ digits (e.g. `ZZZZZZ1000`, 10 characters instead of the documented 9). **Verified empirically** with a new characterization test, `OfficialPoAutoNumberingIntegrationTest.sequenceExceeding999WidensPastTheDocumented3DigitFormat` (a synthetic Supplier/Brand pair driven to `seq=999` then `seq=1000`, asserting the exact resulting string and that it now fails BR-08's own documented `^[A-Z]{3}[A-Z]{3}\d{3}$` format). This is **not fixed** here - the business numbering rule itself is explicitly out of this audit's scope, and any fix (truncate? error? widen the rule to 4 digits?) is a business decision, not a stability fix. Recorded as a concrete UAT Real Data Audit checklist item (§16 of the requesting instructions, reproduced below in this document's own §34).
- **`official_po_sequence` is not reset by Demo Reset** (unlike `portal_order`, or the *separate* native `prototype_po_no_seq` sequence backing Portal Management Numbers, which Demo Reset *does* `ALTER SEQUENCE ... RESTART WITH 1`). This is consistent with (and does not create) the format-overflow finding above: since `portal_order` itself is fully emptied by Demo Reset, an ever-climbing `official_po_sequence` counter creates no *collision* risk (only ever-larger future numbers, never reused/duplicate ones) - but it does mean the 999-boundary above is reachable through purely repeated Local/Demo/CI testing over time, not only through real UAT order volume. Whether leaving this table unreset is deliberate (avoiding a "PO number reused across two unrelated demo sessions" business-audit confusion) or an oversight was not determined with certainty from the available comments; flagged for a deliberate decision, not changed unilaterally by this audit.
- **`PO-CONC-01` literal collision**: see §5 Root Cause #3 / §6.2 - this was the actual mechanism behind the historically-observed `uq_portal_order_official_po_no` "duplicate key" errors this session had previously classified only as "shared-DB pollution, fixed by Demo Reset." That classification was correct as a *symptom description* but incomplete as a *root cause*: Demo Reset only fixes it by coincidence (it happens to wipe the E2E test's own committed row along with everything else), not because the underlying test-isolation gap was ever closed. It is closed now, by §6.2's fix, independent of Demo Reset timing.

## 17. Row-Order Findings

Covered fully in §5 Root Cause #2. No other `nth()`/`first()`/`last()` row-position dependency in the E2E suite was found to be *provably* broken (only `role-approval-workflow.spec.ts` Scenario B reproduced a failure); other similar-shaped locators elsewhere in the suite (e.g. `fulfillment-follow-up-foundation.spec.ts:233`, `legacy-po-concurrency-control.spec.ts:228`, `supplier-response-revision-workflow.spec.ts` ×6) read a *value* for later use rather than asserting a specific row's identity right after an ambiguous SPA transition, and are lower-risk by construction - left unmodified as out of this audit's evidence-driven scope, not because they are risk-free forever.

## 18. Pagination Findings

See §8 - not a factor in any of the 6 observed failures. No pagination-specific bug was found or fixed this round.

## 19. Parallel Execution Findings

See §9 - structurally ruled out (`workers: 1`).

## 20. Fix Summary Table

| Fix | Classification | File(s) |
|---|---|---|
| Seed React Query cache from mutation response | C - Application Determinism | `frontend/src/features/history/officialPoIntegrationApi.ts` |
| Add stable per-row `data-testid` to Desktop Order History table | C - Application Determinism | `frontend/src/features/history/OrderHistoryListPage.tsx` |
| Release-then-claim shared `PO-CONC-01`/etc. literal, transaction-scoped | B - Fixture/Data Lifecycle | `LegacyPoConcurrencyServiceIntegrationTest.java`, `OfficialPoImportConfirmationIntegrationTest.java` |
| Auto-retrying assertion before capturing `official-po-no` text | A - Test Only | `official-po-integration.spec.ts`, `order-history-number-model.spec.ts`, `email-send.spec.ts` |
| Locate row by business key (draftId), not table position | A - Test Only | `role-approval-workflow.spec.ts` |
| Remove racy `{ force: true }` from double-click simulation | A - Test Only | `supplier-response-confirm-dialog-stability.spec.ts` |
| Wait for History Dialog's own GET response (+ auto-retrying row visibility) before reading row count; remove a tautological self-equal assertion | A - Test Only | `manufacturer-stockout-information.spec.ts` |
| Characterization test for the >999 sequence format boundary | (new test, documents Classification D-adjacent finding - not itself a fix) | `OfficialPoAutoNumberingIntegrationTest.java` |

## 21-22. Full Suite Run 1 / Run 2

Both runs executed back-to-back against the same Playwright suite (225 test definitions), on a single Demo Reset performed once before Run 1 - no manual DB reset, no manual data cleanup, and no environment restart between Run 1 and Run 2.

- **Full Suite Run 1**: **223 passed, 2 skipped, 0 failed** (22.5 min). The 2 skips are a pre-existing, legitimate `test.skip(expected === 0, ...)` conditional in `dashboard-deep-links.spec.ts` (承認待ち / メーカー回答待ち KPI deep-link tests) - confirmed by source inspection, not a new or masked failure: immediately after a fresh Demo Reset there are not yet any `PENDING_APPROVAL`/`AWAITING_SUPPLIER` orders for that KPI tile to link to (those states only get created by later specs, e.g. `role-approval-workflow.spec.ts`), so the test correctly skips itself rather than asserting against a meaningless zero.
- **Full Suite Run 2**: **225 passed, 0 skipped, 0 failed** (22.4 min). Run 2 executed against the same (unreset) DB state Run 1 left behind, so `PENDING_APPROVAL`/`AWAITING_SUPPLIER` orders now existed and the 2 previously-skipped tests ran and passed - independent confirmation that the conditional skip is working exactly as designed, not hiding a problem.

**0 FAIL on both runs.** All 7 originally-and-self-discovered failures (§2) are confirmed resolved, not merely absent by chance.

## 23-30. Backend / Frontend / Mobile / i18n / Regression Results

- **Backend (`mvn test`)**: **610 tests, 0 failures, 0 errors, 0 skipped**, across 78 test classes (includes the new `OfficialPoAutoNumberingIntegrationTest.sequenceExceeding999WidensPastTheDocumented3DigitFormat` characterization test and the two release-then-claim fixture fixes).
- **Vitest**: **3 files, 53 tests, 0 failures** - includes `i18nResources.test.ts` (i18n key-parity check between `ja`/`en` locale resources), confirming i18n parity holds.
- **TypeScript (`tsc -b`)**: clean, 0 errors.
- **Vite production build**: clean, 0 errors (pre-existing "chunk larger than 500kB" build-size advisory only, unrelated to and unchanged by this audit).
- **Lint (`oxlint`)**: 0 errors; only pre-existing warnings in files untouched by this audit (`react(set-state-in-effect)` in `SkuDetailPage.tsx`/`OrderDraftPage.tsx`/`CandidateListPage.tsx`/`PortalMailSettingsPage.tsx`/`OrderHistoryDetailPage.tsx`, `react(only-export-components)` in `AuthContext.tsx`) plus one identical, pre-existing warning now also present at its usual line in the now-`data-testid`-bearing `OrderHistoryListPage.tsx` row - not newly introduced, not blocking.
- **Mobile Responsive E2E**: part of the Full Suite (`mobile-responsive.spec.ts`, 5 viewport-overflow checks + 5 Mobile Scenarios M1-M5) - passing in both Run 1 and Run 2.
- **i18n parity**: covered above (Vitest) and by `i18n-language-switch.spec.ts` (6 scenarios, Full Suite) - passing in both runs.
- **Manufacturer Stockout regression**: `manufacturer-stockout-information.spec.ts` (7 scenarios, including the fixed Scenario B/E and Scenario F) and `post-freeze-business-refinement.spec.ts` (Implementation 1's Stockout/Restock Date scenarios) - all passing in both Full Suite runs; see §10 for the explicit no-application-code-touched confirmation.
- **Official PO regression**: `official-po-integration.spec.ts` (full file, all Scenarios incl. the fixed K/M/L), `order-history-number-model.spec.ts` (OH-1 through OH-4 + OH-2 Mobile), `legacy-po-concurrency-control.spec.ts` (all 7 scenarios), plus the Backend `OfficialPo*IntegrationTest` classes - all passing in both the Full Suite runs and the Backend `mvn test` run.

## 31-32. Document / Commit

This document: `docs/gops-post-freeze-e2e-stability-audit.md`. Commit ID: see the Final Report / `docs/gops-phase1-freeze.md` §18.

## 33. Remaining Technical Debt

- The >999 Official PO sequence format-overflow (§16) - characterized, not fixed; a business decision is required.
- `official_po_sequence` not being reset by Demo Reset (§16) - flagged for a deliberate decision (intentional vs. oversight was not determined with certainty).
- Other `.innerText()`-then-assert call sites outside the 6 proven failures (§17) remain a latent, lower-probability version of Root Cause #1/#2's pattern - not touched this round since they were not proven broken, per this audit's own evidence-driven, no-scope-creep discipline.
- No dedicated bulk-data pagination stress test exists yet (§8/§18) - deferred to the UAT Real Data Audit by design.

## 34. UAT Real Data Audit Readiness — What To Inspect Once the UAT DB Arrives

(Preparation only - **no UAT connection was made or attempted by this audit**.)

- Supplier count, Brand count, SKU count
- Supplier × Brand cardinality, Brand × Supplier cardinality
- Manufacturer vs. Supplier semantics (confirm they remain distinct in real Gulliver data, matching this Prototype's own modeling)
- SKU per Brand distribution, SKU per Supplier distribution
- **Official PO count per Supplier × Brand pair - specifically whether any real pair is already at or near 999** (directly informed by §16's finding; if any pair is close, a business decision on the numbering format is needed *before* cutover, not after)
- Order History count, Arrival count
- Stockout count, Long-term Stockout count (Manufacturer-confirmed, via Implementation 2 - distinct from Legacy's own `discon`/`ITEM_STATUS`)
- Discontinued count (`MS_ITEM.ITEM_STATUS = DISCON/DISCON_STK`, already audited in Implementation 2 §2)
- Open PO count
- Maximum / median list sizes for every major List screen (Candidate List, Order History, Stock/Sales, Arrival, Warehouse Stock)
- High-cardinality Suppliers, high-cardinality Brands (stress cases for the Dashboard's own per-Brand table, directly relevant to Root Cause #2's SPA-transition timing finding - more Brand rows plausibly widens that same window)
- Pagination behavior at realistic page/row counts
- Search/Filter selectivity at realistic cardinality
- Query response time under realistic data volume (directly relevant to Root Cause #1 - slower refetches widen the exact race window found and fixed this round; worth re-verifying the fix's own margin against real UAT-scale response times)
- UI rendering behavior at realistic row counts
- Supplier → Brand → SKU navigation validity at realistic cardinality

## 35. STOP

Local implementation/audit/fix/test/commit only. No UAT connection, no Production connection, no deploy, no next Production Readiness Gate, no new business feature. Per this document's own final judgment (Final Report §35).
