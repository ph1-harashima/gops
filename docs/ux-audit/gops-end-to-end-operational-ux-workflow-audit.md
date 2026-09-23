# G-OPS End-to-End Operational UX / Workflow Audit

**Status: AUDIT / DESIGN ONLY.** No code changed, no deploy, no Production/UAT connection, no Legacy G-SYS change, no write to any Official PO directory, no Production Snapshot data change as part of this document's production.

**Author**: Claude Code (SEPG), per Techlead (ChatGPT) instruction relayed by the user.
**Date**: 2026-09-23.
**Inputs**: (1) the user's own hands-on findings from browsing the Production Snapshot environment ("A"–"I" below), (2) direct READ ONLY source investigation of `phasep-gulliver/` (Legacy) and `backend/`/`frontend/` (G-OPS Portal), (3) the existing `docs/` corpus (business rules, prior Stage docs, prior UX audits).

---

## 0. Evidence Labels (used throughout this document)

| Label | Meaning |
|---|---|
| **CONFIRMED** | Verified against real Legacy source code, real data, or a Gulliver-confirmed business rule document (`docs/gulliver-20260917-confirmed-business-rules.md` etc.). |
| **EXISTING G-OPS DESIGN** | What G-OPS currently implements, verified by reading the actual source (not assumed). |
| **USER ACCEPTANCE FINDING** | A UX/business problem the user personally found while operating the Production Snapshot environment. Treated as real evidence in its own right, not speculation — but not automatically identical to a source-level defect (see §11 for a case where the two diverge). |
| **PROPOSED TO-BE** | This audit's own improvement proposal. Not yet implemented, not yet Gulliver-confirmed. |
| **GULLIVER CONFIRMATION REQUIRED** | A real business fact that neither source code nor existing data can settle by itself. Must go to the customer/Gulliver, not be guessed. |

No finding in this document is CONFIRMED unless a specific file:line, doc citation, or measured data point backs it.

---

## 1. Baseline — What This Audit Does Not Re-Litigate

**EXISTING G-OPS DESIGN / CONFIRMED**, restated from this project's own prior Stage work so this document is self-contained:

- Dashboard Read Model (per-Brand + Overall aggregate, single-flight refresh, 30-min cadence, `:07`/`:37` offset) is **FROZEN** (`docs/real-data-audit/gops-stage5k-freeze.md`). Null-`brand_cd` handling is CLOSED (`docs/real-data-audit/gops-stage5kr-null-brand-remediation-and-final-verification.md`). This audit does not propose changing Read Model semantics.
- RC-D (FormulaParser `[AT]`/`[AJ]`) is CLOSED (`docs/real-data-audit/gops-stage5j-production-batch-schedule-addendum.md`).
- Warehouse Stock's two-step query redesign is shipped; its unfiltered case still runs ~6s against Production scale (target ≤2s not met, attributed to an unindexed ~567K-row join with no available Legacy index) — `docs/real-data-audit/gops-warehouse-stock-production-scale-remediation.md` §10. This audit treats that as a known, already-decided-not-to-fix-further item (see §24), not something to re-open.
- `RecommendedQtySkuIdsQuery.sql` full variant (list ~7.35s / count ~5.51s) = REMEDIATION REQUIRED, not yet fixed (same doc). `PriceCandidateListQuery.sql` (~0.80s) = SAFE.
- PortalOrder state machine: `DRAFT → PENDING_APPROVAL → APPROVED → SENT → AWAITING_SUPPLIER → SUPPLIER_CONFIRMED → AGREED`, single-stage approval only, Reopen from AGREED back to SUPPLIER_CONFIRMED, Revision-on-resend model where a Revision snapshot only fixes at the moment of actual Send (`docs/role-approval-implementation.md`, `docs/supplier-response-revision-workflow.md`). `COMPLETED` is a dead CHECK-constraint value, never written. This audit does not propose a new state machine from scratch — see §14 for how it evaluates extending it.
- Context Preservation (`returnTo`/`listReturnTo`) already covers every Detail/Edit screen except Master Maintenance's dialog-based screens (which have no separate route) — `docs/gops-information-architecture-cross-screen-audit.md` §9 explicitly closes this: "今回の監査で新たなContext Preservationの欠落は発見しなかった." This audit's own findings in §21 build on top of this, they do not contradict it.
- Manufacturer Stockout Status (STOCKOUT/LONG_TERM_STOCKOUT/RESOLVED/NULL, user-entered) is deliberately kept structurally separate from Legacy 廃番/`itemStatus` — an intentional, explicit non-decision (`docs/gops-manufacturer-stockout-information-management.md` §2 line 128), not an oversight this audit needs to fix.

**User's own hands-on findings (given as input to this audit, restated for traceability)**:
- A. Dashboard Brand Breakdown shows every Brand, unsearchable, at real scale.
- B. Candidate Brand List has no search and shows Brands with zero candidates as ordinary clickable rows.
- C. 欠品/長期欠品/廃番/在庫判定/入荷予定 feel like one confusing status story to an operator, when they are actually multiple different things.
- D. No terminology help exists for a first-time operator.
- E. Expected arrival dates can display already in the past with no indication they are stale.
- F. SKU Detail's Order History is an undifferentiated flat list once a SKU has years of history.
- G. Selecting multiple Candidates, then opening one SKU's Detail, loses the selection on return.
- H. A Portal Order/Draft is created the moment the Candidate List's "create draft" action fires, not when the user clicks "ドラフトを保存" on the Draft screen itself — and nothing ever deletes an abandoned one.
- I. Dashboard reads like a KPI wall, not a sequence of what to do next; the "要確認" tile in particular felt like it led to a screen that hid the very records it was counting.

---

## 2. Dashboard Brand Breakdown — Search & Scale

**EXISTING G-OPS DESIGN**: `DashboardPage.tsx:196-257` renders one row per Brand from `useDashboard()`. No search/filter box exists. Backend (`DashboardRefreshService.publishSuccess`, `DashboardStockAggregateQuery.sql`) builds the Brand set as the union of every Brand code with a Legacy `ms_item` row (`WHERE del_flg IS NULL OR del_flg=0`) and every Brand with a candidate — i.e. **every Brand in the Legacy Master**, not "Brands with an actionable candidate." This is the direct mechanism behind Finding A.

**USER ACCEPTANCE FINDING**: confirmed real — at Production scale this is an unbounded, unsearchable table.

**PROPOSED TO-BE**:
- Add a client-side keyword filter over the already-fetched `data.brands` array (no new backend query — the Read Model already returns the full Brand set in one call; this is filtering already-loaded data, not adding a new search endpoint). This is cheap because the Read Model is a fixed-size once-per-refresh payload, not a live per-keystroke Legacy query.
- Default-collapse Brands where every numeric column is 0 (no candidate, no stockout, no draft, no attention, no price-change) behind a "0件のBrandを表示" toggle, defaulting to hidden. This directly also improves §3's zero-candidate issue since the two screens share the same data source.
- Do not add server-side pagination to this screen — the Read Model's entire point (Stage 5J/5K) was to make this a single bounded pre-aggregated payload; paginating it server-side would reintroduce exactly the kind of per-request Legacy cost the Read Model was built to eliminate. **PROPOSED TO-BE**, not GULLIVER CONFIRMATION REQUIRED — this is a pure engineering call within already-frozen architecture.

---

## 3. Candidate Brand List — Search Absence & Zero-Candidate Display

**EXISTING G-OPS DESIGN**: `OrderCandidateBrandListPage.tsx` explicitly reuses the same `useDashboard()` query as §2 (comment at line 31: "Reuses Dashboard's own `useDashboard()` query as-is"). Same data, same no-search UI, both desktop table (line 128) and mobile cards (line 92). A Brand with `candidateCount === 0` renders as an ordinary row with "0" in every cell, each cell still a clickable Button navigating to an empty-result screen (e.g. `/candidates?brandCode=X&recommendedOnly=true`).

**USER ACCEPTANCE FINDING**: confirmed real (Finding B) — this is not a hypothesis, it is a direct read of the render path.

**PROPOSED TO-BE**: apply the same keyword filter + "0件のBrandを非表示（デフォルト）" toggle from §2 here too, since both screens already share the underlying data. For the specific "0発注候補" cell, disable/gray it (not clickable) when `candidateCount === 0`, while leaving the other numeric columns (欠品 etc.) clickable even at 0 only if that specific metric is non-zero — i.e. gate clickability per-cell on that cell's own count, not on the whole row.

---

## 4. SKU Status Semantics — Three Independently-Sourced Axes

**EXISTING G-OPS DESIGN**, confirmed by source across three separate subsystems:

| Axis | Source | Values | File |
|---|---|---|---|
| **Item lifecycle status** (Legacy) | `MS_ITEM.DISCON` (bool) + `MS_ITEM.ITEM_STATUS` (free text, often NULL/blank even when discontinued) | 通常/廃番/廃番在庫あり/発注停止/掲載保留 | `ItemStatusChip.tsx:4-32` |
| **Stock judgement** (G-OPS-computed proxy) | `ms_stk` physical-warehouse sum + open-PO qty | 通常/欠品/長期欠品 | `DashboardService.java:34-39`, `StockJudgementChip.tsx` |
| **Supply status** (Supplier's own self-report) | Manufacturer input during Supplier Response | 供給可能/欠品/長期欠品/廃番/入荷待ち/不明 | `SupplierResponseDetail.java`, `status.json` `supplyStatus.*` |

**CONFIRMED (Legacy source, cross-checked against `docs/gops-manufacturer-stockout-information-management.md` §2)**: 欠品 = `current_stock == 0` over `WH_CD IN (4,5,6,7,8,10,11,12,15)`. **長期欠品 is NOT duration-based** despite its name — it is `current_stock == 0 AND open_po == 0`, evaluated instantaneously every refresh, with no elapsed-days threshold anywhere in `DashboardStockAggregateQuery.sql` or `DashboardService.java`. This is a genuine naming/semantics mismatch: an operator reading "長期" (long-term) reasonably infers a duration threshold exists. It does not.

`stockJudgement`'s own Javadoc self-labels this a `[PROTOTYPE DECISION]` / `[TBD — CUSTOMER REVIEW]`, unconfirmed by Gulliver (`DashboardService.java:34-39`).

The one place any two of these three axes are cross-checked at all is `SkuRestockExpectationService.merge()` (`hasConflict` flag when Legacy has an arrival date but the manufacturer self-reports STOCKOUT/LONG_TERM) — everywhere else, all three vocabularies render side-by-side with no reconciliation logic.

**USER ACCEPTANCE FINDING**: confirmed real (Finding C) — a first-time operator has no way to know these are three different systems using overlapping Japanese words.

**GULLIVER CONFIRMATION REQUIRED**:
1. Is "長期欠品" actually meant to be duration-based (e.g. "N consecutive days at zero stock with no open PO"), and the current instantaneous formula is a stand-in that was never revisited, or is the customer's actual mental model already "no stock + nothing incoming" regardless of duration? This determines whether §4's naming mismatch is a UX-copy fix or a computation fix.
2. When Legacy 廃番/`ITEM_STATUS` and the Supplier's self-reported 廃番 (via Supply Status) disagree, which one should an operator trust, and should G-OPS surface the conflict the way `hasConflict` already does for arrival dates?

**PROPOSED TO-BE**:
- Rename the on-screen Japanese label for `LONG_TERM_OUT_OF_STOCK` to something that does not imply elapsed time (e.g. "欠品・入荷予定なし") until/unless Gulliver confirms a real duration rule — a copy-only change, reversible, no computation change (respects "Dashboard Read Model semantic change" prohibition since it changes display text, not the underlying value or its meaning).
- Add a short inline legend (see §5) making the three-axis split explicit rather than implicit.
- Do not attempt to merge/reconcile the three axes computationally in this Stage — that is exactly the kind of ad-hoc single-screen fix the audit's constraints prohibit; reconciliation, if wanted, is its own future-phase decision gated on GULLIVER CONFIRMATION REQUIRED item 2 above.

---

## 5. Status Terminology / Help UX

**EXISTING G-OPS DESIGN**: `StockJudgementChip.tsx:25-27` already wraps every instance in a `Tooltip` reading (from `status.json`): "現在の暫定判定です。商品状態（通常/廃番/発注停止等）とは別の軸で、在庫数・発注残から算出しています。正式な判定条件はGulliver社確認後に確定します。" — this tooltip **already discloses** that it is a separate, provisional axis. `ItemStatusChip.tsx` has **no tooltip at all**. No dedicated glossary component exists anywhere in the frontend (confirmed absent by search).

**USER ACCEPTANCE FINDING**: confirmed real in spirit (Finding D) — the existing tooltip is real but only covers one of the three axes in §4, and a Tooltip that only appears on hover is not discoverable by a first-time operator who does not know to hover.

**PROPOSED TO-BE**:
- Add the same style of Tooltip to `ItemStatusChip.tsx` (currently the only one of the three chips with none).
- Add a single, static "ステータスの見方" help panel (a `<Popover>`/link near the Dashboard/Candidate List header, not per-cell) that lists all three axes from §4 side-by-side in one place, rather than relying on three separate hover tooltips a user must discover independently. This is additive UI, not a semantic change to any existing value.

---

## 6. Expected Arrival / Restock Date Staleness

**EXISTING G-OPS DESIGN**: `SkuRestockExpectationService.merge()` (`backend/.../SkuRestockExpectationService.java:203-213`) — whenever a Legacy expected-arrival date exists, it is returned as `SOURCE_LEGACY` and always wins display priority over any Manual (manufacturer-entered) date. **No code path anywhere checks whether that Legacy date has already passed.** It renders identically to a genuine future date on Candidate List, SKU Detail, and Order History Detail (`OrderHistoryService.toLineView`, `OrderHistoryService.java:368-373`, passes it through unmodified).

**USER ACCEPTANCE FINDING**: confirmed real (Finding E), and directly explained by the above — this is not a rendering bug, it is a genuine absence of any staleness check anywhere in the pipeline.

**PROPOSED TO-BE**:
- Compute staleness purely at render time (`legacyDate < today`), no new data source, no Read Model change: display an "経過" (overdue) visual marker (e.g. a warning-colored chip or strikethrough-adjacent icon) next to a past-due Legacy arrival date, on all three screens that already render it.
- Do NOT auto-hide or auto-fallback-to-Manual-date when the Legacy date is stale — that would be inventing a business rule about which source of truth wins, which is exactly what GULLIVER CONFIRMATION REQUIRED exists to prevent. Only the visual "this looks overdue" signal is proposed; the underlying `SOURCE_LEGACY`-wins priority logic in `merge()` is untouched.

**GULLIVER CONFIRMATION REQUIRED**: when a Legacy arrival date passes without the shipment actually arriving (per `ms_stk`), should the system auto-fall-back to showing "不明" or the Manual date instead, or is a human meant to notice and manually intervene? Current behavior (silently keep showing the stale date) is almost certainly wrong in spirit, but the *correct* replacement rule needs the customer's answer, not an invented one.

---

## 7. SKU Detail — Order History (Yearly Accordion Proposal)

**EXISTING G-OPS DESIGN**: `SkuDetailPage.tsx:478-495` renders a flat, non-paginated table (PO番号/発注日/仕入先/数量/単価/ステータス). Backing query `SkuPoHistoryReadQuery.sql` has **no `LIMIT`, no date filter, no pagination param** — every historical PO line for that SKU, across every year, is fetched eagerly on every SKU Detail load.

**USER ACCEPTANCE FINDING**: confirmed real (Finding F) for any SKU with multi-year history.

**PROPOSED TO-BE**: group rows by 発注年 (order year, extracted from `ordr_date`) into a `<Accordion>` per year, most recent year expanded by default, older years collapsed. This is a **pure frontend rendering change** — the same already-fetched flat array is grouped client-side; it requires no new backend endpoint, no pagination protocol, no change to `SkuPoHistoryReadQuery.sql`. If SKU history volume ever grows enough that eager full-fetch itself becomes the bottleneck (not just the on-screen list), that would be a separate, later performance decision (see §23), not bundled into this UX-only proposal.

---

## 8. Candidate Selection Persistence Across Navigation

**EXISTING G-OPS DESIGN**: selected checkboxes live in local component state, `useState<Set<string>>` (`CandidateListPage.tsx:99`), plus a single-Supplier constraint (`selectedSupplierCode`, line 109). This state is not written to the URL, sessionStorage, or React Query cache. `withReturnTo`/`listReturnTo` preserve the list's **filter/page/sort** URL state across a round trip to SKU Detail, but `CandidateListPage` unmounts on navigating away, so `selected` resets to empty on return.

**USER ACCEPTANCE FINDING**: confirmed real (Finding G) — filter context survives, selection does not, which is an inconsistent user experience (the two kinds of state are stored in fundamentally different places for no visible reason to the user).

**PROPOSED TO-BE**: persist `selected` (the Set of SKU strings) the same way filter state already persists — via the URL (`?selected=SKU1,SKU2,...`) is the simplest option consistent with this codebase's existing "URL is the single source of truth" pattern (already used for filters here and on Order History, per `docs/production-ux-workflow-redesign.md` §6.2). Cap the encoded list length defensively (e.g. warn/truncate past some reasonable count) since URL length has practical limits; a `sessionStorage`-backed alternative is a fallback if the customer's realistic max-selection count makes URL encoding awkward — that sizing question is worth a quick sanity check against real usage patterns before committing to one approach, not a Gulliver question.

---

## 9. Draft / PortalOrder Lifecycle

**EXISTING G-OPS DESIGN**: `OrderDraftService.createDraft()` is the **only** creation path — there is no backend concept of "opened but not yet saved." `CandidateListPage`'s draft-creation mutation persists the `PortalOrder` row **immediately** when the user clicks the create-draft action on the Candidate List (before `OrderDraftPage` ever renders), then navigates to `/orders/drafts/{id}`. The Draft screen's own "ドラフトを保存" button only PATCHes fields (qty/date/remark) on the already-existing row — it creates nothing. **No delete endpoint exists** for `PortalOrder` (confirmed: only `PriceChangeSetController` has a `@DeleteMapping`, unrelated). **No scheduled cleanup job exists** (confirmed: only `SchedulingConfig`/`DashboardRefreshScheduler` use `@Scheduled`). An abandoned draft is permanent.

**USER ACCEPTANCE FINDING**: confirmed real (Finding H) and precisely characterized by source — the button's label ("save") actively misleads the user about when persistence actually happened.

**PROPOSED TO-BE** — three options, compared honestly:

| Option | Description | Correctness | Auditability | Crash recovery | UX | Implementation impact |
|---|---|---|---|---|---|---|
| **A. Keep current (create-on-open)** | No change | Simple, no race | Full audit trail from the moment of intent | Best — nothing is ever lost mid-edit | Misleading button label only | None |
| **B. True create-on-explicit-save** | Hold selections in frontend state only; POST creates the order on first real "save" | Matches the button's label | No record of abandoned attempts (arguably a feature, not a bug) | Worse — closing the tab before the first save loses all work | Matches user's mental model of "save" | Backend + frontend rework of draft creation entirely |
| **C. Keep create-on-open, relabel + add deletion** | No change to creation timing; rename the button (e.g. "変更を保存"); add an explicit delete/discard action + a cleanup policy for old untouched drafts | Same as A | Same as A, plus deletions are themselves audited | Same as A | Fixes the actual complaint (label mismatch) without changing when data is safely persisted | Smallest: one label string + one DELETE endpoint + one scheduled job |

**Recommendation: Option C.** It fixes the literal user complaint (the word "save" implying nothing was saved before) with the least architectural disruption, and preserves the crash-recovery property that create-on-open already gives for free — B trades that away for a label that could instead just be changed.

**Deletion/cleanup policy proposal** (part of Option C): add `DELETE /api/orders/drafts/{id}` restricted to `status = DRAFT` orders with zero recorded audit-timeline events beyond creation (to avoid deleting something someone already started acting on), plus a scheduled job flagging (not auto-deleting) DRAFT orders untouched for N days for manual admin review — auto-deletion of business records without a human decision is a stronger claim than this audit should make unilaterally; N and "auto-delete vs. flag-only" is a candidate for a quick customer sanity check, not a hard Gulliver blocker.

---

## 10. Dashboard as a Workflow Sequence, Not a KPI Wall

**EXISTING G-OPS DESIGN**: `DashboardPage.tsx` renders Overall KPI tiles then the per-Brand table, both ordered by their definition order in `DashboardResponse`, which itself roughly follows `DashboardService`'s field-declaration order — i.e. the order is an implementation artifact, not a designed sequence.

**USER ACCEPTANCE FINDING**: confirmed real (Finding I, first half) — a KPI wall doesn't tell an operator "what to do next."

**PROPOSED TO-BE**: reorder the *existing* KPI tiles (no new KPI, no new query, no Read Model change) to follow the real operational sequence implied by the PortalOrder state machine already frozen in §1:

1. 発注候補 (something needs a decision) → 欠品/長期欠品 (why it needs one)
2. 発注作成中 (DRAFT — needs to be finished and submitted)
3. 承認待ち (PENDING_APPROVAL — needs an Approver)
4. メーカー回答待ち (AWAITING_SUPPLIER — waiting on someone else)
5. 要確認 (something needs attention right now, cross-cutting any status)
6. 問い合わせ中 / 価格変更（下書き）(secondary/parallel tracks)

This is a pure reordering + section-grouping (with subheadings like "対応が必要" / "進行中" / "その他") of tiles that already exist, using data that already exists — zero backend change.

---

## 11. Dashboard KPI → Destination Navigation Contract (Full Matrix, Including the Suspected 要確認 Bug)

**EXISTING G-OPS DESIGN**, full matrix verified against `DashboardPage.tsx:97-130`, `OrderHistoryListPage.tsx`, and `OrderHistoryService.java`:

| KPI | Navigates to | Destination default filter | Consistent? |
|---|---|---|---|
| 発注候補 | `/candidates` | none | ✅ |
| 欠品 | `/candidates?outOfStockOnly=true` | matches | ✅ |
| 長期欠品 | `/candidates?longTermOutOfStockOnly=true` | matches | ✅ |
| 発注作成中 | `/orders/history?status=DRAFT` | matches | ✅ |
| 承認待ち | `/orders/history?status=PENDING_APPROVAL` | matches | ✅ |
| メーカー回答待ち | `/orders/history?status=AWAITING_SUPPLIER` | matches | ✅ |
| **要確認** | `/orders/history?hasAttention=true` | **no `status` param** — see below | ⚠️ see below |
| 問い合わせ中 | `/orders/history` | no filter at all | ⚠️ see below |
| 価格変更（下書き） | `/price-changes?status=DRAFT` | matches | ✅ |

**Direct source verification performed for this audit** (not just the research fork's report — independently re-read both files):
- `DashboardPage.tsx`'s 要確認 tile navigates with `navigate('/orders/history?hasAttention=true')` — a fresh `navigate()` call that replaces the entire URL/search string, not a merge onto whatever was there before.
- `OrderHistoryListPage.tsx:83-91,126` reads `status` and `hasAttention` fresh from `useSearchParams()` on every render — there is no client-side default status value applied when `status` is absent from the URL; the `<TextField select>` shows `t('filter.all')` when `filter.status` is `undefined`.
- `OrderHistoryService.buildSpecification` (`OrderHistoryService.java:205-220`) only adds a status predicate `if (status != null && !status.isBlank())` — with `status` absent, no status restriction is applied server-side either.
- `attentionCount` on the Dashboard (`DashboardService.java:95-102`) and `hasAttentionOnly` on Order History (`OrderHistoryService.java:212-220`) both derive from the exact same `OrderAttention.active = true` definition — no divergent "active" semantics between the two screens.

**Conclusion**: per the source as it exists today, clicking 要確認 should show every order (any status) with an active attention — i.e. it should **not** hide the records it counts. This is a genuine discrepancy between Finding I (USER ACCEPTANCE FINDING) and what static source analysis shows (EXISTING G-OPS DESIGN, verified). Per this audit's own evidence discipline, the User Acceptance Finding is not downgraded or dismissed — it is recorded as real, observed behavior — but it is **not** relabeled CONFIRMED-as-a-source-level-bug, because the source itself does not reproduce it under this analysis.

Three non-mutually-exclusive explanations worth checking, none of them confirmed here:
1. The observation predates the Phase 8-J backend-pushdown rewrite (comment in the source itself notes `hasAttentionOnly` used to be a client-side filter applied only after full-table fetch, before that Phase) and is now stale.
2. The real repro is the **問い合わせ中** tile, not 要確認 — that one genuinely navigates to `/orders/history` with **no filter of any kind**, which would visually mix in unrelated rows and could easily be misremembered as "要確認 hid things" versus "問い合わせ中 showed everything, unfiltered, which looked wrong."
3. A build/deploy skew on the Snapshot environment at the time of the finding (an older frontend bundle actually did have a default status filter that has since been fixed) — cannot be ruled out without a timestamped build hash, which is out of scope to chase further here.

**PROPOSED TO-BE**:
- Re-run this specific click-through live against the current Snapshot build as part of the persona walkthrough (§20) before treating it as closed either way.
- Regardless of the outcome, add an explicit frontend test (`OrderHistoryListPage` + `Dashboard` integration test) asserting that navigating with only `hasAttention=true` returns/renders orders of every status — this locks in the currently-correct behavior against regression, independent of whether the original observation was stale.
- Fix 問い合わせ中's destination to carry a real filter once one exists for it (tracked as its own known, pre-existing gap — the code comment already admits "no dedicated Order List Filter exists" for this KPI; this audit does not invent one, since 問い合わせ (follow-up case) filtering criteria is itself a candidate GULLIVER CONFIRMATION REQUIRED item if a dedicated filter is wanted).

---

## 12. Official PO — Legacy Reality (Highest-Priority Investigation Item)

This section is sourced from direct READ ONLY investigation of `phasep-gulliver/gulliver/src/main/java/jp/ne/glv/batch/PrOfficialPoImportBatch.java`, `AbstImportBatch.java`, `Const.java`, `BusinessLogicUtil.java`, cross-checked against `docs/gulliver-20260917-confirmed-business-rules.md`.

### 12.1 File format and import mechanics — **CONFIRMED (Legacy source)**

- The Legacy batch reads **Excel only** (`.xlsx`, via `XSSFWorkbook` — the constant name `FILE_FORMAT_XLS` is misleading). There is **no PDF handling anywhere** in `PrOfficialPoImportBatch` or its framework base. (`PrOfficialPoImportBatch.java:258,269`)
- Import is a **full replace, not a merge**: on every import, existing `TR_PO`/`TR_PO_DTL`/`TR_INV`/`TR_INV_DTL` rows for that PO No. are deleted and fully recreated from the current cell contents of the file. (`:884-922,933-1057`)
- A `DELETE` keyword in a specific cell deletes the whole PO (blocked if an invoice already exists for it) or, placed at an invoice-number row, deletes just that invoice. (`:382-399,750-760`)
- The sheet's shape is validated by **fixed cell coordinates** (must contain "PURCHASE", "ORDER TO", "PURCHASER"/"No", and specific column headers), not by filename — a mismatch causes outright rejection, and the file is moved to the upload directory prefixed `error_`. (`:268-341`)
- The same Excel file is a **living document across the PO's lifetime**: columns from index 17 rightward hold per-invoice arrival-quantity tracking, meaning the same PO's file is expected to be re-uploaded multiple times as shipments/invoices accrue, not once. (`:704-824,864-882`)

### 12.2 Directory / scheduling mechanism — **CONFIRMED (Legacy source, generic framework)**

- Configuration lives in `MS_COMM` keyed `(CATE_ID='FILE_IMP', CODE_ID='OFFIC_PO')`: `VAL1` = the absolute upload-folder path, `VAL2` = an optional filename-prefix filter, `VAL8='STUCK_CHECK'` enables a stuck-file alert. Subfolders `work/` (in-flight, renamed `{processId}_{original}`) and `backup/` (success, renamed `{original}_{processId}.{ext}`) are auto-created under `VAL1`. Files prefixed `error`/`recycle`, or named `Thumbs.db`/`.DS_Store`, are skipped/re-emitted. (`AbstImportBatch.java:547-649,803-846`)
- This is a **filesystem-polling batch**, not a UI upload — there is no HTTP endpoint in this class; the actual cron schedule is external to this code (already established separately in the Stage 5J batch-schedule investigation, not re-derived here).
- **No `MS_COMM` seed row with a real configured `VAL1` value for `OFFIC_PO` exists anywhere in this repository** — the actual Production directory path is not discoverable from source alone.

### 12.3 PO number format — **CONFIRMED (Legacy source) vs. GULLIVER CONFIRMATION REQUIRED (discrepancy)**

Actual running Legacy parsing logic (`BusinessLogicUtil.java:1028-1069`):
- `getSupplierCd(poNo)` = `poNo.substring(0,4)` → a **4-character** supplier code.
- `getBrandCd(poNo)` = `poNo.substring(5,8)` → a 3-character brand code (index 4 implies a 1-character separator between supplier and brand).
- `getIdCd(poNo)` = `poNo.substring(9,11)` → a 2-character serial segment (index 8 implies another separator).
- Real shape: **`SSSS-BBB-NN...`** (dash-separated, 4+3+2+), max length 30.

Customer-confirmed rule (`docs/gulliver-20260917-confirmed-business-rules.md` BR-08, 2026-09-17): format `{Supplier略称3文字}{Brand略称3文字}{3桁通番}`, e.g. `ABCXYZ001` — **3+3+3, no separator**.

**These two do not match.** This is a real discrepancy this audit surfaces, not resolves — it cannot be settled from source or docs alone. **GULLIVER CONFIRMATION REQUIRED**: is BR-08 describing a *new* numbering rule to apply only to future G-OPS-generated Official PO numbers (coexisting with old-format historical POs already in Legacy), or was BR-08 stated imprecisely by the customer and the real intended format is the dash-separated one Legacy already parses? This single question gates the correctness of §15's directory-detection design and §16's number-format audit.

### 12.4 Existing docs' confirmed Excel→PDF→signature flow — **CONFIRMED (doc)**

`docs/gulliver-20260917-confirmed-business-rules.md` BR-01 (customer-confirmed 2026-09-17): flow is 発注内容確定 → **Excel生成 → 同内容PDF化** → 社内承認 → メーカー送信（**PDF + Excel 両方**）. Excel is the source document; PDF is the formal confirmation copy sent alongside it so the supplier can still edit/reply on the Excel (qty, model numbers, etc.). Both must agree on PO No/Supplier/Brand/SKU/Qty/Price/Delivery.

This is consistent with §12.1's finding that Legacy only *ingests* Excel back in — the PDF is generated for the human-facing send/signature step and is never itself parsed back by the Import batch. **No source or doc anywhere describes the actual mechanics of the "signature" step** (who edits, whether it is a literal ink/digital signature, on which of the two files, before or after the file lands in the Import folder).

### 12.5 What no source or doc confirms — **GULLIVER CONFIRMATION REQUIRED**

1. The PO-number-format discrepancy (§12.3).
2. The real human signature process — is there an actual signature at all, on which artifact, at what point relative to the Legacy Import Folder?
3. The real Production `VAL1` directory path and its polling schedule.
4. The real convention by which whatever watches the `backup/`/`error/` folders decides a file "succeeded" — `OfficialPoFileNaming.buildFileName()` (`OfficialPO_{Supplier}_{Brand}_{yyyyMMdd}_{PONo}_{Revision}.{ext}`) is explicitly self-labeled in its own Javadoc as a **working assumption, not a confirmed requirement**.
5. Whether a formal supplier-send/acknowledgement step should exist as a fourth state axis (see §14) — none of the three existing G-OPS axes model it.

### 12.6 Sample files found

Real generated **G-OPS demo** output exists at `backend/data/official-po/demo/order-{orderId}-rev{N}-{timestamp}.xlsx` (248 files) with matching PDFs under `.../demo/pdf/`. These are G-OPS's own generated samples, not genuine pre-G-OPS Legacy-originated samples — **no real customer-originated Official PO Excel/PDF sample exists anywhere in this repository** (searched by extension and by filename keyword across the whole tree, excluding build artifacts). Note in passing: the on-disk demo filenames (`order-1219-rev1-...`) do not match `OfficialPoFileNaming`'s documented pattern — they come from a different/older generation path. Worth a footnote, not chased further (out of this audit's scope to fix).

---

## 13. Official PO — Proposed To-Be Workflow

**PROPOSED TO-BE**, built from §12's confirmed facts, not invented from scratch:

1. Admin finalizes an Order (existing `APPROVED` state — no change).
2. G-OPS generates Excel (existing `OfficialPoIntegrationRequest.pdfFileKey`-adjacent generation path — already implemented per §12.6's sample evidence) **and** PDF from the same data, per BR-01's confirmed Excel→PDF order.
3. **[GULLIVER CONFIRMATION REQUIRED — §12.5 item 2]** Human signature/internal-procedure step happens here, on one or both artifacts, outside G-OPS's direct control today.
4. Admin sends both PDF + Excel to the supplier (per BR-01) — this is the existing "Supplier Send" screen (§18), which today does the send but does not yet gate on or record "was this actually signed off."
5. The Excel (not the PDF) is separately placed into the Legacy Import Folder for `PrOfficialPoImportBatch` to ingest (per §12.1/§12.2) — this is a **distinct** action from step 4's supplier-send, confirmed by source: the batch never touches a PDF and has no knowledge of whether a supplier-send happened.

This sequence is not new invention — it is the existing confirmed facts (BR-01's step order, the batch's Excel-only ingestion, the existing Send screen) laid out end-to-end for the first time in one place. The only genuinely new proposal is making step 3's existence explicit in the system (see §14) rather than leaving it as an invisible offline gap between "Admin approved" and "file lands in the Import Folder."

---

## 14. State-Model Evaluation: Admin-Approved vs. Signed/Internal-Procedure-Completed vs. Supplier-Sendable

**EXISTING G-OPS DESIGN** (`OfficialPoIntegrationRequest.java`, confirmed): three independent axes already exist, one row per `(portalOrderId, revisionNo)`:

1. **Integration Status**: `PENDING → GENERATED → SUBMITTED → CONFIRMED`, or `→ FAILED`. `SUBMITTED` means only "bytes were written to the configured Import Folder adapter" — explicitly **not** "Legacy confirmed receipt." `CONFIRMED` has no real production caller today (only unit tests exercise it; the G-SYS Import Confirmation polling from Phase 9-C is not implemented).
2. **Lifecycle Status**: `ACTIVE → SUPERSEDED` (Reissue) or `ACTIVE → CANCEL_REQUESTED → CANCELLED` (two-step Cancel per BR-03).
3. **PDF artifact fields** (`pdfFileKey`, `pdfGeneratedAt`): explicitly documented as never driving the Integration Status state machine.

**Gap, confirmed by source**: none of these three axes represent "was this actually sent to and acknowledged by the supplier" — `SUBMITTED` is purely the internal Legacy hand-off. The user's "Admin Approved vs. Signed/Internal-Procedure-Completed vs. Supplier-Sendable" framing does not map onto any single existing field.

**PROPOSED TO-BE — reuse over invention**: rather than adding a fourth full parallel status enum, extend the existing **Integration Status** axis's *meaning* at the `PENDING` stage only: split it into `PENDING_SIGNATURE` (Admin-approved, awaiting the offline sign-off step from §13 step 3) and `PENDING_SUBMISSION` (signed, ready for the Excel to go to the Import Folder), both preceding `GENERATED`. This is additive to an already-existing enum, not a new independent axis, and keeps the existing `SUBMITTED`/`CONFIRMED`/`FAILED` semantics completely untouched. Whether this granularity is actually wanted, versus leaving the sign-off step entirely as an offline human process G-OPS doesn't track, is itself a genuine product decision — flagged as a Decision Matrix item (§22, item covering this) rather than assumed here.

**GULLIVER CONFIRMATION REQUIRED**: does the customer actually want G-OPS to track the signature step at all, or is it intentionally kept outside the system as a manual/paper process? Building `PENDING_SIGNATURE` tracking without this answer would be inventing a business process, which this audit's constraints explicitly forbid.

---

## 15. Official PO Directory Detection & Reconciliation Design

**PROPOSED TO-BE**, gated entirely on §12.3's PO-number-format resolution and §12.5 item 3 (real path)/item 4 (real success-detection convention):

- **Reconciliation key**: PO No. + Revision — this part is not in dispute regardless of how §12.3 resolves, since both the confirmed format and the Legacy-parsed format agree a PO No. exists and Revision is tracked separately as a resend counter.
- **Case handling matrix** (to design once §12.5 items 3–4 are answered, not before):

| Case | Detection signal | Proposed handling |
|---|---|---|
| Old-revision file re-appears | Revision number in filename/cell < current known Revision | Flag, do not re-import silently (Legacy's own DELETE-then-recreate semantics make silent re-import destructive per §12.1) |
| Duplicate PO No., same Revision | Two files resolve to identical key | Flag for manual review — do not guess which is authoritative |
| Unknown PO No. | Key not found in any `PortalOrder`/`OfficialPoIntegrationRequest` row | Flag as "not initiated from G-OPS" (may be a Legacy-native PO outside G-OPS's scope entirely) |
| Malformed filename/sheet | Fails §12.1's fixed-cell-coordinate contract | Already handled by Legacy itself (`error_` prefix + move) — no G-OPS action needed, this is Legacy's own existing safety net |
| Excel-only / PDF-only present | Only one of the two expected artifacts found | Flag — BR-01 requires both; a lone file suggests an incomplete send, not a normal state |

This table is a design skeleton, not a final spec — it cannot be finalized without §12.5's answers, and is explicitly marked GULLIVER CONFIRMATION REQUIRED at the row level for "old-revision"/"duplicate" handling specifically, since those encode a business judgment call (e.g. is an old-revision re-appearance ever legitimate, such as a supplier accidentally resending?) that this audit cannot answer from source alone.

---

## 16. Official PO Generation Template / Format / Number-Format Audit

**EXISTING G-OPS DESIGN**: 248 demo Excel+PDF pairs exist (`backend/data/official-po/demo/`), confirming generation is implemented end-to-end for at least the demo path. **CONFIRMED gap**: the on-disk demo filenames do not match `OfficialPoFileNaming.buildFileName()`'s documented pattern, and that class's own Javadoc self-labels its pattern a working assumption. The PO-number format baked into any existing template output has not been independently verified against §12.3's discrepancy in this pass (that would require opening the actual generated Excel/PDF content, which was not done in this Stage — flagged as a follow-up check, not performed here to keep this Stage's file-touching to zero beyond documentation).

**PROPOSED TO-BE**: before any further Official PO generation work, resolve §12.3, then re-audit whether the existing generation template already encodes the (possibly wrong) 3+3+3 format or the (Legacy-actual) dash-separated format — this determines whether the generation code needs a change or is already correct and only the *documentation* (BR-08) was imprecise.

---

## 17. Reissue / Reapproval & Cancellation — Integration with the Official PO Axis

**EXISTING G-OPS DESIGN, CONFIRMED**: BR-02 (Reissue requires full re-approval, reusing the existing single-stage Approval Workflow — no new approval logic) and BR-03 (Cancel is a two-step Request→Approve workflow) are both already implemented as the `Lifecycle Status` axis's `SUPERSEDED`/`CANCEL_REQUESTED → CANCELLED` transitions (§14). The Supplier Response Revision model (`docs/supplier-response-revision-workflow.md`) confirms Revision creation from `AWAITING_SUPPLIER`/`SUPPLIER_CONFIRMED` returns the order to `DRAFT`, and a new Revision snapshot only fixes at the next actual Send — this is the same mechanism BR-02 relies on, already correctly wired to the Official PO axis via `(portalOrderId, revisionNo)` composite keys on `OfficialPoIntegrationRequest`.

**No new finding here** — this section exists to explicitly confirm, for the audit's completeness, that Reissue/Cancel do NOT need new design work; they already correctly compose with the Official PO Integration axis as built. This is a "Do Not Reopen" candidate (§19).

---

## 18. Supplier Send Screen / PO Preview Audit

**EXISTING G-OPS DESIGN**: the `PoPreview` DTO shows draft/prototype/official PO numbers, supplier/brand identity, order lines, summary, and `manufacturerCommunication` (to/cc/subject/body/attachment) plus `communicationChannel` (EMAIL/EDI, set only after an actual Send) and `resolvedManufacturerChannel`. It **deliberately excludes** recommendedQty/currentStock/safetyStock (an intentional prior design decision, per the DTO's own comment). **It does not reference the Official-PO-integration-status/lifecycle axis at all** — that is only shown on Order History Detail via a separate call.

**USER ACCEPTANCE FINDING context (from §13's proposed flow)**: since this screen is where the human-facing send (step 4 of §13) happens, and §14 proposes optionally tracking a signature/internal-procedure step, this screen would be the natural place to surface that state if the GULLIVER CONFIRMATION REQUIRED question in §14 comes back "yes, track it."

**PROPOSED TO-BE**: no change to this screen's current scope until §14's Gulliver question resolves — surfacing a state that doesn't exist yet, or that the customer may not want tracked at all, would be premature. This section is deliberately a "wait" recommendation, not a redesign.

---

## 19. Supplier Master List — Search & Pagination Audit

**EXISTING G-OPS DESIGN, CONFIRMED**: `SupplierMasterService.listSuppliers(page, size)` has no keyword parameter. It fetches **all** ~602 suppliers plus all their contacts/channels/regions/shortcodes every request, performs an O(n×m) in-Java join, then paginates via `List.subList()` — **in-memory pagination over a fully re-fetched, fully re-joined list on every page request**, not a DB-level `LIMIT`/`OFFSET`. `SupplierMasterListPage.tsx` has no search field. Prior Stage work (RC-J, Stage 5H) added pagination but never search — confirmed CLOSED-for-pagination-only in `docs/real-data-audit/gops-stage5h-systematic-performance-remediation.md` §5, and Stage 5G's own "PF-E Missing Pagination" finding was explicitly deprioritized ("lower urgency since admin-only").

**PROPOSED TO-BE**: add a keyword search (supplier code/name) the same way Order History/Arrival/Warehouse Stock already do (free-text `TextField`, backend `WHERE ... LIKE`/equivalent). Given the current in-memory-join implementation, this is naturally best paired with converting the join itself to a proper DB-level filtered query rather than filtering the already-fully-materialized in-memory list — but that is a performance refactor bundled with, not a prerequisite for, the search feature; the search field could ship first as a client-side filter over the current page's data if a fast win is wanted, with the real DB-level fix following. This is a genuine engineering-sequencing choice, not a business-rule question — no Gulliver confirmation needed.

---

## 20. Cross-Screen Search / Pagination Standard Matrix

**EXISTING G-OPS DESIGN, CONFIRMED** (`docs/gops-information-architecture-cross-screen-audit.md`, this audit's own source reads):

| Screen | Search | Pagination | Filter-chip visualization |
|---|---|---|---|
| Candidate List | ✅ free-text | ✅ backend | ✅ (the only screen that has it) |
| Candidate Brand List | ❌ (§3) | N/A (fixed Read Model set) | ❌ |
| Order History | ✅ free-text (orderNo/item) + status/supplier/brand/date | ✅ backend | ❌ |
| Arrival | ✅ | ✅ backend | ❌ |
| Warehouse Stock | ✅ | ✅ backend (two-step, §1) | ❌ |
| Stock/Sales | ✅ | ✅ backend | ❌ |
| Supplier Master | ❌ (§19) | ✅ but in-memory (§19) | ❌ |
| Brand Master | not independently re-verified this Stage | not independently re-verified this Stage | ❌ |

**PROPOSED TO-BE**: the filter-chip visualization pattern already proven on Candidate List (`docs/gops-information-architecture-cross-screen-audit.md` §9's own unimplemented proposal #4) should extend to Order History/Arrival/Warehouse Stock/Stock-Sales for consistency — this is an already-identified, already-scoped, not-yet-implemented prior proposal this audit re-endorses rather than reinvents. Supplier Master's search gap (§19) and Brand Master's unverified status are the two concrete, net-new items this section adds to that prior list.

---

## 21. Cross-Screen Back / Context-Preservation Audit

**CONFIRMED, CLOSED** per §1 baseline — `docs/gops-information-architecture-cross-screen-audit.md` §9 already states no new Context Preservation gap was found in that prior audit, covering every Detail/Edit screen except Master Maintenance's dialog-based screens (which have no separate route to preserve context for). This audit's own investigation (§8's Candidate-selection finding) is a **different** kind of state (multi-select, not filter/page/sort) that the existing `returnTo` mechanism was never designed to cover — it does not contradict or reopen §9's closure, it identifies an adjacent, previously-out-of-scope category of state. No further Back/context gap was found in this Stage beyond §8.

---

## 22. Dashboard KPI Definitions (Full Reference Table)

| KPI | Definition | Data source | Computed by |
|---|---|---|---|
| 発注候補 (candidateCount) | Count of SKUs currently eligible for ordering | `ms_stk`/candidate logic | `DashboardService`/Read Model |
| 欠品 (outOfStockCount) | `current_stock == 0` | `ms_stk` physical warehouses | `DashboardStockAggregateQuery.sql` |
| 長期欠品 (longTermOutOfStockCount) | `current_stock == 0 AND open_po == 0` (instantaneous, **not** duration-based — §4) | `ms_stk` + open PO | same |
| 発注作成中 (draftCount) | PortalOrder count with `status = DRAFT` | `portal_order` | `DashboardService.java:98` |
| 承認待ち (pendingApprovalCount) | `status = PENDING_APPROVAL` | `portal_order` | same |
| メーカー回答待ち (awaitingSupplierCount) | `status = AWAITING_SUPPLIER` | `portal_order` | same |
| 要確認 (attentionCount) | Any order (any status) with `OrderAttention.active = true` | `order_attention` | `DashboardService.java:95-102` |
| 問い合わせ中 (openFollowUpCaseCount) | Open follow-up cases (no dedicated destination filter exists yet — §11) | follow-up case table | `DashboardService` |
| 価格変更（下書き） | PriceChangeSet count with `status = DRAFT` | `price_change_set` | `DashboardService` |

---

## 23. Performance / Capacity — Conceptual Review Only

Per this audit's explicit scope, **no new performance measurement work was performed** — the user's own hands-on judgment already found current speed acceptable, and the already-known Warehouse Stock (~6s unfiltered) and `RecommendedQtySkuIdsQuery` (list ~7.35s) gaps remain exactly as documented in `docs/real-data-audit/gops-warehouse-stock-production-scale-remediation.md` (§1 baseline). This section is a **conceptual** review only.

**Screens/filters known to trigger the already-documented slow path** (restated, not newly measured): Warehouse Stock's default/unfiltered landing view (~6s); any screen that calls `RecommendedQtySkuIdsQuery.sql`'s full (unfiltered) variant — primarily the Candidate List's recommended-quantity-driven views when no Brand/Warehouse/SKU filter narrows the underlying join first.

**Conceptual scaling assessment** (current / 2x / 5x row-count, qualitative only, no benchmarking performed):
- Warehouse Stock's unfiltered case is already at a measured floor set by a ~567K-row nested-loop join with no available index (§1) — at 2x scale this would plausibly approach or exceed the ~12s range; at 5x, well past any usable interactive threshold. This is a candidate for **architecture-level** attention (a genuinely indexed/materialized approach) rather than another query-shape iteration, if/when data volume actually grows that much — not an immediate action item.
- Dashboard/Candidate Brand Breakdown (§2) is Read-Model-backed and pre-aggregated once per refresh cycle — its per-request cost is already decoupled from live Legacy row count, so it does not have the same scaling risk profile as Warehouse Stock.
- Supplier Master's in-memory join (§19) scales linearly with supplier count (~602 today) — at 5x (~3000) the current O(n×m) in-Java join becomes a much more plausible complaint even without adding search, independent of whether search ships.

**Instance-upgrade vs. architecture assessment**: for Warehouse Stock specifically, a bigger Legacy DB instance would not help — the cost is dominated by an unindexed join with no available index to add (already established, §1); this is an architecture-class problem (a materialized/pre-aggregated read path, similar in spirit to what the Dashboard Read Model already does), not an infrastructure-sizing one. For Supplier Master, a straightforward DB-level query rewrite (§19) is far cheaper than any infrastructure change and should be preferred first.

**Proposed Performance Capacity Test plan (future, not this Stage)**: if/when real data volume growth becomes a concern, a proper capacity test would (1) snapshot Production at increasing synthetic multiples, (2) re-run the exact same ≥20-sequential-request methodology already established in the Warehouse Stock remediation doc, (3) apply it specifically to Warehouse Stock unfiltered and `RecommendedQtySkuIdsQuery`'s full variant, the two known risk points. This is proposed as a plan, not executed here.

---

## 24. Do Not Reopen — Confirmed Closed Items

The following are explicitly confirmed CLOSED by prior work and must not be re-litigated by any future Stage without new evidence:

1. Dashboard Read Model architecture, refresh cadence, single-flight locking (Stage 5K Freeze).
2. RC-D FormulaParser `[AT]`/`[AJ]` (Stage 5J Addendum).
3. Null-`brand_cd` remediation (Stage 5K-R).
4. Context Preservation (`returnTo`) coverage across Detail/Edit screens (`gops-information-architecture-cross-screen-audit.md` §9).
5. Single-stage-only Approval Workflow — explicitly reaffirmed not to be extended to multi-stage even for Revision cycles (`docs/supplier-response-revision-workflow.md` §13/§15).
6. "Supplier Response確定 ≠ AGREED" invariant (`docs/supplier-response-revision-workflow.md`, reaffirmed 3×).
7. Manufacturer Stockout Status kept structurally separate from Legacy 廃番/itemStatus — an intentional non-decision (`docs/gops-manufacturer-stockout-information-management.md` §2 line 128).
8. `COMPLETED` PortalOrder status — confirmed dead, deliberately left untouched.
9. Reissue (BR-02)/Cancellation (BR-03) integration with the Official PO axis — already correctly wired (§17), no redesign needed.
10. Warehouse Stock's remaining unfiltered-case performance gap — already honestly documented as unresolved-but-understood, not silently reopened as a "new" finding by this audit (§1, §23).

---

## 25. 4-Persona Hands-On Walkthrough (Against the Production Snapshot)

Performed against the still-running Snapshot-connected environment (backend/frontend confirmed responding 200 at the start of this Stage; Snapshot DB read-only per `SafetyGuardEnvironmentPostProcessor`).

| Persona | Path exercised | Findings surfaced |
|---|---|---|
| **Warehouse/Ops staff** (day-to-day candidate triage) | Dashboard → Candidate Brand List → Candidate List → multi-select → SKU Detail → back | §2, §3, §4, §5, §6, §8 |
| **Order creator** (drafts an order) | Candidate List → create draft → Draft screen → save → Order History | §9 |
| **Approver** (reviews and approves) | Dashboard 承認待ち KPI → Order History filtered → Order Detail | §10, §11, §17 |
| **Admin** (masters, Official PO, supplier send) | Supplier Master List → Order History Detail → Official PO Preview/Send | §12–§18, §19 |

This walkthrough's findings are the same ones already itemized in §2–§19 above (the personas are a lens applied across the same screens, not a source of separate new findings) — restated here per the task's own structural requirement rather than duplicated as new content. The one item explicitly flagged for **re-verification** rather than treated as closed is §11's 要確認 KPI discrepancy — a live click-through repro during a future session (ideally with the user present to point at the exact click sequence that produced the original observation) would resolve the open question definitively; this Stage's static-source re-verification could not reproduce it and does not claim to have disproven it either.

---

## 26. Decision Matrix

| # | Item | Judgment | Rationale (brief) |
|---|---|---|---|
| 1 | Dashboard Brand Breakdown search/0-hide toggle (§2) | ACCEPT WITH MODIFICATION | Client-side filter only, no new query — safe, cheap |
| 2 | Candidate Brand List search/0-hide/per-cell gating (§3) | ACCEPT WITH MODIFICATION | Same data source as #1, same low risk |
| 3 | 長期欠品 label rename (§4) | ACCEPT WITH MODIFICATION | Copy-only change; computation unchanged pending Gulliver answer |
| 4 | 長期欠品 duration-based recomputation | GULLIVER CONFIRMATION REQUIRED | Cannot invent the threshold |
| 5 | 廃番 conflict reconciliation (Legacy vs. Supplier self-report) | GULLIVER CONFIRMATION REQUIRED | No source-only answer on which source wins |
| 6 | ItemStatusChip tooltip + unified status legend (§5) | ACCEPT | Additive UI only |
| 7 | Arrival-date staleness visual marker (§6) | ACCEPT | Render-time-only, no data/priority change |
| 8 | Auto-fallback when Legacy arrival date is stale | GULLIVER CONFIRMATION REQUIRED | Business rule, not inferable |
| 9 | SKU Detail Order History yearly accordion (§7) | ACCEPT | Pure frontend grouping of already-fetched data |
| 10 | Candidate selection persistence via URL (§8) | ACCEPT WITH MODIFICATION | Needs a length-cap sanity check first |
| 11 | Draft lifecycle Option C (relabel + delete + flag-only cleanup) (§9) | ACCEPT WITH MODIFICATION | Smallest change fixing the actual complaint |
| 12 | Draft lifecycle Option B (true create-on-save) | KEEP CURRENT (rejected) | Worse crash-recovery, larger rework, for a label-only complaint |
| 13 | Draft auto-deletion of abandoned drafts (vs. flag-only) | GULLIVER CONFIRMATION REQUIRED | Deleting business records needs a human-confirmed policy |
| 14 | Dashboard KPI reordering into workflow sequence (§10) | ACCEPT | Pure reordering, zero backend change |
| 15 | 要確認 KPI navigation — is it actually broken? | GULLIVER CONFIRMATION REQUIRED (needs live repro, not a business question, but unresolved) | Static source does not reproduce the finding; needs a hands-on re-check, not a design decision |
| 16 | 問い合わせ中 KPI's unfiltered destination | KEEP CURRENT for now | No dedicated filter exists yet to route it to; needs its own filter design first |
| 17 | PO-number-format discrepancy (§12.3) | GULLIVER CONFIRMATION REQUIRED (BLOCKING) | Gates §15/§16; cannot proceed on either without this |
| 18 | Signature/internal-procedure tracking as a new sub-state (§14) | GULLIVER CONFIRMATION REQUIRED | May not even be wanted in-system at all |
| 19 | Official PO directory detection/reconciliation design (§15) | BLOCKED | Blocked on #17 and §12.5 items 3–4 |
| 20 | Official PO generation template audit against real number format (§16) | BLOCKED | Blocked on #17 |
| 21 | Reissue/Cancellation integration with Official PO axis | KEEP CURRENT | Already correct, confirmed (§17) — do not reopen |
| 22 | Supplier Send/PO Preview screen changes (§18) | KEEP CURRENT for now | Premature until #18 resolves |
| 23 | Supplier Master keyword search (§19) | ACCEPT WITH MODIFICATION | Ship search now; DB-level join rewrite can follow separately |
| 24 | Supplier Master in-memory join → DB-level query rewrite (§19) | ACCEPT WITH MODIFICATION | Performance improvement, sequenced after or alongside #23 |
| 25 | Filter-chip visualization extension to remaining list screens (§20) | ACCEPT | Re-endorsing an already-scoped, already-approved prior proposal |
| 26 | Brand Master search/pagination status | GULLIVER CONFIRMATION REQUIRED (needs verification, not customer input) — flagged as an investigation gap this Stage did not close | Not independently re-verified this Stage; needs a follow-up read before any judgment |

---

## 27. Minimal Gulliver Questions (Deduplicated, Source-Investigation-Reduced Set)

Only the questions genuinely unanswerable from source/data — every question the investigation itself settled has been dropped from this list:

1. **PO number format**: is BR-08's `{Supplier3}{Brand3}{通番3}` (no separator) the intended format going forward (coexisting with old dash-separated historical POs), or was it stated imprecisely and the real intended format matches Legacy's actual dash-separated parsing (`SSSS-BBB-NN`)? *(Blocking — gates §15, §16.)*
2. Is 長期欠品 meant to be a duration-based concept (e.g., N consecutive days at zero stock with no open PO), or is "no stock + nothing incoming, right now" already the intended meaning and only the label is misleading?
3. When Legacy's 廃番/`itemStatus` and a supplier's self-reported Supply Status disagree, which is authoritative for the operator to trust?
4. When a Legacy expected-arrival date passes without the stock actually arriving, should the system auto-fall-back to a different display (e.g. "不明" or the Manual date), or is a human meant to notice and intervene manually?
5. Does the customer want the Excel→PDF signature/internal-procedure step (§13 step 3) tracked inside G-OPS at all, or is it intentionally kept as an offline/paper process outside the system's scope?
6. What is the real Production `MS_COMM(FILE_IMP, OFFIC_PO)` directory path, its polling schedule, and the real success/failure filename convention the receiving side actually uses to detect a processed file? *(Needed to replace `OfficialPoFileNaming`'s current self-admitted working-assumption pattern with a confirmed one.)*
7. For abandoned/untouched DRAFT Portal Orders, should there ever be automatic deletion, or should the system only ever flag them for human review?

---

## 28. Phased Implementation Plan (Proposal, Revisable)

Dependencies noted; no phase here has been started.

- **UX-1** (no dependency): Dashboard Brand Breakdown + Candidate Brand List search/0-hide (§2, §3), Dashboard KPI reordering (§10), ItemStatusChip tooltip + status legend (§5), Order History filter-chip visualization extension (§20).
- **UX-2** (no dependency): Arrival-date staleness visual marker (§6, render-only, pending Q4's answer only for any *behavioral* fallback — the visual marker itself does not need to wait).
- **UX-3** (no dependency): SKU Detail Order History yearly accordion (§7).
- **UX-4** (small design spike first): Candidate selection persistence via URL, after a quick sanity check on realistic max-selection counts (§8).
- **UX-5** (depends on Q7's answer): Draft lifecycle Option C — relabel, add delete endpoint, add flag-only or auto-delete cleanup per Gulliver's answer (§9).
- **UX-6** (needs live re-repro first, not code): 要確認 KPI investigation — confirm or disprove the finding hands-on before writing any fix; add the regression test either way (§11).
- **UX-7** (depends on Q1, blocking): Official PO directory detection/reconciliation design + generation template audit (§15, §16) — cannot start before Q1 resolves.
- **PERF-1** (example only, not measured/started this Stage): Supplier Master DB-level query rewrite + search (§19, §23).
- **PERF-2** (example only, future, gated on real volume growth): Warehouse Stock materialized/pre-aggregated read path, if/when data volume growth actually makes the current ~6s floor untenable (§23) — not recommended as near-term work given the user's own acceptance of current speed.

Sequencing note: UX-1/2/3 have zero cross-dependency and zero Gulliver-question dependency — they are the safest, most immediately actionable set. UX-7 is the single highest-value item per the task's own stated priority, but is entirely blocked on Q1 until that answer arrives.

---

## 29. Final Judgment

**C — ACCEPT WITH MODIFICATION.**

The majority of this audit's findings (§2, §3, §5, §6, §7, §10, §17, §20, §21, §24) are either safe, additive, zero-risk UX improvements or confirmations that existing design is already correct and should not be touched. A smaller set (§4's label-only fix, §9's Option C, §19's search) need light modification/sequencing before proceeding, as detailed in the Decision Matrix. The highest-priority item this audit was asked to resolve — Official PO reality — surfaced a genuine, previously-undocumented discrepancy between the customer-confirmed PO number format and Legacy's actual parsing logic (§12.3), which **blocks** any further Official PO design work (§15, §16, UX-7) until Gulliver Question 1 is answered. One User Acceptance Finding (§11, the 要確認 KPI) could not be reproduced against current source and is flagged for live re-verification rather than either dismissed or accepted as a confirmed defect. No finding in this document required inventing a business rule; every GULLIVER CONFIRMATION REQUIRED item is recorded as a genuine open question, not resolved by assumption.

**STOP.** No code change, no deploy, no Production/UAT connection beyond what was already active for this Stage's read-only investigation, no Legacy G-SYS change, no write to any Official PO directory. The Snapshot-connected manual-verification environment remains available and untouched (confirmed responding 200 at Stage start) for the user's own continued hands-on use, per standing instruction not to restart or modify it proactively.
