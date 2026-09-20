# G-OPS 9/17 Business Requirements Re-Audit + Mobile Approval / Stockout Date Design

**Status**: AUDIT + DESIGN ONLY. No code changed in producing this document. Baseline unchanged: Phase1 Freeze (`docs/gops-phase1-freeze.md`, Final Commit `71ad25d`) + Freeze Blocker round (Final Commit `71ad25d`/`3f69447`).

---

## 1. Source of Truth — critical correction

**`docs/20260917_打合せ議事メモ.pdf` is NOT a G-OPS/Gulliver meeting record.** Read in full: it is a Zoom AI-generated summary of a single-attendee ("T.Harashima") discussion about an unrelated business — order-volume growth, a proposed new product-registration/EC system, and a cost estimate. It contains zero mentions of Gulliver, G-SYS, Official PO, Supplier/Brand, or any G-OPS terminology. It appears to share its filename date with the real 9/17 work by coincidence only. **Do not use this PDF as a 9/17 source for anything.**

The actual, internally-honest source of truth is **`docs/gulliver-20260917-confirmed-business-rules.md`**. Its own header (lines 7-9) already discloses this precisely: its confirmed content (BR-01–BR-10) comes from "ユーザー（顧客側担当）から直接提示された確定仕様" (specification presented directly by the user/customer-side contact in that session's own task instructions) — the PDF was consulted only for background and explicitly not treated as the primary record even by that document's own authors.

Other secondary docs read for cross-checking: `gulliver-20260917-phase1-gap-analysis.md`, `gulliver-20260917-phase1-implementation-report.md`, `gulliver-20260917-confirmed-rules-gap-implementation.md`, `gulliver-phase1-acceptance-review.md`, `gulliver-phase1-acceptance-fix-report.md`, `docs/gops-phase1-freeze.md`, `docs/gops-phase1-final-cleanup-report.md`.

**Important negative finding**: none of the above documents — including the authoritative BR-01–BR-10 — contain any mention of "欠品" (stockout) at all. The Stockout/Long-term Stockout Expected Date want (this task's item 3B) is **not traceable to any 9/17 record in this repository**. It is a new request introduced directly in today's task instructions, not a re-confirmation of a prior Gulliver ask. See §10.

## 2. 9/17 Requirements (BR-01–BR-10) and Traceability Matrix

| ID | Requirement (from confirmed-business-rules.md) | Category | Classification | Evidence |
|---|---|---|---|---|
| BR-01 | Official PO: Excel and PDF share one data model, same content | OFFICIAL PO | **IMPLEMENTED** | `OfficialPoExcelGenerator`/`OfficialPoPdfGenerator` share one input model; E2E Scenario J generates both from the same source |
| BR-02 | Reissue requires re-approval; old Revision → SUPERSEDED, new → ACTIVE | REISSUE | **IMPLEMENTED** | `official-po-integration.spec.ts` Scenario K |
| BR-03 | Cancel: mandatory reason → ADMIN approval → CANCELLED; no automatic Legacy write | CANCEL | **IMPLEMENTED** | Scenario L |
| BR-04 | Email To/CC override (not ADMIN-only), final-confirmation dialog, non-blocking domain-mismatch warning, Audit | EMAIL | **IMPLEMENTED** | `email-send.spec.ts` (override, domain warning never blocks Send, `EMAIL_SENT` audit) |
| BR-05 | Default CC is an initial value only — never mandatory, never silently re-appended | EMAIL | **IMPLEMENTED** | `PortalMailSettings.java` Javadoc: "only ever PREFILLS... never automatically appended" |
| BR-06 | Demo Send hidden in Production; real flow is Send→Success→Audit only | EMAIL | **IMPLEMENTED** | `app.demo-features.enabled=false` in `application-production.yml` gates Demo Send UI |
| BR-07 | Portal管理番号 and 正式PO番号 are distinct, both non-editable | OFFICIAL PO | **IMPLEMENTED** | Order History Cleanup-1 (this Phase) made the distinction visible end-to-end; both fields confirmed non-editable |
| BR-08 | Format `{Supplier3}{Brand3}{Serial3}`, per-pair sequence, concurrency-safe, short codes Gulliver-owned/READ ONLY, used consistently | OFFICIAL PO | **PARTIAL** | Auto-numbering/sequencing/concurrency-safety confirmed working. **Gap**: `OrderHistoryDetailPage.tsx` (`official-po-number-form`) still has a live manual "confirm PO number" mutation (`INVALID_OFFICIAL_PO_NUMBER`/`DUPLICATE_OFFICIAL_PO_NUMBER` handling) — BR-08's own text says this manual step "should in principle be changed to auto-numbering," but it has not been fully retired alongside the new auto-numbering path |
| BR-09 | Domestic/Overseas: Overseas = Legacy `calc4`/`MS_FORMULA`; Domestic = unconfirmed, never fabricate a number | RECOMMENDED QTY | **IMPLEMENTED** | `DomesticRecommendedQtyStrategy.calculate()` unconditionally returns `null`; `RecommendedQtyCalculator` dispatches by classification; `supplier-region-classification.spec.ts` Scenario 8 confirms "設定準備中" is still shown, re-verified intact after all recent Freeze/Blocker work |
| BR-10 | Company = Supplier; no separate Company Master | MASTER DATA | **IMPLEMENTED** | No `Company` entity exists anywhere in the domain package |

**Summary**: 9 of 10 IMPLEMENTED, 1 PARTIAL (BR-08's un-retired manual PO-number-confirm UI). Zero NOT IMPLEMENTED, zero BUSINESS DECISION REQUIRED among the 10 confirmed rules themselves.

### 2.1 §5 checklist areas not covered by a distinct BR — confirmed via this session's own recent work

Supplier Response (Requested/Confirmed Qty, Difference, Partial response, Agreement, Reopen), Approval (Waiting/Detail/Stock/Sales/Lead Time/Arrival Schedule/Recommended Qty/Approve/Reject/Edit/Audit), and Master (Supplier Contact/Manufacturer Channel/Region Classification/Official PO Short Code/Mail Template/Default CC) are all **IMPLEMENTED** — re-confirmed as part of this session's Freeze and Freeze-Blocker rounds (full Backend/E2E regression: 594/594 and 212/212 respectively, both green as of Final Commit `71ad25d`). No re-litigation needed here; see those reports for the detailed evidence trail.

## 3. Mobile Approval — Current State (Browser-verified)

Flow tested live: Dashboard → 承認待ち KPI → Order List (status=PENDING_APPROVAL) → Order Detail (Approval), at 375×812 / 390×844 / 430×932 / 1440×900 (Desktop reference). Screenshots: `docs/gops-post-freeze-business-refinement-screenshots/`.

### 3.1 Order List (承認待ち) — `mobile-order-list-390.png`

Filters stack one-per-row (label above input), pushing the result table below the fold. The table itself is **still the raw Desktop `<Table>`** (`OrderHistoryListPage.tsx` — confirmed earlier this session it has no `isCardLayout` branch, unlike `OrderCandidateBrandListPage`/`OrderDraftPage`, which already do) — columns (Portal管理番号/正式PO番号/Revision/Status/etc.) require horizontal in-container scrolling to see. No page-level overflow (the existing overflow-audit guard holds), but the table itself is cramped and requires left-right scrolling — genuinely awkward on a phone.

### 3.2 Order Detail / Approval Detail — `mobile-approval-detail-390.png` vs `desktop-approval-detail-1440.png`

At Desktop, the header (`戻る` link + title + 2 chips) fits on one clean line. At 390px, the SAME content breaks into a 3-way layout collision, confirming every one of the user-reported problems:

- **Title wraps badly**: "発注詳細 - DRAFT-20260920-8843" wraps across 3 lines, splitting mid-token (`DRAFT-` / `20260920-` / `8843`).
- **Back link wraps vertically**: "発注一覧へ戻る" is squeezed into a narrow left column and wraps into 3 lines of 2-3 characters each ("発注一" / "覧へ戻" / "る") — a clearly broken layout, not a minor cosmetic issue.
- **Status chip overflows**: the orange status chip is clipped at the right edge of the viewport, partially cut off.
- **High information density, no clear priority**: title/back-link/2 chips are all competing for the same horizontal space with no responsive stacking strategy.

**What already works well** (do not regress this): below the header, the Order Summary block, the SKU line card (`OD-TENT-001`, all fields readable, one-per-row), and the bottom Sticky Action Bar (`修正` / `承認` / `差し戻し`, all reachable without scrolling) are already correctly mobile-adapted — this matches the existing `order-detail-line-cards` Card layout and sticky-button behavior already covered by `mobile-responsive.spec.ts` M1. **The problem is isolated to the page HEADER**, not the whole page.

### 3.3 Dashboard — `mobile-dashboard-390.png`

No problems found — KPI tiles and Brand breakdown table already render cleanly at all 3 Mobile widths tested (this matches the existing Mobile Responsive Overflow Audit's own 5-viewport regression coverage).

## 4. Mobile Approval — Proposed Design (design only, not implemented)

Given §3's findings, the fix is narrower than a full redesign: **the header needs a Mobile-specific layout; the body already works.**

### 4.1 Header (the only real problem area)

```
[< 発注一覧へ戻る]              <- own row, plain text link, no wrap
発注詳細                         <- own row, short static title (not "発注詳細 - {no}")
DRAFT-20260920-8843  [承認待ち]  <- own row: Portal管理番号 value + Status chip, wraps as a unit if needed
```

Concretely: split the current single `<Typography>` (`{title} - {draftNo}`) into 3 stacked rows below `sm` breakpoint, matching the same `useMediaQuery(theme.breakpoints.down('sm'))` card-layout-switch pattern already used by `OrderCandidateBrandListPage`/`OrderDraftPage` — no new pattern invented, reuse the established one. The "Portal管理番号" chip is folded into the number line itself (label removed, relying on the already-existing tooltip/Chip pattern from Order Detail) rather than as a separate floating chip, since it's the label competing for space, not the value.

### 4.2 Order List → Mobile Card layout (newly identified gap, in scope since it's step 2 of the Approval flow)

Apply the SAME `isCardLayout` pattern `OrderCandidateBrandListPage` already uses: below `sm`, render one Card per Order (Management No. / Official PO No. / Status / Brand / Updated At), replacing the raw `<Table>`. This directly reuses the Order History Cleanup-1 terminology (Portal管理番号/正式PO番号) already finalized this Phase — no new naming decisions needed.

### 4.3 Actions — confirms user's own proposal

Primary (Approve/Return) in the existing bottom Sticky Action Area — already implemented and working (§3.2). Secondary (修正) already placed as the leftmost, visually de-emphasized button — matches the user's own "Secondary Action" proposal already. Confirmation dialogs for Approve/Return already exist (`approve-dialog-confirm`, return-dialog equivalent) — reuse as-is, no new dialog needed.

### 4.4 Workflow/Permission impact

**None.** This is a pure layout fix to `OrderHistoryDetailPage.tsx`'s header JSX and a new Mobile Card branch on `OrderHistoryListPage.tsx` — no Backend change, no new API, no Role/Permission change, no state-machine change.

## 5. Stockout / Long-term Stockout — Requirement Reality Check

Per §1, this is **not** a 9/17-traceable requirement — it is introduced fresh in today's task. Treating it as a new, standalone Business request (not a re-confirmation), the design below is scoped to what's technically groundable in current Legacy data, per the instruction not to invent Business Meaning.

## 6. Legacy Arrival/Stockout Data — Findings

Legacy `TR_ARR` (`TrArr.java`) already carries the relevant date fields:

- **`ETA`** — estimated arrival at port
- **`ETA_WH`** — estimated arrival at warehouse (the practically-useful "when is this sellable" date; Legacy's own `PrEtaWhMailBatch` emails off this field)
- **`STK_IN_DATE`** — actual stock-in date (backward-looking only, not a prediction)

**These are already surfaced in G-OPS**: `ArrivalSummaryResponse.java` already exposes `eta`/`etaWarehouse` on the existing 入荷確認 (Arrival) screen (confirmed live, §6 screenshot below) — this is Phase 8-G work, not new. The term already used in the shipped UI is **「入荷予定日」** (Expected Arrival Date) — reuse this exact term, do not invent a new one.

### 6.1 The critical nuance that must drive the design

Current Long-term OOS logic (`DashboardService.java`, unchanged, re-verified this round):

```
isOutOfStock(c)         = currentStock == 0
isLongTermOutOfStock(c) = isOutOfStock(c) && (openPo == null || openPo == 0)
```

**"長期欠品" specifically means "OOS AND nothing currently on order."** Live-confirmed via Browser (`stockout-current-detail.png`): SKU `HM-MUG-002`, classified 長期欠品, shows 入荷予定数=0 and the Arrival screen returns "該当する入荷データがありません" (no matching Arrival data) when filtered to this SKU. **This means the exact cases Gulliver would most want a date for (Long-term OOS) are precisely the cases where Legacy has NO Expected Arrival Date to show — nothing has been ordered yet.** Only plain 欠品 with an open PO already in flight has a real Legacy-sourced ETA/ETA_WH available today.

## 7. Business Meaning Classification (per the requested A/B/C/D split)

| Type | Definition | Source | Applies to |
|---|---|---|---|
| A. Expected Arrival Date | Legacy `ETA`/`ETA_WH`, already exists when a PO/Arrival record exists | Legacy, automatic | Plain 欠品 WITH an open PO |
| B. Stockout Expected End Date | "When will this stop being out of stock" — not a distinct field anywhere; would be DERIVED from A when A exists, otherwise Unknown | Derived, not stored | Both, but only computable when A exists |
| C. Supplier Expected Restock Date | A Manufacturer-communicated restock estimate, independent of any PO already existing | Does not exist anywhere today — would require new Manual Input | Long-term 欠品 specifically (the case with no A) |
| D. Unknown | No date available at all | N/A | Long-term 欠品 with no Manual Input entered either |

**Both A and C are needed** — they answer different questions for different SKU populations, exactly as the instructions warned not to collapse into one. A is free (already flows from Legacy, zero new input). C requires a new Manual Input mechanism, since no such field exists in Legacy or Portal today.

## 8. Existing vs. Manual Input

| Field | Source | Evidence |
|---|---|---|
| Expected Arrival Date (Type A) | **Legacy, automatic** — already flows via `ArrivalSummaryResponse.eta`/`etaWarehouse` | Confirmed live and in code (§6) |
| Supplier Expected Restock Date (Type C) | **Manual Input required** — no Legacy or Portal field exists today | Confirmed absent from Legacy schema (`TR_ARR`/`TR_PO`/`MS_STK` — no forward-looking Supplier-communicated date column) and from Portal's own schema |

## 9. Stockout Date — Proposed Data Model (design only)

For Type C (the only one requiring a new field), a new **Portal-only** Master/record is needed — scoped minimally, same "Portal-owned since Legacy has no equivalent" pattern already established for `manufacturer_channel`/`supplier_region_classification`/`official_po_short_code`:

- Key: SKU (+ optionally Supplier/Brand, TBD at implementation time — likely SKU alone is sufficient since restock timing is a per-item fact)
- `expected_restock_date` (nullable DATE)
- `is_unknown` (boolean — an explicit "未定" state, distinct from simply leaving the date null on creation, so a user can affirmatively record "asked, no answer yet" vs. "never asked")
- `memo` (nullable text)
- `updated_by` / `updated_at` (standard audit pair, matching every other Portal Master's convention)

This does **not** touch Brand/Supplier/Official-PO-Short-Code Data Model in any way (§18 rule respected) — it is an independent, SKU-keyed record.

## 10. Stockout Date — Proposed UI (design only)

**Display** (where — chosen by business relevance, not blanket-applied everywhere per instruction):

- **Candidate List** (欠品/長期欠品 filter view): add a compact column/line — "9/28入荷予定" (Type A, from Legacy) when a PO is in flight, or "11/15再入荷予定" / "未定" (Type C, Manual Input) when Long-term OOS has no PO. This is exactly the visibility the instructions describe wanting (§16).
- **SKU Detail**: same information, plus (for ADMIN/OPERATOR) the entry point to set/edit Type C.
- **Order Draft/Approval Detail**: NOT proposed for this iteration — these screens are about the CURRENT order being placed, not a general stockout-timing lookup; adding it there would be scope creep beyond what's needed.
- **Stock/Sales**: not proposed initially — same reasoning as above; revisit only if a concrete need surfaces.

**Input** (Manual Input for Type C only): SKU Detail is the natural entry point (it's already the per-SKU hub, already links to Arrival). Supplier Response is NOT proposed as an entry point — a Supplier's restock promise is logically closer to "a fact ADMIN records about the SKU" than a per-Response artifact, and Supplier Response's own data model is Order-scoped, not SKU-scoped, so it doesn't fit cleanly.

**Audit**: yes — `updated_by`/`updated_at` (§9) is sufficient; a full `audit_event` entry is not proposed as required for this (a low-stakes, frequently-updated operational note, unlike an Order/PO state transition) — flag as a call the user may want to overrule.

## 11. List Visibility — Concrete Mockup

```
SKU          Status     Stockout Until
HM-MUG-002   欠品        9/28入荷予定
OD-BAG-002   長期欠品    11/15再入荷予定
KT-BOWL-002  長期欠品    未定
```

Mobile: same information folded into the existing Candidate Card layout (already Mobile-adapted, `mobile-responsive.spec.ts` M5) as one additional line per card — no new layout pattern needed, reuse what already exists.

## 12. Brand/Supplier 3-character Short Code — Current Implementation (description only, no change)

**Correction to the assumed current structure**: it is **not** nested under "Master Maintenance → Supplier → Supplier Settings → PO Code." It is its **own top-level admin route**: `/admin/official-po-short-codes` (`OfficialPoShortCodePage`), with its own nav entry (`navAdminOfficialPoShortCodes`) in both Desktop and Mobile navigation.

- **DB**: single table `official_po_short_code` (Flyway V29), with a `code_type` discriminator (`'SUPPLIER'`/`'BRAND'`, CHECK constraint), `business_code`, `short_code CHAR(3)`. Uniqueness: `(code_type, business_code) WHERE is_active=true` — one active code per Supplier code, independently one per Brand code. **This is one table with two row-types, not two separate Master tables.**
- **API**: `GET/POST /api/admin/official-po-short-codes`, `PUT .../{id}`.
- **Validation**: 3-char length + uniqueness scoped as above. **No constraint prevents two different Suppliers/Brands from sharing the same literal 3-character value** (e.g. Supplier "ABC" and Brand "ABC" could both exist, or two different Suppliers could theoretically... no — uniqueness is per `business_code`, so two different Suppliers can't collide with each other, but a Supplier code and a completely unrelated Brand's code CAN coincidentally share the same 3 letters, since `code_type` is part of the uniqueness key, not excluded by it). Worth flagging for later review, not fixed now (§18 rule).
- **Permission**: ADMIN-only (same as every other Master Maintenance write endpoint).
- **PO Number generation**: `OfficialPoNumberGenerator` resolves the Supplier short code and Brand short code **independently** (separate lookups by `supplierCode`/`brandCode` respectively) and concatenates them + a per-(Supplier,Brand)-pair sequence — confirms the two codes are genuinely independent lookups, not a joint key.
- Legacy has no 3-character abbreviation field anywhere (`ms_comm` only carries `code_id`/`code_name`) — this remains, as already documented, a Portal-only Master an ADMIN populates by hand with Gulliver-decided values.

**No Data Model opinion is offered here** — per §18, this is purely "what exists today," pending UAT real-data Relationship confirmation.

## 13. UAT-Data-Dependent Items

- Supplier:Brand cardinality (1:1 / 1:N / N:1 / N:N) — cannot be determined without real G-SYS data (see the separate Real Data Relationship audit thread; still blocked on Ernest's UAT DB)
- Whether `official_po_short_code`'s current structure (independent Supplier/Brand codes, not a joint key) is actually correct for the real Relationship — explicitly NOT decided this round
- Manufacturer vs. Supplier — same-entity-or-not question, still open pending real data

## 14. Production Readiness Items (unchanged from the prior audit round)

Hosting, Network, Secrets, Production Import Folder/SMTP/EDI connection — see `docs/production-readiness/` (previous round), untouched by this audit.

## 15. Future Phase Items

- Supplier Expected Restock Date (Type C) full workflow beyond the minimal design in §9-10, if Gulliver wants richer tracking (reminders, escalation, etc.)
- Past Revision individual download (pre-existing Known Limitation, unrelated to this audit)
- EDI actual integration, User Management/SSO — unchanged Known Limitations

## 16. Existing Remaining Gaps — Reclassified

| Gap | Classification |
|---|---|
| Domestic Recommended Qty exact formula | D (UAT/Business — needs Gulliver's real formula) |
| Past Revision individual download | F (Future Phase) |
| Actual Supplier/Brand Short Code source | D (UAT real-data dependent, §13) |
| Official PO Excel/PDF exact visual layout | E (Business Decision — Gulliver confirmation needed) |
| Production Import Folder / SMTP / EDI | C (Production Readiness) |
| User Management / SSO / MFA | C (Production Readiness) + E (Business Decision on whether even required) |
| Approval Workflow details (multi-stage, self-approval) | E (Business Decision, explicitly optional per prior audit) |
| BR-08's un-retired manual PO-number-confirm UI (§2) | **B (Phase1 improvement required)** — newly identified this round |

## 17. Recommended Next Implementation Scope

1. **Mobile Approval header fix** (§4.1) + **Order List Mobile Card layout** (§4.2) — READY TO IMPLEMENT, no Workflow/Permission change, no Business confirmation needed.
2. **Stockout Date** (§9-11) — NEEDS BUSINESS CONFIRMATION on: whether SKU-only keying is sufficient (vs. per-Supplier/PO), whether Audit-trail-grade tracking is required for Type C edits (§10), and confirmation that "not on Order Draft/Approval Detail, not on Stock/Sales" (this round's scope trim) is acceptable.
3. BR-08 manual-entry retirement (§2/§16) — separate, smaller item; flagged here but not designed this round (out of today's requested scope).

---

**Screenshots**: `docs/gops-post-freeze-business-refinement-screenshots/` — `mobile-dashboard-{375,390,430}.png`, `mobile-order-list-390.png`, `mobile-approval-detail-{375,390,430}.png`, `desktop-{dashboard,order-list,approval-detail}-1440.png`, `stockout-current-list.png`, `stockout-current-detail.png`.
