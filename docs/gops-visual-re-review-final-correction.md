# G-OPS Visual Re-Review Final Correction

Scope: exactly the 2 remaining P2 Findings from ChatGPT's 30-screenshot Visual
Re-Review of commit `d50ac098e1451b828999cfb83c9f6bed28a2aa56` — Mobile Supplier
Response UX, and Order History Search. Nothing else in that commit's Accepted
scope (Number Terminology, Reissue/Revision, Cancellation status, Mobile
Approval, Mobile Candidate, Mobile SKU Detail title, Supplier Response save
button visibility) was touched.

## 1. Baseline

Accepted implementation commit: `d50ac098e1451b828999cfb83c9f6bed28a2aa56`
("Post-Freeze Visual Walkthrough Findings Fix: 7 Findings from the Business
Scenario Visual Walkthrough"). No commits landed between that baseline and
this correction.

## 2. Finding A Root Cause — Mobile Supplier Response UX

`SupplierResponsePage.tsx`'s SKU response table had never been given a
Mobile-specific layout. Every other List/Table screen in this app
(`CandidateListPage`, `OrderHistoryListPage`) already switches to a
1-row-per-Card layout below the `sm` (600px) breakpoint; this screen was the
one exception, so below 600px its 9-column Desktop `<Table>` rendered exactly
as-is — item names wrapping one or two characters per line, every input
squeezed to a few pixels wide, and the row requiring horizontal scroll to
read at all. This is a distinct problem from Finding #3 (the earlier fix,
already Accepted, kept the Save button from being visually covered by the
unsaved-changes Warning) — Finding #3 never touched the table's own layout.

## 3. Finding A Fix

`SupplierResponsePage.tsx`: added the same `useMediaQuery(theme.breakpoints
.down('sm'))` / `isCardLayout` idiom already used elsewhere in this codebase.
Below `sm`, each SKU renders as its own `Card` (SKU code + product name
header, then 発注数量/回答数量/希望納期/回答納期/差異/回答理由・メモ/供給状況/
確認事項/欠品情報として登録 — every field the Desktop Table already has,
reusing the exact same i18n keys, `data-testid`s, edit handlers, and
readOnly/`isEditable` gating). The Desktop Table itself (`sm` and above) is
completely unchanged — same JSX, same conditions, now simply the `else`
branch of the new `isCardLayout` ternary. No Business Logic (Save/Confirm/
Agree/Revision/Stockout-registration mutations, validation, required-field
gating) was touched — only which layout renders the same data and the same
event handlers.

## 4. Finding A Browser Verification

Driven live at ~390–500px width (Claude-in-Chrome's own window-resize floor
prevented an exact 390px in this environment, same documented limitation as
the prior Findings Fix round; the layout switch itself is driven by CSS width,
so 500px still exercises the identical `sm` breakpoint code path) through the
real Business UI — create Draft → Approve → Official PO request/Excel →
Demo Send → Supplier Response, purchase01/admin01 role switch, no Test API,
no DB shortcut:

- A01: Card layout renders immediately, SKU code and product name both fully
  readable on their own lines, no per-character wrapping.
- A02: Confirmed Qty (`9`) and Confirmed Delivery Date (`2026/10/10`, entered
  via the DOM `value` accessor since typing digits directly into a native
  `<input type=date>` re-triggers the same keystroke-order tooling quirk
  recorded in the prior Findings Fix round — not an application bug) both
  entered directly on the Card, full-width, no truncation.
- A03/A04: scrolled the page's own nested scroll container to its true
  `scrollHeight` (not just a fixed number of mouse-wheel ticks, which — as in
  the prior round — can under-scroll a short page and produce a false
  "covered" reading) and confirmed via `document.elementFromPoint()` at the
  Save button's own screen coordinates that the Save `<button>` itself, not
  the Warning `<div>`, is the topmost hit-testable element
  (`hitTestIsBtn: true`). Clicked it for real — "回答を保存しました。"
  succeeded, and 確認事項 correctly shows 数量変更あり/納期変更あり with the
  差異 section reading `11 → 9` / `null → 2026-10-10` — the same Business
  Logic the Desktop Table has always driven, now rendered on a Card.
  `document.documentElement.scrollWidth === clientWidth` (500 === 500)
  confirmed no horizontal overflow throughout.

## 5. Finding B Root Cause — Order History Search

No code defect. Root-caused via the 3-part audit the task specified, all
converging on the same conclusion:

**Frontend** (`OrderHistoryListPage.tsx`): the search input is intentionally
local state (`orderNoKeywordInput`) that only commits to the URL — and
therefore only triggers a fresh Backend request — on Enter or blur (an
explicit, already-documented design choice: "typing a keyword must not fire a
fresh Backend request on every keystroke"). No debounce exists because none is
needed for an Enter/blur-committed field. `useOrderHistory`'s TanStack Query
`queryKey` includes the full `filter` object, so a changed `orderNoKeyword`
always produces a fresh query — no stale-cache path exists. `updateFilter`
always calls `next.set('page', '0')` on any filter change.

**Backend** (`OrderHistoryService.buildSpecification`): `orderNoKeyword` is a
single case-insensitive (`cb.lower`, keyword lower-cased in
`normalizeKeyword`) `LIKE '%keyword%'` OR'd across `draftNo`, `prototypePoNo`
(Portal管理番号), and `officialPoNo` (正式PO番号) — a real DB-pushed
`Specification` composed with `Pageable`, not an in-JVM stream filter over a
fetched page (confirmed by reading the Phase 8-J rewrite's own Javadoc and the
`Page<PortalOrder> result = portalOrderRepository.findAll(spec, pageable)`
call site).

**Browser** (live, this session, network-traced): typing `ALPOUT696` and
pressing Enter fires `GET /api/orders/history?orderNoKeyword=ALPOUT696&page=0
&size=20` → `200`, narrowing "40件の発注" to "1件の発注" showing exactly
PO-DEMO-20260921-0030. Confirmed further with `ALPOUT694` (network request
captured directly, same exact-match behavior), a partial keyword `ALPOUT`
(5 matching rows, including `PO-DEMO-20260921-0006` — an Order far outside
Page 1 of the 40-row unfiltered list, proving pagination interaction is
correct: search reaches every page server-side, never only the current
Page's already-rendered rows), a Portal管理番号 search
(`PO-DEMO-20260921-0030`), and Clear (empty + Enter → URL loses
`orderNoKeyword`, list returns to all 40 rows).

**What actually produced the original misleading screenshot(s)**: the
ChatGPT Visual Re-Review's B05 screenshot was captured after typing the
keyword but *without* pressing Enter or blurring the field — exactly the one
interaction this field's own documented design defers a request until. A
second capture (C04, from the same walkthrough round) used click coordinates
computed for a different browser window size than was actually active at
that moment (a recorded, pre-existing Claude-in-Chrome window-resize
inconsistency in this environment), landing off the actual input. Neither is
an application defect.

## 6. Finding B Fix

None. No file changed for this Finding — confirmed via `git status --short
backend/` returning empty before this task's commit. Per the task's own
instruction ("明確なSearch/Paginationバグなら修正する") a fix is only
warranted when one is found; none was.

## 7. Finding B Browser Verification

See §5 above (root-cause verification and browser verification are the same
live session for this Finding, since verifying the existing behavior *was*
the fix-verification). Screenshots B01–B03 (see
`docs/gops-visual-re-review-final-correction-screenshots/README.md`) replace
the earlier, uninstrumented Visual Walkthrough captures with the same 3
scenarios captured correctly (Enter pressed, correct window/coordinates).

## 8. Regression Results

- Backend: no files changed. Ran `SupplierResponseServiceIntegrationTest`
  (24), `SkuRestockExpectationServiceIntegrationTest` (15, covers 欠品情報
  として登録's own endpoint, reused unmodified by Finding A's Card layout),
  `OrderHistoryServiceIntegrationTest` (14) as a sanity check anyway — 53/53
  passed.
- Frontend: `tsc -b` clean, Vitest 53/53, `vite build` clean, ja/en i18n key
  parity clean (no i18n keys added or removed — Finding A's Card reuses every
  existing `supplierResponse:table.*`/`differences.*` key verbatim).
- Targeted E2E (45/45 passed): `order-history-number-model.spec.ts` (7,
  including the new OH-5), `mobile-responsive.spec.ts` (14, including the
  extended M8), `manufacturer-stockout-information.spec.ts` (7, including
  欠品情報として登録's own Desktop/Mobile scenarios),
  `supplier-response-revision-workflow.spec.ts` (7),
  `supplier-response-confirmation-bugfix.spec.ts` (5),
  `supplier-response-confirm-dialog-stability.spec.ts` (6). No existing
  assertion was weakened or removed.

## 9. Remaining Issues

None found at P0/P1/P2/P3 during this correction's own verification pass. The
2 minor P3 observations recorded during the prior Findings Fix round's
screenshot capture (Order History search box not visibly filtering — now
conclusively explained as a capture artifact, not a defect, per §5 above; and
the Draft edit table's own horizontal scroll at narrow widths, still out of
scope for any of the 7+2 Findings addressed across both rounds) remain
unchanged and unfixed, as neither was in this task's scope.
